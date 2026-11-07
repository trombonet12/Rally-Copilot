package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.geo.ProyeccionPlana
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.Rumbo
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Polilínea de un tramo en proyección plana local (equirectangular a la latitud media, como `CaminoPlano`) con los
 * metros acumulados reescalados a [longitudObjetivoM], para que el progreso coincida con `Tramo.longitudM`.
 * A diferencia de `CaminoPlano`, la proyección puede salirse por los extremos (prolongando el primer y el último
 * segmento) para localizar el cruce exacto de la salida y de la meta entre dos fijaciones.
 */
internal class TrazaPlana(
    original: List<PuntoGeo>,
    longitudObjetivoM: Double,
) {
    private val puntos = sinRepetidos(original)
    private val plano = ProyeccionPlana.deTraza(puntos)
    private val xs = DoubleArray(puntos.size) { plano.x(puntos[it]) }
    private val ys = DoubleArray(puntos.size) { plano.y(puntos[it]) }
    private val acumulada = DoubleArray(puntos.size)

    /** Metros del plano local por metro del tramo. */
    private val factor: Double

    init {
        require(puntos.size >= 2) { "La traza no tiene dos puntos distintos" }
        for (i in 1 until puntos.size) acumulada[i] = acumulada[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        factor = if (acumulada.last() > 0.0) longitudObjetivoM / acumulada.last() else 1.0
    }

    /** Progreso en metros del tramo (negativo antes de la salida, mayor que la longitud tras la meta) y distancia. */
    class Proyeccion(
        val progresoM: Double,
        val lateralM: Double,
    )

    /**
     * Proyecta [punto] sobre el tramo buscando solo con progreso dentro de [ventanaM] (metros del tramo). Devuelve
     * null si la ventana no toca ningún segmento. La distancia lateral es a la polilínea (con sus prolongaciones
     * extremas cuando la ventana las incluye).
     */
    fun proyectar(
        punto: PuntoGeo,
        ventanaM: ClosedFloatingPointRange<Double>,
    ): Proyeccion? {
        val x = plano.x(punto)
        val y = plano.y(punto)
        val ventana = (ventanaM.start / factor)..(ventanaM.endInclusive / factor)
        var mejor: Proyeccion? = null
        for (i in 0 until acumulada.lastIndex) {
            val candidata = proyectarEnSegmento(i, x, y, ventana)
            if (candidata != null && (mejor == null || candidata.lateralM < mejor.lateralM)) mejor = candidata
        }
        return mejor
    }

    private fun proyectarEnSegmento(
        i: Int,
        x: Double,
        y: Double,
        ventana: ClosedFloatingPointRange<Double>,
    ): Proyeccion? {
        val largo = acumulada[i + 1] - acumulada[i]
        val dx = xs[i + 1] - xs[i]
        val dy = ys[i + 1] - ys[i]
        val tDesde = (ventana.start - acumulada[i]) / largo
        val tHasta = (ventana.endInclusive - acumulada[i]) / largo
        val tMin = if (i == 0) tDesde else max(0.0, tDesde)
        val tMax = if (i == acumulada.size - 2) tHasta else min(1.0, tHasta)
        return if (largo <= 0.0 || tMin > tMax) {
            null
        } else {
            val t = (((x - xs[i]) * dx + (y - ys[i]) * dy) / (largo * largo)).coerceIn(tMin, tMax)
            Proyeccion((acumulada[i] + t * largo) * factor, hypot(x - (xs[i] + t * dx), y - (ys[i] + t * dy)))
        }
    }

    /** Rumbo (grados desde el norte) de la cuerda de ±[semiventanaM] metros alrededor de [progresoM]. */
    fun rumboEn(
        progresoM: Double,
        semiventanaM: Double,
    ): Double {
        val (x0, y0) = xyEn((progresoM - semiventanaM).coerceAtLeast(0.0) / factor)
        val (x1, y1) = xyEn((progresoM + semiventanaM) / factor)
        return Rumbo.deVector(x1 - x0, y1 - y0)
    }

    private fun xyEn(planoM: Double): Pair<Double, Double> {
        var i = 0
        while (i < acumulada.size - 2 && acumulada[i + 1] < planoM) i++
        val largo = acumulada[i + 1] - acumulada[i]
        val t = if (largo > 0.0) ((planoM - acumulada[i]) / largo).coerceIn(0.0, 1.0) else 0.0
        return (xs[i] + t * (xs[i + 1] - xs[i])) to (ys[i] + t * (ys[i + 1] - ys[i]))
    }

    private companion object {
        const val REPETIDO_M = 0.01

        /** Quita los puntos consecutivos (casi) iguales: un segmento sin longitud no tiene dirección. */
        fun sinRepetidos(puntos: List<PuntoGeo>): List<PuntoGeo> {
            val unicos = mutableListOf<PuntoGeo>()
            for (p in puntos) if (unicos.isEmpty() || unicos.last().distanciaA(p) > REPETIDO_M) unicos += p
            return unicos
        }
    }
}
