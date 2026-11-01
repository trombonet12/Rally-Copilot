package app.trombito.rallycopilot.data.alertas

import app.trombito.rallycopilot.data.vias.FuenteVias
import app.trombito.rallycopilot.data.vias.ProveedorViaActualOsm
import app.trombito.rallycopilot.domain.alertas.AlertasEnCurso
import app.trombito.rallycopilot.domain.alertas.ExcesoEnCurso
import app.trombito.rallycopilot.domain.alertas.IncidenciasVigentes
import app.trombito.rallycopilot.domain.alertas.RadarProximo
import app.trombito.rallycopilot.domain.alertas.RepositorioIncidencias
import app.trombito.rallycopilot.domain.alertas.TramoActivo
import app.trombito.rallycopilot.domain.vias.ProveedorViaActual
import app.trombito.rallycopilot.motor.alertas.AlertasRadar
import app.trombito.rallycopilot.motor.alertas.EstadoTramo
import app.trombito.rallycopilot.motor.alertas.ExcesoVelocidad
import app.trombito.rallycopilot.motor.alertas.FiltroIncidencias
import app.trombito.rallycopilot.motor.alertas.Radar
import app.trombito.rallycopilot.motor.alertas.VelocidadMediaTramo
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.voz.Frase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Crea la [SesionAlertas] de cada conducción; las alertas se publican en [AlertasEnVivoImpl]. */
@Singleton
class FabricaAlertasSesion
    @Inject
    constructor(
        private val radares: FuenteRadares,
        private val incidencias: RepositorioIncidencias,
        private val vias: FuenteVias,
        private val salida: AlertasEnVivoImpl,
    ) {
        /** Con su propio seguimiento de la vía (el de la UI es otro): no comparten el estado del seguidor. */
        fun crear(): SesionAlertas =
            SesionAlertas(
                radares,
                incidencias,
                ProveedorViaActualOsm(vias),
                salida::publicar,
                System::currentTimeMillis,
            )
    }

/**
 * Las alertas de una conducción: radares, velocidad media de tramo, exceso de velocidad e incidencias. En cada
 * fijación ([alFijar]) devuelve las frases candidatas (todas de tipo ALERTA) para que las sume el copiloto de voz y
 * publica un [AlertasEnCurso] para la UI. No es concurrente: se usa desde la corrutina de la sesión, que es también la
 * de [arrancar]. Nunca registra coordenadas.
 *
 * - **Guiado**: radares, tramos y avisos de incidencias con la ruta (las posiciones son el progreso sobre ella).
 * - **Libre**: radares con la geometría de la vía actual (orientada en el sentido de la marcha) y, además, los que
 *   queden delante en línea recta; la posición de cada frase es `notasM` más la distancia al radar, así que comparte
 *   eje con las notas (sin notas el eje arranca en 0 en cada fijación). No hay velocidad media (solo en guiado) ni
 *   avisos de incidencias por voz (solo de la ruta), pero sí incidencias relevantes (radio de 3 km) para el mapa.
 * - Los radares se leen en segundo plano al [arrancar]: hasta entonces (o sin archivo) no hay avisos de radar.
 */
class SesionAlertas internal constructor(
    private val radares: FuenteRadares,
    private val incidencias: RepositorioIncidencias,
    private val vias: ProveedorViaActual,
    private val publicar: (AlertasEnCurso?) -> Unit,
    private val ahoraMs: () -> Long,
) {
    private var ruta: List<PuntoGeo>? = null
    private var datosRadares: List<Radar>? = null
    private var radaresPorId: Map<String, Radar> = emptyMap()
    private var vigentes = IncidenciasVigentes()

    private var radaresSucios = true
    private var incidenciasSucias = true
    private var incidenciasConstruidasMs = 0L
    private var radarRuta: AlertasRadar? = null
    private var libres: RadaresLibres? = null
    private var media: VelocidadMediaTramo? = null
    private var filtro: FiltroIncidencias? = null
    private var tramoFinal: Pair<TramoActivo, Long>? = null
    private val exceso = ExcesoVelocidad()

    /**
     * Pone en marcha los datos de fondo en [alcance]: pide refrescar las incidencias (a lo sumo cada 5 min), sigue sus
     * cambios y lee los radares. [rutaInicial] es la polilínea de la ruta guiada, o `null` en modo libre. Devuelve el
     * trabajo, que quien llama cancela al terminar.
     */
    fun arrancar(
        alcance: CoroutineScope,
        rutaInicial: List<PuntoGeo>?,
    ): Job {
        nuevaRuta(rutaInicial)
        return alcance.launch {
            launch { incidencias.refrescar() }
            launch {
                val leidos = radares.radares()
                datosRadares = leidos
                radaresPorId = leidos.associateBy { it.id }
                radaresSucios = true
            }
            incidencias.incidencias.collect {
                vigentes = it
                incidenciasSucias = true
            }
        }
    }

    /** Ruta guiada nueva (arranque o recálculo): los avisos y la media de tramo se rehacen sobre ella. */
    fun nuevaRuta(puntos: List<PuntoGeo>?) {
        ruta = puntos?.takeIf { it.size >= 2 }
        radaresSucios = true
        incidenciasSucias = true
        tramoFinal = null
    }

    /**
     * Una fijación: [velocidadMs] es la velocidad estimada, [progresoM] el progreso sobre la ruta guiada (null en
     * libre) y [notasM] la posición de las notas (eje de las frases en libre). Devuelve las frases candidatas.
     */
    suspend fun alFijar(
        fijacion: Fijacion,
        velocidadMs: Double?,
        progresoM: Double?,
        notasM: Double?,
    ): List<Frase> {
        reconstruirSiToca()
        val v = velocidadMs ?: 0.0
        val via = vias.viaEn(fijacion)
        val guiado = ruta != null
        // En guiado sin progreso todavía no hay nada que decir de la ruta.
        val sobreRuta = if (guiado) progresoM else null
        val base = progresoM ?: notasM ?: 0.0
        val frasesRadar =
            when {
                !guiado -> libres?.frases(fijacion, via, v, base).orEmpty()
                sobreRuta != null -> radarRuta?.candidatas(sobreRuta, v).orEmpty()
                else -> emptyList()
            }
        val estadoTramo = sobreRuta?.let { media?.actualizar(it, fijacion.tiempoMs) }
        val kmh = velocidadMs?.times(MS_A_KMH) ?: Double.NaN
        val estadoExceso = exceso.actualizar(kmh, via?.limiteKmh, fijacion.tiempoMs)
        val frasesIncidencias = sobreRuta?.let { filtro?.candidatas(it, v) }.orEmpty()
        publicar(
            AlertasEnCurso(
                radarProximo = proximo(frasesRadar, base),
                tramo = tramoActivo(estadoTramo, fijacion.tiempoMs),
                exceso = ExcesoEnCurso(via?.limiteKmh, estadoExceso.rojo, estadoExceso.exceso),
                incidencias = filtro?.relevantes(fijacion.punto, sobreRuta).orEmpty(),
                antiguedadIncidenciasMs = antiguedadMs(),
                errorIncidencias = vigentes.errorUltimo,
            ),
        )
        return frasesRadar + listOfNotNull(estadoTramo?.frase) + frasesIncidencias + listOfNotNull(estadoExceso.frase)
    }

    /** Fin de la conducción: publica «sin alertas». Se puede llamar más de una vez. */
    fun cerrar() {
        publicar(null)
    }

    private fun antiguedadMs(): Long? = vigentes.obtenidasEn?.let { (ahoraMs() - it.toEpochMilli()).coerceAtLeast(0L) }

    private fun reconstruirSiToca() {
        val cargados = datosRadares
        if (radaresSucios && cargados != null) {
            radaresSucios = false
            libres = RadaresLibres(cargados)
            radarRuta = ruta?.let { AlertasRadar(cargados, it) }
            media = radarRuta?.let { VelocidadMediaTramo(it.tramos) }
        }
        val ahora = ahoraMs()
        val caducado = ahora - incidenciasConstruidasMs !in 0L until REFILTRO_MS
        if (incidenciasSucias || caducado) {
            incidenciasSucias = false
            incidenciasConstruidasMs = ahora
            filtro = FiltroIncidencias(vigentes.incidencias, ruta, Instant.ofEpochMilli(ahora))
        }
    }

    // ---- Estado para la UI

    private fun proximo(
        frasesRadar: List<Frase>,
        base: Double,
    ): RadarProximo? =
        frasesRadar
            .mapNotNull { f ->
                val radar = radaresPorId[f.id.removePrefix(PREFIJO_RADAR)]
                val punto = f.puntoM
                if (radar == null || punto == null) null else radar to (punto - base)
            }.minByOrNull { it.second }
            ?.let { (radar, distancia) -> RadarProximo(radar.tipo, distancia.coerceAtLeast(0.0), radar.limiteKmh) }

    private fun tramoActivo(
        estado: EstadoTramo?,
        tiempoMs: Long,
    ): TramoActivo? {
        if (estado == null) return tramoFinal?.takeIf { tiempoMs < it.second }?.first
        val actual = TramoActivo(estado.mediaKmh, estado.tramo.limiteKmh, estado.superaLimite, estado.terminado)
        tramoFinal = if (estado.terminado) actual to tiempoMs + RETENCION_TRAMO_FINAL_MS else null
        return actual
    }

    private companion object {
        const val MS_A_KMH = 3.6
        const val PREFIJO_RADAR = "radar-"

        /** Las incidencias se refiltran al menos cada minuto: así las que caducan salen aunque el feed no cambie. */
        const val REFILTRO_MS = 60_000L

        /** Cuánto se sigue enseñando la media final de un tramo ya terminado. */
        const val RETENCION_TRAMO_FINAL_MS = 10_000L
    }
}
