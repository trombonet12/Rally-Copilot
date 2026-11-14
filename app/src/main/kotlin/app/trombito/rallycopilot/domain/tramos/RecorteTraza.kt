package app.trombito.rallycopilot.domain.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.PuntoTraza
import kotlin.math.ceil

/** Puntos de traza con su distancia acumulada, para el troceado en tramos de vía. */
object RecorteTraza {
    /** Paso (m) con que se densifica el trozo de un tramo guardado, cuya traza está simplificada a ~5 m. */
    const val PASO_M = 25.0

    /** Cada punto de [puntos] con los metros acumulados desde el primero (medidos punto a punto). */
    fun conDistancias(puntos: List<PuntoGeo>): List<PuntoTraza> {
        var acumulado = 0.0
        return puntos.mapIndexed { i, p ->
            if (i > 0) acumulado += puntos[i - 1].distanciaA(p)
            PuntoTraza(p, acumulado)
        }
    }

    /**
     * El trozo de [traza] entre [desdeM] y [hastaM] (metros desde su inicio, acotados a la traza), remuestreado cada
     * [pasoM] con la distancia contada desde el inicio del trozo; vacío si el trozo no mide nada.
     */
    fun subpolilinea(
        traza: List<PuntoGeo>,
        desdeM: Double,
        hastaM: Double,
        pasoM: Double = PASO_M,
    ): List<PuntoTraza> {
        require(pasoM > 0.0) { "Paso inválido: $pasoM" }
        val acumulada = if (traza.size < 2) emptyList() else conDistancias(traza).map { it.distanciaM }
        val total = acumulada.lastOrNull() ?: 0.0
        val desde = desdeM.coerceIn(0.0, total)
        val hasta = hastaM.coerceIn(desde, total)
        return if (hasta - desde <= 0.0) emptyList() else remuestrear(traza, acumulada, desde, hasta, pasoM)
    }

    private fun remuestrear(
        traza: List<PuntoGeo>,
        acumulada: List<Double>,
        desde: Double,
        hasta: Double,
        pasoM: Double,
    ): List<PuntoTraza> {
        val pasos = ceil((hasta - desde) / pasoM).toInt().coerceAtLeast(1)
        var i = 0
        return (0..pasos).map { k ->
            val d = if (k == pasos) hasta else desde + k * pasoM
            while (i < acumulada.size - 2 && acumulada[i + 1] < d) i++
            val largo = acumulada[i + 1] - acumulada[i]
            val t = if (largo > 0.0) ((d - acumulada[i]) / largo).coerceIn(0.0, 1.0) else 0.0
            val a = traza[i]
            val b = traza[i + 1]
            PuntoTraza(PuntoGeo(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t), d - desde)
        }
    }
}
