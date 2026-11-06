package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import kotlinx.coroutines.flow.first
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Decide si se ha perdido el GPS: han pasado más de [umbralMs] desde la última fijación buena (error de [precisionMaxM]
 * o menos). Sin ninguna fijación buena todavía no hay pérdida que avisar (el primer arranque tarda en fijar). Usa el
 * reloj monótono de las fijaciones (`Fijacion.tiempoMs`). Sin hilos: lo usa el colector de fijaciones del ViewModel.
 */
internal class DetectorSenalGps(
    private val umbralMs: Long = UMBRAL_MS,
    private val precisionMaxM: Double = PRECISION_MAXIMA_M,
) {
    private var ultimaBuenaMs: Long? = null

    /** Anota [fijacion]: solo cuenta como señal si su precisión es buena (o no se conoce). */
    fun fijacion(fijacion: Fijacion) {
        if ((fijacion.precisionM ?: 0.0) <= precisionMaxM) ultimaBuenaMs = fijacion.tiempoMs
    }

    /** Si, a las [ahoraMs], se ha perdido la señal. */
    fun sinSenal(ahoraMs: Long): Boolean = ultimaBuenaMs?.let { ahoraMs - it > umbralMs } ?: false

    companion object {
        const val UMBRAL_MS = 5_000L
        const val PRECISION_MAXIMA_M = 50.0
    }
}

/**
 * El copiloto avisa de que se perdió el GPS (o volvió), solo conduciendo (libre o guiando) y si no está en silencio.
 * Se llama una vez por cambio de estado de la señal, no por fijación.
 */
internal class AvisadorSenalGps(
    private val navegacion: ControlNavegacion,
    private val ajustes: RepositorioAjustesConduccion,
    private val locutor: Locutor,
) {
    suspend fun avisar(sinSenal: Boolean) {
        val control = navegacion.estado.value
        val conduciendo = control is EstadoNavegacion.Libre || control is EstadoNavegacion.Guiando
        if (!conduciendo || ajustes.ajustes.first().modoCopiloto == ModoCopiloto.SILENCIO) return
        locutor.decir(if (sinSenal) FRASE_SIN_SENAL else FRASE_SENAL_RECUPERADA)
    }

    private companion object {
        const val FRASE_SIN_SENAL = "Sin señal GPS"
        const val FRASE_SENAL_RECUPERADA = "GPS recuperado"
    }
}

/** Margen (m) entre el punto mostrado y la geometría de la vía para decir que van por la misma carretera. */
private const val MARGEN_VIA_M = 12.0

/**
 * Si el punto [punto] (la posición ya ajustada al camino) está sobre la vía [via] (a [margenM] o menos de su
 * trazado). Sin vía o sin geometría no hay nada que contradiga: devuelve `true`.
 */
internal fun viaCoincide(
    punto: PuntoGeo,
    via: ViaActual?,
    margenM: Double = MARGEN_VIA_M,
): Boolean {
    val trazado = via?.trazado
    if (trazado == null || trazado.size < 2) return true
    return distanciaAPolilineaM(punto, trazado) <= margenM
}

/** Distancia mínima (m) de [punto] a la polilínea [linea], en proyección plana local. */
internal fun distanciaAPolilineaM(
    punto: PuntoGeo,
    linea: List<PuntoGeo>,
): Double {
    val escalaX = PuntoGeo.RADIO_TIERRA_M * cos(Math.toRadians(punto.lat))
    val escalaY = PuntoGeo.RADIO_TIERRA_M

    fun x(p: PuntoGeo) = Math.toRadians(p.lon - punto.lon) * escalaX

    fun y(p: PuntoGeo) = Math.toRadians(p.lat - punto.lat) * escalaY
    var mejor = Double.MAX_VALUE
    for (i in 0 until linea.lastIndex) {
        val ax = x(linea[i])
        val ay = y(linea[i])
        val dx = x(linea[i + 1]) - ax
        val dy = y(linea[i + 1]) - ay
        val largo2 = dx * dx + dy * dy
        // El punto está en el origen (0, 0): la proyección sobre el segmento es -(a·d) / |d|².
        val t = if (largo2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / largo2).coerceIn(0.0, 1.0)
        mejor = minOf(mejor, hypot(ax + t * dx, ay + t * dy))
    }
    return mejor
}
