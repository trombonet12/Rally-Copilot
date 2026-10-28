package app.trombito.rallycopilot.motor.velocidad

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Una lectura del GPS; velocidad (m/s), rumbo (grados desde el norte) y precisión (m) pueden faltar. */
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
 * @param maxSaltoMs velocidad implícita (m/s) por encima de la cual una fijación se considera un salto y se descarta.
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
     * Las fijaciones desordenadas, demasiado juntas (sin velocidad propia) o con un salto imposible no
     * modifican el estado.
     */
    fun actualizar(f: Fijacion): Double? {
        val prev = anterior
        if (!aceptable(prev, f)) return vigente(f.tiempoMs)

        actualizarRumbo(prev, f)
        val propia = f.velocidadMs?.takeIf { it.isFinite() && it >= 0.0 }
        velocidad = propia ?: prev?.let { velocidadEntre(it, f) }
        tiempoVelocidad = f.tiempoMs
        anterior = f
        return vigente(f.tiempoMs)
    }

    /** Velocidad (m/s) si la última estimación no tiene más de `caducidadMs` respecto a [ahoraMs]. */
    fun vigente(ahoraMs: Long): Double? = velocidad?.takeIf { ahoraMs - tiempoVelocidad <= caducidadMs }

    /** Último rumbo conocido en grados [0, 360): el del GPS o el calculado entre posiciones. */
    fun rumbo(): Double? = rumboActual

    private fun aceptable(
        prev: Fijacion?,
        f: Fijacion,
    ): Boolean {
        if (prev == null) return true
        val dtMs = f.tiempoMs - prev.tiempoMs
        return when {
            dtMs <= 0 -> false
            dtMs < DT_MIN_MS -> f.velocidadMs != null
            else -> prev.punto.distanciaA(f.punto) / (dtMs / MS_POR_S) <= maxSaltoMs
        }
    }

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
