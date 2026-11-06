package app.trombito.rallycopilot.data.ruta

import app.trombito.rallycopilot.data.datosmapa.ConfigDatosMapa
import app.trombito.rallycopilot.data.datosmapa.EsperaDatosSembrados
import app.trombito.rallycopilot.data.vias.FuenteVias
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.CorrectorRotondas
import app.trombito.rallycopilot.motor.ruta.LectorRutaValhalla
import app.trombito.rallycopilot.motor.ruta.Ruta
import app.trombito.rallycopilot.motor.ruta.RutaInvalida
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import com.valhalla.valhalla.ValhallaException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enrutador offline con Valhalla sobre `filesDir/mapa/valhalla-mallorca.tar`. El motor se abre en la primera petición
 * y se reabre si el tar cambia (nueva versión de los datos); las peticiones se atienden de una en una en IO. Antes de
 * mirar el tar espera a que termine la siembra de los datos del APK.
 */
@Singleton
class EnrutadorValhalla internal constructor(
    private val tar: File,
    private val fabrica: FabricaMotorRutas,
    private val dispatcher: CoroutineDispatcher,
    private val reloj: () -> Long,
    private val siembra: EsperaDatosSembrados = EsperaDatosSembrados.Ninguna,
    private val vias: FuenteVias? = null,
) : Enrutador {
    @Inject
    constructor(
        config: ConfigDatosMapa,
        fabrica: FabricaMotorRutas,
        siembra: EsperaDatosSembrados,
        vias: FuenteVias,
    ) : this(File(config.directorioMapa, NOMBRE_TAR), fabrica, Dispatchers.IO, System::nanoTime, siembra, vias)

    private val cerrojo = Mutex()
    private var motor: MotorRutas? = null
    private var modificadoDelMotor = 0L

    /**
     * [PerfilRuta.SINUOSA] calcula primero con `exclude_tunnels` (sube por el Coll de Sóller, no por el túnel). Si esa
     * ruta falla (p. ej. 442 «no path» por los túneles de la Ma-10) o mide más de [FACTOR_MAXIMO_RODEO] veces la
     * sinuosa sin exclusión, se devuelve la de sin exclusión. Coste: con la exclusión correcta se calculan dos rutas
     * (la normal solo para comparar longitudes) y `calculoMs` suma ambas.
     */
    override suspend fun ruta(
        origen: PuntoGeo,
        destino: PuntoGeo,
        perfil: PerfilRuta,
    ): ResultadoRuta = rutaConParadas(origen, emptyList(), destino, perfil)

    /** Igual que [ruta] con las [paradas] intermedias como locations de tipo break (una leg por tramo). */
    override suspend fun rutaConParadas(
        origen: PuntoGeo,
        paradas: List<PuntoGeo>,
        destino: PuntoGeo,
        perfil: PerfilRuta,
    ): ResultadoRuta {
        val excluida =
            if (perfil == PerfilRuta.SINUOSA) {
                calcular(origen, destino, perfil, excluirTuneles = true, paradas = paradas)
            } else {
                null
            }
        if (excluida !is ResultadoRuta.Ok) {
            return calcular(
                origen,
                destino,
                perfil,
                excluirTuneles = false,
                paradas = paradas,
            )
        }
        val normal = calcular(origen, destino, perfil, excluirTuneles = false, paradas = paradas)
        val total = excluida.calculoMs + ((normal as? ResultadoRuta.Ok)?.calculoMs ?: 0L)
        return when {
            normal !is ResultadoRuta.Ok -> excluida
            excluida.ruta.longitudM > FACTOR_MAXIMO_RODEO * normal.ruta.longitudM -> normal.copy(calculoMs = total)
            else -> excluida.copy(calculoMs = total)
        }
    }

    /** Un único cálculo; [excluirTuneles] es la hard exclusion de Valhalla y puede dejar la ruta sin solución. */
    internal suspend fun calcular(
        origen: PuntoGeo,
        destino: PuntoGeo,
        perfil: PerfilRuta,
        excluirTuneles: Boolean,
        paradas: List<PuntoGeo> = emptyList(),
    ): ResultadoRuta =
        withContext(dispatcher) {
            siembra.esperar()
            if (!tar.isFile) return@withContext ResultadoRuta.SinDatos
            val peticion = PeticionRuta.json(origen, destino, perfil, excluirTuneles, paradas)
            cerrojo.withLock {
                val inicio = reloj()
                try {
                    val json = motorActual().rutaJson(peticion)
                    val ruta = conOrdinalesDeRotonda(LectorRutaValhalla.leer(json))
                    ResultadoRuta.Ok(ruta, (reloj() - inicio) / NANOS_POR_MS)
                } catch (e: ValhallaException) {
                    ResultadoRuta.Error(e.message ?: "Valhalla no pudo calcular la ruta")
                } catch (e: RutaInvalida) {
                    ResultadoRuta.Error(e.message ?: "Respuesta de ruta no válida")
                } catch (e: IllegalStateException) {
                    ResultadoRuta.Error("No se pudo abrir el motor de rutas: ${e.message}")
                } catch (e: UnsatisfiedLinkError) {
                    ResultadoRuta.Error("Motor de rutas no disponible en este dispositivo: ${e.message}")
                }
            }
        }

    /**
     * Valhalla 3.9.1 da siempre 2 en `roundabout_exit_count`: el número de salida de cada rotonda se cuenta sobre el
     * grafo local de vías (ver [CorrectorRotondas]). Sin vías instaladas o si la rotonda no se resuelve queda sin
     * ordinal y se canta sin él.
     */
    private suspend fun conOrdinalesDeRotonda(ruta: Ruta): Ruta {
        val hayRotondas = ruta.maniobras.any { it.tipo == TipoManiobra.ROTONDA_ENTRADA }
        val grafo = if (hayRotondas) vias?.datos()?.grafo else null
        return if (grafo == null) ruta else CorrectorRotondas.aplicar(ruta, grafo)
    }

    /** Abre el motor o lo reabre si el tar ha cambiado desde que se abrió. Llamar con el cerrojo tomado. */
    private fun motorActual(): MotorRutas {
        val modificado = tar.lastModified()
        motor?.let { abierto ->
            if (modificado == modificadoDelMotor) return abierto
            motor = null
            abierto.close()
        }
        return fabrica.crear(tar).also {
            motor = it
            modificadoDelMotor = modificado
        }
    }

    companion object {
        const val NOMBRE_TAR = "valhalla-mallorca.tar"
        private const val NANOS_POR_MS = 1_000_000L
        internal const val FACTOR_MAXIMO_RODEO = 1.6
    }
}
