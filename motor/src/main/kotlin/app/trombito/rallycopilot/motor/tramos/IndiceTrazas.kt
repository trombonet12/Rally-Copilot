package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.geo.ProyeccionPlana
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import java.util.TreeSet
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToLong

/**
 * Índice espacial de las trazas de los tramos: una rejilla de [CELDA_M] metros en una proyección plana común. Cada
 * traza se muestrea cada [PASO_MUESTRA_M] metros y se anota en las celdas de sus muestras; [cercanos] devuelve los
 * tramos de la celda de un punto y de sus ocho vecinas (cubre de sobra los 20 m de radio de entrada: la muestra más
 * próxima dista a lo sumo 50 m del segmento). Con cientos de tramos guardados, cada fijación solo mira unos pocos.
 * Las [TrazaPlana] se crean al primer uso.
 */
internal class IndiceTrazas(
    private val tramos: List<Tramo>,
) {
    private val proyeccion: ProyeccionPlana? = tramos.firstOrNull()?.let { ProyeccionPlana(it.traza.first()) }
    private val celdas = HashMap<Long, MutableList<Int>>()
    private val trazas = arrayOfNulls<TrazaPlana>(tramos.size)

    init {
        for (i in tramos.indices) if (tieneLongitud(tramos[i])) anotar(i)
    }

    /** Traza plana del tramo [i] (solo para índices devueltos por [cercanos]). */
    fun traza(i: Int): TrazaPlana =
        trazas[i] ?: TrazaPlana(tramos[i].traza, tramos[i].longitudM).also { trazas[i] = it }

    /** Índices (en orden creciente) de los tramos con alguna muestra en la celda de [punto] o en sus vecinas. */
    fun cercanos(punto: PuntoGeo): Set<Int> {
        val plano = proyeccion ?: return emptySet()
        val ix = celda(plano.x(punto))
        val iy = celda(plano.y(punto))
        val resultado = TreeSet<Int>()
        for (dx in -1..1) for (dy in -1..1) celdas[clave(ix + dx, iy + dy)]?.let { resultado.addAll(it) }
        return resultado
    }

    private fun anotar(i: Int) {
        val plano = proyeccion ?: return
        val traza = tramos[i].traza
        val claves = HashSet<Long>()
        for (k in 0 until traza.lastIndex) {
            val x0 = plano.x(traza[k])
            val y0 = plano.y(traza[k])
            val dx = plano.x(traza[k + 1]) - x0
            val dy = plano.y(traza[k + 1]) - y0
            val partes = ceil(hypot(dx, dy) / PASO_MUESTRA_M).toInt().coerceAtLeast(1)
            for (s in 0..partes) claves += clave(celda(x0 + dx * s / partes), celda(y0 + dy * s / partes))
        }
        for (c in claves) celdas.getOrPut(c) { mutableListOf() }.add(i)
    }

    private fun tieneLongitud(tramo: Tramo): Boolean =
        tramo.traza.zipWithNext().any { (a, b) -> a.distanciaA(b) > LONGITUD_UTIL_M }

    private fun celda(metros: Double): Int = floor(metros / CELDA_M).toInt()

    private fun clave(
        ix: Int,
        iy: Int,
    ): Long = ix.toLong() * FACTOR_CLAVE + iy

    private companion object {
        const val CELDA_M = 250.0
        const val PASO_MUESTRA_M = 100.0
        const val LONGITUD_UTIL_M = 0.05
        const val FACTOR_CLAVE = 4_294_967_296L
    }
}

/**
 * Mejor tiempo previo en un trozo de carretera entre todos los tramos y pasadas guardados: para cada tramo cercano a
 * los dos extremos del trozo (a [DetectorSubtramo.RADIO_M] o menos, en el mismo sentido y con longitud compatible) y
 * cada pasada que cubra el trozo, el tiempo entre ambos extremos; el mínimo.
 */
internal class ComparadorSubtramos(
    private val indice: IndiceTrazas,
    private val tramos: List<Tramo>,
    private val pasadas: Map<String, List<Pasada>>,
) {
    /** Mejor tiempo (ms) entre [desde] y [hasta] (a [recorridoM] m de marcha), o null si ninguna pasada lo cubre. */
    fun mejorMs(
        desde: PuntoGeo,
        hasta: PuntoGeo,
        recorridoM: Double,
    ): Long? =
        indice
            .cercanos(desde)
            .intersect(indice.cercanos(hasta))
            .mapNotNull { mejorEn(it, desde, hasta, recorridoM) }
            .minOrNull()

    /** Tiempo (ms) de la pasada de menor total del tramo [tramoId] entre [desdeM] y [hastaM], o null sin referencia. */
    fun tiempoDeReferencia(
        tramoId: String,
        desdeM: Double,
        hastaM: Double,
    ): Long? =
        pasadas[tramoId]
            .orEmpty()
            .filter { it.tiempoEn(desdeM) != null }
            .minByOrNull { it.totalMs }
            ?.let { tiempoEntre(it, desdeM, hastaM) }

    private fun mejorEn(
        i: Int,
        desde: PuntoGeo,
        hasta: PuntoGeo,
        recorridoM: Double,
    ): Long? {
        val tramo = tramos[i]
        val traza = indice.traza(i)
        val ventana = 0.0..tramo.longitudM
        val a = traza.proyectar(desde, ventana)?.takeIf { it.lateralM <= DetectorSubtramo.RADIO_M }
        val b = traza.proyectar(hasta, ventana)?.takeIf { it.lateralM <= DetectorSubtramo.RADIO_M }
        if (a == null || b == null) return null
        val largoM = b.progresoM - a.progresoM
        return if (largoM > 0.0 && abs(largoM - recorridoM) <= TOLERANCIA_LONGITUD * recorridoM) {
            pasadas[tramo.id].orEmpty().mapNotNull { tiempoEntre(it, a.progresoM, b.progresoM) }.minOrNull()
        } else {
            null
        }
    }

    private fun tiempoEntre(
        pasada: Pasada,
        desdeM: Double,
        hastaM: Double,
    ): Long? {
        val t0 = pasada.tiempoEn(desdeM)
        val t1 = pasada.tiempoEn(hastaM)
        return if (t0 != null && t1 != null) (t1 - t0).roundToLong() else null
    }

    private companion object {
        /** Diferencia máxima (fracción) entre la longitud del trozo en el tramo y la recorrida. */
        const val TOLERANCIA_LONGITUD = 0.15
    }
}
