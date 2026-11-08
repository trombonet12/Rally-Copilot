package app.trombito.rallycopilot.data.ruta

import app.trombito.rallycopilot.data.datosmapa.ConfigDatosMapa
import app.trombito.rallycopilot.data.datosmapa.EsperaDatosSembrados
import app.trombito.rallycopilot.data.vias.FuenteVias
import app.trombito.rallycopilot.di.DispatcherIo
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.CorrectorRotondas
import app.trombito.rallycopilot.motor.ruta.LectorRutaValhalla
import app.trombito.rallycopilot.motor.ruta.PuntuacionCurvas
import app.trombito.rallycopilot.motor.ruta.Ruta
import app.trombito.rallycopilot.motor.ruta.RutaInvalida
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import com.valhalla.valhalla.ValhallaException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong
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
        @DispatcherIo dispatcher: CoroutineDispatcher,
        siembra: EsperaDatosSembrados,
        vias: FuenteVias,
    ) : this(File(config.directorioMapa, NOMBRE_TAR), fabrica, dispatcher, System::nanoTime, siembra, vias)

    private val cerrojo = Mutex()
    private var motor: MotorRutas? = null
    private var modificadoDelMotor = 0L
    private val cerrojoCache = Any()
    private var ultima: Pair<Solicitud, Candidatas>? = null

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
    ): ResultadoRuta = resolver(origen, paradas, destino, perfil, medirSiempre = false)

    override suspend fun rutaMedida(
        origen: PuntoGeo,
        paradas: List<PuntoGeo>,
        destino: PuntoGeo,
        perfil: PerfilRuta,
    ): ResultadoRuta = resolver(origen, paradas, destino, perfil, medirSiempre = true)

    /**
     * Valhalla no tiene coste de curvatura, así que [PerfilRuta.EQUILIBRADA] y [PerfilRuta.SINUOSA] salen de calcular
     * varias [VarianteRuta] y elegir con [SelectorPerfiles] según los metros de curvas de cada una. Las candidatas de
     * una misma petición se calculan una vez y se comparten entre los tres perfiles (la última petición queda en
     * memoria): si dos perfiles eligen la misma ruta devuelven la misma, sin recalcular ni remedir. La rápida es un
     * único cálculo, sin esperar a las demás. `calculoMs` es el de todos los cálculos hechos hasta ese momento.
     * Sin la rápida (sin datos o sin camino) no hay referencia y se devuelve su resultado.
     */
    private suspend fun resolver(
        origen: PuntoGeo,
        paradas: List<PuntoGeo>,
        destino: PuntoGeo,
        perfil: PerfilRuta,
        medirSiempre: Boolean,
    ): ResultadoRuta {
        val candidatas = candidatasDe(Solicitud(origen, paradas, destino, tar.lastModified()))
        val rapida = candidatas.rapida()
        return when {
            rapida !is ResultadoRuta.Ok -> rapida
            perfil != PerfilRuta.MAS_RAPIDA -> elegirVariante(perfil, candidatas, rapida)
            medirSiempre -> rapida.copy(curvasM = candidatas.curvasDe(rapida.ruta))
            else -> rapida
        }
    }

    private suspend fun elegirVariante(
        perfil: PerfilRuta,
        candidatas: Candidatas,
        rapida: ResultadoRuta.Ok,
    ): ResultadoRuta {
        val elegida =
            SelectorPerfiles.elegir(
                perfil,
                Candidata(rapida.ruta, candidatas.curvasDe(rapida.ruta)),
                candidatas.otras().map { Candidata(it, candidatas.curvasDe(it)) },
            )
        return ResultadoRuta.Ok(elegida.ruta, candidatas.calculoMs(), elegida.curvasM)
    }

    private fun candidatasDe(solicitud: Solicitud): Candidatas =
        synchronized(cerrojoCache) {
            ultima?.takeIf { it.first == solicitud }?.second
                ?: Candidatas(solicitud).also { ultima = solicitud to it }
        }

    private data class Solicitud(
        val origen: PuntoGeo,
        val paradas: List<PuntoGeo>,
        val destino: PuntoGeo,
        val modificadoDelTar: Long,
    )

    /**
     * Las rutas candidatas de una [Solicitud], calculadas bajo demanda y guardadas solo si salieron bien (un error se
     * reintenta en la siguiente llamada). Cada cálculo y cada medida van bajo su propio cerrojo para que pedir la
     * rápida no espere a las demás variantes.
     */
    private inner class Candidatas(
        private val s: Solicitud,
    ) {
        private val cerrojoRapida = Mutex()
        private val cerrojoOtras = Mutex()
        private val cerrojoMedidas = Mutex()
        private var rapidaOk: ResultadoRuta.Ok? = null
        private var otrasRutas: List<Ruta>? = null
        private val medidas = HashMap<Ruta, Double>()

        private val totalMs = AtomicLong()

        fun calculoMs(): Long = totalMs.get()

        suspend fun rapida(): ResultadoRuta =
            cerrojoRapida.withLock {
                rapidaOk ?: calcular(s.origen, s.destino, VarianteRuta.RAPIDA, excluirTuneles = false, s.paradas).also {
                    if (it is ResultadoRuta.Ok) {
                        totalMs.addAndGet(it.calculoMs)
                        rapidaOk = it
                    }
                }
            }

        /** Las variantes que han dado ruta, sin la rápida y sin repetir rutas iguales entre sí ni a la rápida. */
        suspend fun otras(): List<Ruta> =
            cerrojoOtras.withLock {
                otrasRutas ?: calcularOtras().also { otrasRutas = it }
            }

        private suspend fun calcularOtras(): List<Ruta> {
            val rutaRapida = rapidaOk?.ruta
            val resultados =
                listOf(
                    calcular(s.origen, s.destino, VarianteRuta.POCAS_AUTOVIAS, excluirTuneles = false, s.paradas),
                    calcularConTuneles(s.origen, s.destino, VarianteRuta.SINUOSA, s.paradas),
                    calcular(s.origen, s.destino, VarianteRuta.MUY_SINUOSA, excluirTuneles = false, s.paradas),
                ).filterIsInstance<ResultadoRuta.Ok>()
            totalMs.addAndGet(resultados.sumOf { it.calculoMs })
            return resultados.map { it.ruta }.filter { it != rutaRapida }.distinct()
        }

        /** Metros de curvas de [ruta]; una ruta ya medida (o igual a otra) no se mide otra vez. */
        suspend fun curvasDe(ruta: Ruta): Double =
            cerrojoMedidas.withLock {
                medidas[ruta] ?: withContext(dispatcher) { PuntuacionCurvas.medir(ruta.puntos).curvasM }.also {
                    medidas[ruta] = it
                }
            }
    }

    /**
     * [calcular] con `exclude_tunnels` primero (sube por el Coll de Sóller, no por el túnel). Si esa ruta falla (p. ej.
     * 442 «no path» por los túneles de la Ma-10) o mide más de [FACTOR_MAXIMO_RODEO] veces la de sin exclusión, se
     * devuelve la de sin exclusión. Coste: con la exclusión correcta se calculan dos rutas (la normal solo para
     * comparar longitudes) y `calculoMs` suma ambas.
     */
    internal suspend fun calcularConTuneles(
        origen: PuntoGeo,
        destino: PuntoGeo,
        variante: VarianteRuta,
        paradas: List<PuntoGeo> = emptyList(),
    ): ResultadoRuta {
        val excluida = calcular(origen, destino, variante, excluirTuneles = true, paradas = paradas)
        if (excluida !is ResultadoRuta.Ok) {
            return calcular(origen, destino, variante, excluirTuneles = false, paradas = paradas)
        }
        val normal = calcular(origen, destino, variante, excluirTuneles = false, paradas = paradas)
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
        variante: VarianteRuta,
        excluirTuneles: Boolean,
        paradas: List<PuntoGeo> = emptyList(),
    ): ResultadoRuta =
        withContext(dispatcher) {
            siembra.esperar()
            if (!tar.isFile) return@withContext ResultadoRuta.SinDatos
            val peticion = PeticionRuta.json(origen, destino, variante, excluirTuneles, paradas)
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
