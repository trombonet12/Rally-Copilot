package app.trombito.rallycopilot.ui.miscarreteras

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.mapa.TramoCalor

/** El trazo sin mapa base de una vía conducida. */
internal fun TramoCalor.aTrazo() = TrazoCarretera(puntos, pasadas, velMediaKmh)

/**
 * Las esquinas suroeste y noreste de la caja que encierra todas las [tramos], para encuadrar el mapa; vacío si no hay
 * ningún punto.
 */
internal fun esquinasDe(tramos: List<TramoCalor>): List<PuntoGeo> {
    var minLat = Double.POSITIVE_INFINITY
    var minLon = Double.POSITIVE_INFINITY
    var maxLat = Double.NEGATIVE_INFINITY
    var maxLon = Double.NEGATIVE_INFINITY
    var hayPuntos = false
    for (tramo in tramos) {
        for (punto in tramo.puntos) {
            hayPuntos = true
            if (punto.lat < minLat) minLat = punto.lat
            if (punto.lat > maxLat) maxLat = punto.lat
            if (punto.lon < minLon) minLon = punto.lon
            if (punto.lon > maxLon) maxLon = punto.lon
        }
    }
    return if (hayPuntos) listOf(PuntoGeo(minLat, minLon), PuntoGeo(maxLat, maxLon)) else emptyList()
}
