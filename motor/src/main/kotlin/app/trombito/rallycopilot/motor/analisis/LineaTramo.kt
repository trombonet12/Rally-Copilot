package app.trombito.rallycopilot.motor.analisis

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import java.util.Arrays

/**
 * Traza de un tramo con los metros acumulados reescalados a [longitudM] (como `Tramo.longitudM`), para pasar de
 * distancia desde la salida a posición y al revés sin desfases con el cronometraje.
 */
internal class LineaTramo(
    private val traza: List<PuntoGeo>,
    longitudM: Double,
) {
    private val acumulada = DoubleArray(traza.size)
    private val factor: Double

    init {
        for (i in 1 until traza.size) acumulada[i] = acumulada[i - 1] + traza[i - 1].distanciaA(traza[i])
        factor = if (acumulada.last() > 0.0) longitudM / acumulada.last() else 1.0
        for (i in acumulada.indices) acumulada[i] *= factor
    }

    /** Posición a [distanciaM] metros de la salida (acotada a los extremos). */
    fun puntoEn(distanciaM: Double): PuntoGeo {
        val d = distanciaM.coerceIn(0.0, acumulada.last())
        val busqueda = Arrays.binarySearch(acumulada, d)
        val i = if (busqueda >= 0) busqueda else -busqueda - 2
        return if (i >= traza.lastIndex) {
            traza.last()
        } else {
            val largo = acumulada[i + 1] - acumulada[i]
            val t = if (largo > 0.0) (d - acumulada[i]) / largo else 0.0
            PuntoGeo(
                traza[i].lat + t * (traza[i + 1].lat - traza[i].lat),
                traza[i].lon + t * (traza[i + 1].lon - traza[i].lon),
            )
        }
    }

    /** Polilínea entre [desdeM] y [hastaM] con los vértices de la traza que caen dentro. */
    fun entre(
        desdeM: Double,
        hastaM: Double,
    ): List<PuntoGeo> {
        val intermedios = traza.indices.filter { acumulada[it] > desdeM && acumulada[it] < hastaM }.map { traza[it] }
        return listOf(puntoEn(desdeM)) + intermedios + puntoEn(hastaM)
    }
}
