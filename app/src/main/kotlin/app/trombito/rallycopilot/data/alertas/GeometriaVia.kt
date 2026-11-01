package app.trombito.rallycopilot.data.alertas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/** Medidas sobre una polilínea en plano local (equirectangular a la latitud del punto) que usan las alertas. */
internal object GeometriaVia {
    private const val VUELTA = 360.0
    private const val LLANO = 180.0

    /** Longitud en metros. */
    fun longitudM(puntos: List<PuntoGeo>): Double = puntos.zipWithNext().sumOf { (a, b) -> a.distanciaA(b) }

    /** Metros desde el inicio de [puntos] hasta la proyección de [punto] sobre la polilínea más cercana a él. */
    fun progresoM(
        puntos: List<PuntoGeo>,
        punto: PuntoGeo,
    ): Double {
        val escalaX = PuntoGeo.RADIO_TIERRA_M * cos(Math.toRadians(punto.lat))
        var mejor = Double.MAX_VALUE
        var progreso = 0.0
        var acumulado = 0.0
        for ((a, b) in puntos.zipWithNext()) {
            val ax = Math.toRadians(a.lon - punto.lon) * escalaX
            val ay = Math.toRadians(a.lat - punto.lat) * PuntoGeo.RADIO_TIERRA_M
            val dx = Math.toRadians(b.lon - a.lon) * escalaX
            val dy = Math.toRadians(b.lat - a.lat) * PuntoGeo.RADIO_TIERRA_M
            val largo = hypot(dx, dy)
            val t = if (largo == 0.0) 0.0 else (-(ax * dx + ay * dy) / (largo * largo)).coerceIn(0.0, 1.0)
            val distancia = hypot(ax + t * dx, ay + t * dy)
            if (distancia < mejor) {
                mejor = distancia
                progreso = acumulado + t * largo
            }
            acumulado += largo
        }
        return progreso
    }

    /** Diferencia entre dos rumbos en 0..180 grados. */
    fun diferenciaRumbo(
        a: Double,
        b: Double,
    ): Double {
        val d = abs(a - b) % VUELTA
        return if (d > LLANO) VUELTA - d else d
    }
}
