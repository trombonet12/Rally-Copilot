package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.geo.ProyeccionPlana
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.Rumbo
import app.trombito.rallycopilot.motor.geo.fraccionEnSegmento
import app.trombito.rallycopilot.motor.vias.Coincidencia
import app.trombito.rallycopilot.motor.vias.IndiceVias
import kotlin.math.hypot

/**
 * Polilínea con distancia acumulada (haversine, igual que las posiciones de la hoja de notas) para proyectar una
 * posición sobre ella y sacar el punto y el rumbo a una distancia dada. Necesita al menos dos puntos. Proyectar es
 * O(n), de sobra para una predicción de 2 km a 1 Hz.
 */
class RutaMedida(
    val puntos: List<PuntoGeo>,
) {
    /** Posición proyectada (m desde el inicio) y distancia (m) de la posición a la polilínea. */
    class Proyeccion(
        val posicionM: Double,
        val distanciaM: Double,
    )

    private val acumulada = DoubleArray(puntos.size)
    private val plano = ProyeccionPlana.deTraza(puntos)
    private val xs = DoubleArray(puntos.size) { plano.x(puntos[it]) }
    private val ys = DoubleArray(puntos.size) { plano.y(puntos[it]) }

    init {
        require(puntos.size >= 2) { "Una ruta medida necesita al menos dos puntos" }
        for (i in 1 until puntos.size) acumulada[i] = acumulada[i - 1] + puntos[i - 1].distanciaA(puntos[i])
    }

    val longitudM: Double get() = acumulada.last()

    fun proyectar(punto: PuntoGeo): Proyeccion {
        val px = plano.x(punto)
        val py = plano.y(punto)
        var mejorDistancia = Double.MAX_VALUE
        var mejorPosicion = 0.0
        for (i in 0 until puntos.size - 1) {
            val t = fraccionEnSegmento(px, py, xs[i], ys[i], xs[i + 1], ys[i + 1])
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
        return Rumbo.deVector(xs[i + 1] - xs[i], ys[i + 1] - ys[i])
    }

    /** Vía que casa a [posicionM] en el sentido de la marcha, o null si no hay ninguna a [RADIO_VIA_M]. */
    fun viaEn(
        indice: IndiceVias,
        posicionM: Double,
    ): Coincidencia? = indice.cercana(puntoEn(posicionM), rumboEn(posicionM), RADIO_VIA_M)

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

    private companion object {
        const val RADIO_VIA_M = 25.0
    }
}
