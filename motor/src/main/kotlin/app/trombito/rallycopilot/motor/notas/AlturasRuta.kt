package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.geo.ProyeccionPlana
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.Rumbo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.PerfilVia
import kotlin.math.hypot

/**
 * Perfil de altura de una ruta: una altura cada [PASO_M] metros con la misma `s` que [Remuestreo] sin suavizado
 * (la posición acumulada de la polilínea: muestra `k` en `k * 5` m). Cada muestra se proyecta sobre la vía más
 * cercana (con el rumbo de la ruta) y se lee su perfil en la posición proyectada. Puro y determinista.
 */
object AlturasRuta {
    const val PASO_M = 5.0
    const val RADIO_VIA_M = 25.0

    /**
     * Alturas (m) cada [PASO_M] metros a lo largo de [ruta]. Las muestras sin vía a ≤ [radioViaM] o cuya vía no
     * tiene perfil se interpolan entre las vecinas válidas (en los extremos se repite la válida más próxima).
     * Si ninguna muestra es válida, o la ruta tiene menos de dos puntos distintos, devuelve una lista vacía.
     */
    fun calcular(
        ruta: List<PuntoGeo>,
        indice: IndiceVias,
        perfiles: Map<Long, PerfilVia>,
        radioViaM: Double = RADIO_VIA_M,
    ): List<Double> {
        val muestras = muestrear(ruta)
        val longitudes = HashMap<Long, Double>()
        val crudas =
            DoubleArray(muestras.size) { k ->
                val m = muestras[k]
                val coincidencia = indice.cercana(m.punto, m.rumboGrados, radioViaM)
                val perfil = coincidencia?.let { perfiles[it.via.id] }
                if (coincidencia == null || perfil == null) {
                    Double.NaN
                } else {
                    val largo = longitudes.getOrPut(coincidencia.via.id) { coincidencia.via.puntos.longitudMetros() }
                    perfil.alturaM(coincidencia.posicionEnViaM, largo)
                }
            }
        return rellenar(crudas)
    }

    /** Muestra de la ruta a `k * PASO_M` metros con su rumbo; la comparte el extractor de avisos. */
    internal class Muestra(
        val punto: PuntoGeo,
        val rumboGrados: Double?,
    )

    internal fun muestrear(ruta: List<PuntoGeo>): List<Muestra> {
        if (ruta.isEmpty()) return emptyList()
        val plano = ProyeccionPlana.deTraza(ruta)
        val xs = DoubleArray(ruta.size) { plano.x(ruta[it]) }
        val ys = DoubleArray(ruta.size) { plano.y(ruta[it]) }
        val acumulada = DoubleArray(ruta.size)
        for (i in 1 until ruta.size) acumulada[i] = acumulada[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        val total = acumulada.last()
        val n = if (ruta.size < 2 || total <= 0.0) 0 else (total / PASO_M).toInt() + 1
        val px = DoubleArray(n)
        val py = DoubleArray(n)
        val puntos = ArrayList<PuntoGeo>(n)
        var tramo = 0
        for (k in 0 until n) {
            val s = k * PASO_M
            while (tramo < acumulada.size - 2 && acumulada[tramo + 1] < s) tramo++
            val largo = acumulada[tramo + 1] - acumulada[tramo]
            val t = if (largo <= 0.0) 0.0 else ((s - acumulada[tramo]) / largo).coerceIn(0.0, 1.0)
            px[k] = xs[tramo] + t * (xs[tramo + 1] - xs[tramo])
            py[k] = ys[tramo] + t * (ys[tramo + 1] - ys[tramo])
            val a = ruta[tramo]
            val b = ruta[tramo + 1]
            puntos.add(PuntoGeo(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon)))
        }
        return List(n) { k -> Muestra(puntos[k], rumbo(px, py, k)) }
    }

    /** Rumbo (0 = norte, horario) entre la muestra anterior y la siguiente a [k]; null si solo hay una muestra. */
    private fun rumbo(
        px: DoubleArray,
        py: DoubleArray,
        k: Int,
    ): Double? {
        val a = maxOf(0, k - 1)
        val b = minOf(px.lastIndex, k + 1)
        if (a == b) return null
        return Rumbo.deVector(px[b] - px[a], py[b] - py[a])
    }

    private fun rellenar(crudas: DoubleArray): List<Double> {
        val n = crudas.size
        val antes = IntArray(n) { -1 }
        val despues = IntArray(n) { -1 }
        var ultima = -1
        for (k in 0 until n) {
            if (!crudas[k].isNaN()) ultima = k
            antes[k] = ultima
        }
        ultima = -1
        for (k in n - 1 downTo 0) {
            if (!crudas[k].isNaN()) ultima = k
            despues[k] = ultima
        }
        if (n == 0 || antes[n - 1] < 0) return emptyList()
        return List(n) { k ->
            val a = antes[k]
            val d = despues[k]
            when {
                a == d -> crudas[k]
                a < 0 -> crudas[d]
                d < 0 -> crudas[a]
                else -> crudas[a] + (crudas[d] - crudas[a]) * (k - a) / (d - a)
            }
        }
    }
}
