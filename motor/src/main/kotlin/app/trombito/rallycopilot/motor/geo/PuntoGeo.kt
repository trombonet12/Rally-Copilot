package app.trombito.rallycopilot.motor.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Punto WGS84 en grados decimales; la altitud (metros) es opcional porque OSM y muchos GPX no la traen. */
data class PuntoGeo(
    val lat: Double,
    val lon: Double,
    val alt: Double? = null,
) {
    init {
        require(lat in -90.0..90.0) { "Latitud fuera de rango: $lat" }
        require(lon in -180.0..180.0) { "Longitud fuera de rango: $lon" }
    }

    /** Distancia de círculo máximo (haversine) en metros; error < 0,5 % frente a WGS84, de sobra para notas. */
    fun distanciaA(otro: PuntoGeo): Double {
        val dLat = Math.toRadians(otro.lat - lat)
        val dLon = Math.toRadians(otro.lon - lon)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat)) * cos(Math.toRadians(otro.lat)) * sin(dLon / 2).pow(2)
        return 2 * RADIO_TIERRA_M * asin(sqrt(a))
    }

    companion object {
        const val RADIO_TIERRA_M = 6_371_008.8
    }
}

/** Longitud total de una polilínea en metros. */
fun List<PuntoGeo>.longitudMetros(): Double = zipWithNext { a, b -> a.distanciaA(b) }.sum()
