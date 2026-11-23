package app.trombito.rallycopilot.motor.ruta

import app.trombito.rallycopilot.motor.geo.ProyeccionPlana
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.distanciaASegmento
import app.trombito.rallycopilot.motor.geo.longitudMetros
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Decide si dos rutas son «la misma» para la vista previa (nunca se enseña dos veces): su longitud difiere menos de un
 * [TOLERANCIA_LONGITUD] (1 %) y la distancia de Hausdorff entre sus trazas, muestreadas cada [PASO_MUESTREO_M] (50 m),
 * es menor que [UMBRAL_HAUSDORFF_M] (30 m). Así dos rutas de Valhalla que solo difieren en el carril, en la rotonda o
 * en la densidad de puntos cuentan como una, y un rodeo de 200 m o un tramo distinto no. Puro y sin estado.
 */
object ComparadorRutas {
    const val TOLERANCIA_LONGITUD = 0.01
    const val UMBRAL_HAUSDORFF_M = 30.0
    const val PASO_MUESTREO_M = 50.0

    fun sonIguales(
        a: Ruta,
        b: Ruta,
    ): Boolean = a === b || a.puntos == b.puntos || (comparables(a, b) && hausdorffMenorQueUmbral(a, b))

    /** Con al menos dos puntos cada una y una longitud que difiere menos de [TOLERANCIA_LONGITUD]. */
    private fun comparables(
        a: Ruta,
        b: Ruta,
    ): Boolean {
        if (a.puntos.size < 2 || b.puntos.size < 2) return false
        val largoA = a.puntos.longitudMetros()
        val largoB = b.puntos.longitudMetros()
        return abs(largoA - largoB) <= TOLERANCIA_LONGITUD * max(largoA, largoB)
    }

    /** Toda muestra de cada traza está a menos de [UMBRAL_HAUSDORFF_M] de la otra (Hausdorff muestreado). */
    private fun hausdorffMenorQueUmbral(
        a: Ruta,
        b: Ruta,
    ): Boolean {
        val plano = ProyeccionPlana.deTraza(a.puntos)
        val trazaA = TrazaPlana(plano, a.puntos)
        val trazaB = TrazaPlana(plano, b.puntos)
        return trazaA.muestrasCercaDe(trazaB) && trazaB.muestrasCercaDe(trazaA)
    }

    /** Una traza en coordenadas planas (metros) para medirle distancias sin recalcular la proyección. */
    private class TrazaPlana(
        plano: ProyeccionPlana,
        puntos: List<PuntoGeo>,
    ) {
        private val xs = DoubleArray(puntos.size) { plano.x(puntos[it]) }
        private val ys = DoubleArray(puntos.size) { plano.y(puntos[it]) }
        private val segmentos = puntos.size - 1

        /**
         * Cada muestra de esta traza (su inicio, una cada [PASO_MUESTREO_M] y su final) está a menos de
         * [UMBRAL_HAUSDORFF_M] de [otra]. Busca el segmento cercano a partir del último hallado: en rutas parecidas
         * es el mismo o el siguiente, así que comparar rutas iguales cuesta lo mismo que recorrerlas una vez.
         */
        fun muestrasCercaDe(otra: TrazaPlana): Boolean {
            var ultimo = 0
            var recorridoM = 0.0
            var siguienteM = 0.0
            for (i in 0 until segmentos) {
                val largo = hypot(xs[i + 1] - xs[i], ys[i + 1] - ys[i])
                while (siguienteM <= recorridoM + largo) {
                    val t = if (largo > 0.0) (siguienteM - recorridoM) / largo else 0.0
                    val px = xs[i] + t * (xs[i + 1] - xs[i])
                    val py = ys[i] + t * (ys[i + 1] - ys[i])
                    ultimo = otra.segmentoCercano(px, py, ultimo)
                    if (ultimo < 0) return false
                    siguienteM += PASO_MUESTREO_M
                }
                recorridoM += largo
            }
            return otra.segmentoCercano(xs[segmentos], ys[segmentos], ultimo) >= 0
        }

        /** Segmento a menos de [UMBRAL_HAUSDORFF_M] de (px, py), mirando a ambos lados de [desde]; -1 si no hay. */
        private fun segmentoCercano(
            px: Double,
            py: Double,
            desde: Int,
        ): Int {
            val inicio = desde.coerceIn(0, segmentos - 1)
            var hallado = -1
            var d = 0
            while (hallado < 0 && d < segmentos) {
                val delante = inicio + d
                val detras = inicio - d
                hallado =
                    when {
                        delante < segmentos && estaCerca(px, py, delante) -> delante
                        d > 0 && detras >= 0 && estaCerca(px, py, detras) -> detras
                        else -> -1
                    }
                d++
            }
            return hallado
        }

        private fun estaCerca(
            px: Double,
            py: Double,
            i: Int,
        ): Boolean {
            val fuera =
                px < min(xs[i], xs[i + 1]) - UMBRAL_HAUSDORFF_M ||
                    px > max(xs[i], xs[i + 1]) + UMBRAL_HAUSDORFF_M ||
                    py < min(ys[i], ys[i + 1]) - UMBRAL_HAUSDORFF_M ||
                    py > max(ys[i], ys[i + 1]) + UMBRAL_HAUSDORFF_M
            return !fuera && distanciaASegmento(px, py, xs[i], ys[i], xs[i + 1], ys[i + 1]) < UMBRAL_HAUSDORFF_M
        }
    }
}
