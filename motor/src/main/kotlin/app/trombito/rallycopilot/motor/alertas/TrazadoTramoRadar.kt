package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.Arista
import app.trombito.rallycopilot.motor.vias.Coincidencia
import app.trombito.rallycopilot.motor.vias.GrafoVias
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.Via

/**
 * Trazado de un tramo de radar para dibujarlo en el mapa: la polilínea de [inicio] a [fin] siguiendo las vías de OSM.
 *
 * Se ajusta cada extremo a su vía (≤ [AJUSTE_M]); si es la misma, se recorta entre las dos proyecciones. Si son
 * vías distintas se encadena de extremo a extremo: en cada fin de vía se salta a la vía conectada que más acerque al
 * [fin] (o directamente a la del [fin]), hasta [MAX_VIAS] vías. Sin vías cerca, sin encadenado posible o con un
 * recorrido mayor que [FACTOR_RECTA] veces la recta más [MARGEN_M], el trazado es la línea recta de dos puntos.
 */
object TrazadoTramoRadar {
    const val AJUSTE_M = 40.0
    private const val FACTOR_RECTA = 2.5
    private const val MARGEN_M = 300.0
    private const val MAX_VIAS = 12
    private const val MIN_AVANCE_M = 1.0

    fun de(
        inicio: PuntoGeo,
        fin: PuntoGeo,
        indice: IndiceVias,
        grafo: GrafoVias,
    ): List<PuntoGeo> {
        val recta = listOf(inicio, fin)
        val a = indice.cercana(inicio, null, AJUSTE_M)
        val b = indice.cercana(fin, null, AJUSTE_M)
        val tope = inicio.distanciaA(fin) * FACTOR_RECTA + MARGEN_M
        val camino = if (a == null || b == null) null else recorrer(a, b, fin, grafo, tope)
        return if (camino == null) recta else listOf(inicio) + camino + listOf(fin)
    }

    /** Una vía recorrida desde [desdeM] (metros desde su primer punto) en el sentido [adelante] de sus puntos. */
    private class Paso(
        val via: Via,
        val desdeM: Double,
        val adelante: Boolean,
    )

    @Suppress("ReturnCount") // salidas tempranas del encadenado: cada una es un «no hay camino» o el final
    private fun recorrer(
        a: Coincidencia,
        b: Coincidencia,
        fin: PuntoGeo,
        grafo: GrafoVias,
        tope: Double,
    ): List<PuntoGeo>? {
        if (a.via.id == b.via.id) return recorte(a.via, a.posicionEnViaM, b.posicionEnViaM).takeIf { largo(it) <= tope }
        val acumulado = ArrayList<PuntoGeo>()
        var largo = 0.0
        var paso = Paso(a.via, a.posicionEnViaM, haciaElFin(a.via, fin))
        repeat(MAX_VIAS) {
            val extremo = if (paso.adelante) paso.via.puntos.last() else paso.via.puntos.first()
            val hasta = if (paso.adelante) largoVia(paso.via) else 0.0
            val trozo = recorte(paso.via, paso.desdeM, hasta)
            acumulado += trozo
            largo += largo(trozo)
            if (largo > tope) return null
            val conectadas = grafo.aristas(extremo).filter { it.via.id != paso.via.id }
            val directa = conectadas.firstOrNull { it.via.id == b.via.id }
            if (directa != null) {
                val resto = recorte(directa.via, posicionDe(directa.via, directa.indice), b.posicionEnViaM)
                acumulado += resto
                return acumulado.takeIf { largo + largo(resto) <= tope }
            }
            paso = siguiente(conectadas, extremo, fin) ?: return null
        }
        return null
    }

    /** La vía conectada (y su sentido) cuyo otro extremo queda más cerca de [fin] que [desde]; `null` si ninguna. */
    private fun siguiente(
        conectadas: List<Arista>,
        desde: PuntoGeo,
        fin: PuntoGeo,
    ): Paso? {
        val actual = desde.distanciaA(fin)
        var mejor: Paso? = null
        var mejorDistancia = actual - MIN_AVANCE_M
        for (arista in conectadas) {
            val puntos = arista.via.puntos
            for (adelante in listOf(true, false)) {
                val otro = if (adelante) puntos.last() else puntos.first()
                val sale = if (adelante) arista.indice < puntos.lastIndex else arista.indice > 0
                val d = otro.distanciaA(fin)
                if (sale && d < mejorDistancia) {
                    mejorDistancia = d
                    mejor = Paso(arista.via, posicionDe(arista.via, arista.indice), adelante)
                }
            }
        }
        return mejor
    }

    /** El sentido de los puntos de [via] cuyo extremo queda más cerca de [fin]. */
    private fun haciaElFin(
        via: Via,
        fin: PuntoGeo,
    ): Boolean = via.puntos.last().distanciaA(fin) <= via.puntos.first().distanciaA(fin)

    private fun largoVia(via: Via): Double = largo(via.puntos)

    private fun largo(puntos: List<PuntoGeo>): Double = puntos.zipWithNext().sumOf { (p, q) -> p.distanciaA(q) }

    /** Metros desde el primer punto de [via] hasta su punto [indice]. */
    private fun posicionDe(
        via: Via,
        indice: Int,
    ): Double = largo(via.puntos.subList(0, indice + 1))

    /**
     * Puntos de [via] entre [desdeM] y [hastaM] (metros desde su primer punto), con los extremos interpolados y en el
     * orden de [desdeM] a [hastaM].
     */
    private fun recorte(
        via: Via,
        desdeM: Double,
        hastaM: Double,
    ): List<PuntoGeo> {
        val minimo = minOf(desdeM, hastaM)
        val maximo = maxOf(desdeM, hastaM)
        val dentro = ArrayList<PuntoGeo>()
        var acumulado = 0.0
        dentro += en(via.puntos, minimo)
        for ((p, q) in via.puntos.zipWithNext()) {
            acumulado += p.distanciaA(q)
            if (acumulado > minimo && acumulado < maximo) dentro += q
        }
        dentro += en(via.puntos, maximo)
        return if (desdeM <= hastaM) dentro else dentro.asReversed()
    }

    /** Punto a [metros] del inicio de [puntos] (interpolado dentro del segmento). */
    private fun en(
        puntos: List<PuntoGeo>,
        metros: Double,
    ): PuntoGeo {
        var acumulado = 0.0
        for ((p, q) in puntos.zipWithNext()) {
            val segmento = p.distanciaA(q)
            if (metros <= acumulado + segmento) {
                val t = if (segmento == 0.0) 0.0 else ((metros - acumulado) / segmento).coerceIn(0.0, 1.0)
                return PuntoGeo(p.lat + (q.lat - p.lat) * t, p.lon + (q.lon - p.lon) * t)
            }
            acumulado += segmento
        }
        return puntos.last()
    }
}
