package app.trombito.rallycopilot.motor.geo

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Rumbos en grados desde el norte, sentido horario (0 = norte, 90 = este). Convención única de `:motor`. */
object Rumbo {
    private const val VUELTA = 360.0
    private const val LLANO = 180.0

    /** Rumbo inicial (0..360) de [a] a [b] por círculo máximo; en distancias cortas coincide con el plano local. */
    fun entre(
        a: PuntoGeo,
        b: PuntoGeo,
    ): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return normalizar(Math.toDegrees(atan2(y, x)))
    }

    /** Rumbo (0..360) de un vector plano de [este] y [norte] metros (o cualquier unidad común). */
    fun deVector(
        este: Double,
        norte: Double,
    ): Double = normalizar(Math.toDegrees(atan2(este, norte)))

    /** Diferencia entre dos rumbos en 0..180 grados. */
    fun diferencia(
        a: Double,
        b: Double,
    ): Double {
        val d = abs(a - b) % VUELTA
        return if (d > LLANO) VUELTA - d else d
    }

    /** [grados] llevados a 0..360 (excluido). */
    fun normalizar(grados: Double): Double = ((grados % VUELTA) + VUELTA) % VUELTA
}
