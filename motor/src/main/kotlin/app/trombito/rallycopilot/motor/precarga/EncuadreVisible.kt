package app.trombito.rallycopilot.motor.precarga

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Rectángulo que ve un mapa MapLibre orientado al norte y sin inclinación. El mundo mide `512 · 2^zoom` puntos de
 * ancho (MapLibre usa teselas de 512 puntos independientes de la densidad), así que basta el tamaño de la vista en dp.
 */
object EncuadreVisible {
    private const val PUNTOS_MUNDO_A_ZOOM_0 = 512.0

    fun rectangulo(
        latitud: Double,
        longitud: Double,
        zoom: Double,
        anchoDp: Double,
        altoDp: Double,
    ): Rectangulo {
        require(anchoDp > 0 && altoDp > 0) { "Vista sin tamaño" }
        val mundo = PUNTOS_MUNDO_A_ZOOM_0 * 2.0.pow(zoom)
        val dLon = anchoDp / mundo * 360.0 / 2.0
        val yCentro = mercatorY(latitud)
        val dy = altoDp / mundo / 2.0
        val norte = latitudDe((yCentro - dy).coerceIn(0.0, 1.0))
        val sur = latitudDe((yCentro + dy).coerceIn(0.0, 1.0))
        return Rectangulo((longitud - dLon).coerceAtLeast(-180.0), sur, (longitud + dLon).coerceAtMost(180.0), norte)
    }

    /** Y normalizada de Web Mercator: 0 arriba (norte), 1 abajo. */
    private fun mercatorY(latitud: Double): Double {
        val r = Math.toRadians(latitud.coerceIn(-MAX_LAT, MAX_LAT))
        return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0
    }

    private fun latitudDe(y: Double): Double = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * y))))

    private const val MAX_LAT = 85.0
}
