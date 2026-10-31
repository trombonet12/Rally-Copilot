package app.trombito.rallycopilot.data.notas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.LimiteVelocidad
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Polilínea con distancia acumulada (haversine, igual que las posiciones de la hoja de notas) para proyectar una
 * posición sobre ella y sacar el punto y el rumbo a una distancia dada. Necesita al menos dos puntos. Proyectar es
 * O(n), de sobra para una predicción de 2 km a 1 Hz.
 */
internal class RutaMedida(
    val puntos: List<PuntoGeo>,
) {
    /** Posición proyectada (m desde el inicio) y distancia (m) de la posición a la polilínea. */
    class Proyeccion(
        val posicionM: Double,
        val distanciaM: Double,
    )

    private val acumulada = DoubleArray(puntos.size)
    private val origen = puntos.first()
    private val escalaX = PuntoGeo.RADIO_TIERRA_M * cos(Math.toRadians(puntos.sumOf { it.lat } / puntos.size))
    private val xs = DoubleArray(puntos.size) { Math.toRadians(puntos[it].lon - origen.lon) * escalaX }
    private val ys = DoubleArray(puntos.size) { Math.toRadians(puntos[it].lat - origen.lat) * PuntoGeo.RADIO_TIERRA_M }

    init {
        require(puntos.size >= 2) { "Una ruta medida necesita al menos dos puntos" }
        for (i in 1 until puntos.size) acumulada[i] = acumulada[i - 1] + puntos[i - 1].distanciaA(puntos[i])
    }

    val longitudM: Double get() = acumulada.last()

    fun proyectar(punto: PuntoGeo): Proyeccion {
        val px = Math.toRadians(punto.lon - origen.lon) * escalaX
        val py = Math.toRadians(punto.lat - origen.lat) * PuntoGeo.RADIO_TIERRA_M
        var mejorDistancia = Double.MAX_VALUE
        var mejorPosicion = 0.0
        for (i in 0 until puntos.size - 1) {
            val t = fraccion(i, px, py)
            val d = hypot(px - (xs[i] + t * (xs[i + 1] - xs[i])), py - (ys[i] + t * (ys[i + 1] - ys[i])))
            if (d < mejorDistancia) {
                mejorDistancia = d
                mejorPosicion = acumulada[i] + t * (acumulada[i + 1] - acumulada[i])
            }
        }
        return Proyeccion(mejorPosicion, mejorDistancia)
    }

    /** Punto a [posicionM] metros del inicio (saturado a los extremos). */
    fun puntoEn(posicionM: Double): PuntoGeo {
        val i = segmentoEn(posicionM)
        val largo = acumulada[i + 1] - acumulada[i]
        val t = if (largo > 0.0) ((posicionM - acumulada[i]) / largo).coerceIn(0.0, 1.0) else 0.0
        val a = puntos[i]
        val b = puntos[i + 1]
        return PuntoGeo(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
    }

    /** Rumbo (0 norte, horario) del segmento que contiene [posicionM]. */
    fun rumboEn(posicionM: Double): Double {
        val i = segmentoEn(posicionM)
        val grados = Math.toDegrees(atan2(xs[i + 1] - xs[i], ys[i + 1] - ys[i]))
        return if (grados < 0.0) grados + GRADOS_VUELTA else grados
    }

    /** Límite de velocidad (km/h) de la vía que casa a [posicionM] en el sentido de la marcha, o null. */
    fun limiteKmhEn(
        indice: IndiceVias,
        posicionM: Double,
    ): Double? =
        indice
            .cercana(puntoEn(posicionM), rumboEn(posicionM), RADIO_VIA_M)
            ?.let { LimiteVelocidad.aplicable(it.via, it.adelante) }
            ?.toDouble()

    /** Índice del segmento [i, i+1] que contiene la posición (búsqueda binaria). */
    private fun segmentoEn(posicionM: Double): Int {
        var bajo = 0
        var alto = puntos.size - 2
        while (bajo < alto) {
            val medio = (bajo + alto + 1) ushr 1
            if (acumulada[medio] <= posicionM) bajo = medio else alto = medio - 1
        }
        return bajo
    }

    private fun fraccion(
        i: Int,
        px: Double,
        py: Double,
    ): Double {
        val dx = xs[i + 1] - xs[i]
        val dy = ys[i + 1] - ys[i]
        val largo2 = dx * dx + dy * dy
        return if (largo2 == 0.0) 0.0 else (((px - xs[i]) * dx + (py - ys[i]) * dy) / largo2).coerceIn(0.0, 1.0)
    }

    private companion object {
        const val RADIO_VIA_M = 25.0
        const val GRADOS_VUELTA = 360.0
    }
}
