package app.trombito.rallycopilot.motor.velocidad

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Una lectura del GPS; velocidad (m/s), rumbo (grados desde el norte) y precisión (m) pueden faltar.
 * `tiempoMs` es un reloj monótono en ms (no la hora UTC del receptor, que puede repetirse o saltar).
 */
data class Fijacion(
    val punto: PuntoGeo,
    val tiempoMs: Long,
    val velocidadMs: Double?,
    val rumboGrados: Double?,
    val precisionM: Double?,
)

/** Convierte m/s a km/h redondeando al entero; negativos, NaN e infinitos dan 0. */
fun kmh(metrosPorSegundo: Double): Int =
    if (!metrosPorSegundo.isFinite() || metrosPorSegundo <= 0.0) 0 else (metrosPorSegundo * 3.6).roundToInt()

/**
 * Estima velocidad y rumbo a partir de fijaciones GPS (en orden temporal).
 *
 * @param maxSaltoMs velocidad implícita (m/s) por encima de la cual una fijación es un salto (no se calcula velocidad).
 * @param caducidadMs antigüedad máxima de la velocidad para seguir siendo [vigente].
 */
class EstimadorVelocidad(
    private val maxSaltoMs: Double = 70.0,
    private val caducidadMs: Long = 5000,
) {
    private var anterior: Fijacion? = null
    private var velocidad: Double? = null
    private var tiempoVelocidad: Long = 0
    private var rumboActual: Double? = null

    /**
     * Incorpora una fijación y devuelve la velocidad vigente (m/s) o null si no se conoce.
     * - La velocidad del receptor (finita y >= 0) manda siempre, sin filtro de salto.
     * - Con el mismo tiempo que la anterior solo cuenta la velocidad del receptor (no la referencia por posición);
     *   las desordenadas se descartan.
     * - Sin velocidad del receptor, las fijaciones a menos de 500 ms se ignoran; si implican más de `maxSaltoMs`
     *   no se calcula velocidad (se conserva la anterior) ni rumbo entre posiciones, pero SE REANCLA la referencia
     *   para que un salto real no bloquee las siguientes.
     */
    fun actualizar(f: Fijacion): Double? {
        val prev = anterior
        val propia = f.velocidadMs?.takeIf { it.isFinite() && it >= 0.0 }
        val dtMs = prev?.let { f.tiempoMs - it.tiempoMs } ?: Long.MAX_VALUE
        when {
            dtMs < 0 -> Unit
            dtMs == 0L && propia != null -> incorporar(prev, f, propia, salto = false)
            dtMs == 0L || (dtMs < DT_MIN_MS && propia == null) -> Unit
            else -> incorporar(prev, f, propia, salto = esSalto(prev, f, dtMs))
        }
        return vigente(f.tiempoMs)
    }

    private fun esSalto(
        prev: Fijacion?,
        f: Fijacion,
        dtMs: Long,
    ): Boolean = prev != null && prev.punto.distanciaA(f.punto) / (dtMs / MS_POR_S) > maxSaltoMs

    private fun incorporar(
        prev: Fijacion?,
        f: Fijacion,
        propia: Double?,
        salto: Boolean,
    ) {
        actualizarRumbo(if (salto) null else prev, f)
        val nueva = propia ?: if (salto) null else prev?.let { velocidadEntre(it, f) }
        if (nueva != null || !salto) {
            velocidad = nueva
            tiempoVelocidad = f.tiempoMs
        }
        // Mismo instante: no sirve de referencia para calcular por posición.
        if (prev == null || f.tiempoMs != prev.tiempoMs) anterior = f
    }

    /** Velocidad (m/s) si la última estimación no tiene más de `caducidadMs` respecto a [ahoraMs]. */
    fun vigente(ahoraMs: Long): Double? = velocidad?.takeIf { ahoraMs - tiempoVelocidad <= caducidadMs }

    /** Último rumbo conocido en grados [0, 360): el del GPS o el calculado entre posiciones. */
    fun rumbo(): Double? = rumboActual

    private fun velocidadEntre(
        prev: Fijacion,
        f: Fijacion,
    ): Double? {
        val dtMs = f.tiempoMs - prev.tiempoMs
        return if (dtMs in DT_MIN_MS..DT_MAX_MS) prev.punto.distanciaA(f.punto) / (dtMs / MS_POR_S) else null
    }

    private fun actualizarRumbo(
        prev: Fijacion?,
        f: Fijacion,
    ) {
        val delGps = f.rumboGrados?.takeIf { it.isFinite() }
        val seMovio = prev != null && prev.punto.distanciaA(f.punto) > DISTANCIA_MIN_RUMBO_M
        if (delGps != null) {
            rumboActual = ((delGps % GRADOS_VUELTA) + GRADOS_VUELTA) % GRADOS_VUELTA
        } else if (prev != null && seMovio) {
            rumboActual = rumboEntre(prev.punto, f.punto)
        }
    }

    private fun rumboEntre(
        a: PuntoGeo,
        b: PuntoGeo,
    ): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + GRADOS_VUELTA) % GRADOS_VUELTA
    }

    private companion object {
        const val DT_MIN_MS = 500L
        const val DT_MAX_MS = 5000L
        const val MS_POR_S = 1000.0
        const val DISTANCIA_MIN_RUMBO_M = 3.0
        const val GRADOS_VUELTA = 360.0
    }
}
