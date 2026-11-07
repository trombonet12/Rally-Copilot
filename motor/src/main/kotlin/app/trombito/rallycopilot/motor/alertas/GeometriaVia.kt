package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.geo.ProyeccionPlana
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.fraccionEnSegmento
import kotlin.math.hypot

/** Medidas sobre una polilínea en plano local (equirectangular a la latitud del punto) que usan las alertas. */
object GeometriaVia {
    /** Metros desde el inicio de [puntos] hasta la proyección de [punto] sobre la polilínea más cercana a él. */
    fun progresoM(
        puntos: List<PuntoGeo>,
        punto: PuntoGeo,
    ): Double {
        val plano = ProyeccionPlana(punto)
        var mejor = Double.MAX_VALUE
        var progreso = 0.0
        var acumulado = 0.0
        for ((a, b) in puntos.zipWithNext()) {
            val ax = plano.x(a)
            val ay = plano.y(a)
            val bx = plano.x(b)
            val by = plano.y(b)
            val largo = hypot(bx - ax, by - ay)
            val t = fraccionEnSegmento(0.0, 0.0, ax, ay, bx, by)
            val distancia = hypot(ax + t * (bx - ax), ay + t * (by - ay))
            if (distancia < mejor) {
                mejor = distancia
                progreso = acumulado + t * largo
            }
            acumulado += largo
        }
        return progreso
    }
}
