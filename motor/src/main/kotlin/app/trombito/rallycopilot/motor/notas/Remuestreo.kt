package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.geo.ProyeccionPlana
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Ventana del suavizado gaussiano: ±3 sigmas; el exponente de la campana es −z²/2. */
private const val SIGMAS_VENTANA = 3.0
private const val EXPONENTE_GAUSS = -0.5

/**
 * Traza plana en metros (x al este, y al norte, origen en el primer punto) con muestras equiespaciadas.
 * [s] es la distancia en metros desde el inicio de la polilínea original hasta cada muestra.
 */
class TrazaPlana(
    val x: DoubleArray,
    val y: DoubleArray,
    val s: DoubleArray,
) {
    val size: Int get() = x.size
}

/**
 * Primera etapa del motor de notas: proyecta la polilínea a un plano local (equirectangular centrado en la
 * latitud media), la remuestrea a paso fijo por longitud de arco y la suaviza con un núcleo gaussiano antes de
 * derivar. Puro y determinista.
 */
object Remuestreo {
    const val PASO_M = 5.0
    const val SIGMA_M = 10.0

    private val VACIA = TrazaPlana(DoubleArray(0), DoubleArray(0), DoubleArray(0))

    /**
     * Devuelve la traza remuestreada cada [pasoM] metros y suavizada con sigma = [sigmaM] (ventana ±3 sigma,
     * renormalizada en los extremos, donde faltan vecinos). Con menos de dos puntos distintos devuelve una traza
     * vacía. La última muestra es la última múltiplo de [pasoM] que cabe en la polilínea; con [sigmaM] = 0 no se
     * suaviza.
     */
    fun remuestrear(
        puntos: List<PuntoGeo>,
        pasoM: Double = PASO_M,
        sigmaM: Double = SIGMA_M,
    ): TrazaPlana {
        require(pasoM.isFinite() && pasoM > 0.0) { "Paso inválido: $pasoM" }
        require(sigmaM.isFinite() && sigmaM >= 0.0) { "Sigma inválida: $sigmaM" }
        return if (puntos.size < 2) VACIA else muestrear(puntos, pasoM, sigmaM)
    }

    private fun muestrear(
        puntos: List<PuntoGeo>,
        pasoM: Double,
        sigmaM: Double,
    ): TrazaPlana {
        val (xs, ys, acumulada) = proyectar(puntos)
        val total = acumulada.last()
        if (total <= 0.0) return VACIA
        val n = (total / pasoM).toInt() + 1
        val rx = DoubleArray(n)
        val ry = DoubleArray(n)
        val rs = DoubleArray(n) { it * pasoM }
        var tramo = 0
        for (k in 0 until n) {
            while (tramo < acumulada.size - 2 && acumulada[tramo + 1] < rs[k]) tramo++
            val largo = acumulada[tramo + 1] - acumulada[tramo]
            val t = if (largo <= 0.0) 0.0 else ((rs[k] - acumulada[tramo]) / largo).coerceIn(0.0, 1.0)
            rx[k] = xs[tramo] + t * (xs[tramo + 1] - xs[tramo])
            ry[k] = ys[tramo] + t * (ys[tramo + 1] - ys[tramo])
        }
        return TrazaPlana(suavizar(rx, pasoM, sigmaM), suavizar(ry, pasoM, sigmaM), rs)
    }

    private fun proyectar(puntos: List<PuntoGeo>): Triple<DoubleArray, DoubleArray, DoubleArray> {
        val plano = ProyeccionPlana.deTraza(puntos)
        val xs = DoubleArray(puntos.size) { plano.x(puntos[it]) }
        val ys = DoubleArray(puntos.size) { plano.y(puntos[it]) }
        val acumulada = DoubleArray(puntos.size)
        for (i in 1 until puntos.size) {
            acumulada[i] = acumulada[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        }
        return Triple(xs, ys, acumulada)
    }

    private fun suavizar(
        valores: DoubleArray,
        pasoM: Double,
        sigmaM: Double,
    ): DoubleArray {
        if (sigmaM == 0.0) return valores
        val semiancho = (SIGMAS_VENTANA * sigmaM / pasoM).roundToInt().coerceAtLeast(1)
        val pesos =
            DoubleArray(2 * semiancho + 1) {
                val z = (it - semiancho) * pasoM / sigmaM
                exp(EXPONENTE_GAUSS * z * z)
            }
        return DoubleArray(valores.size) { i ->
            var suma = 0.0
            var peso = 0.0
            for (j in maxOf(0, i - semiancho)..minOf(valores.lastIndex, i + semiancho)) {
                val w = pesos[j - i + semiancho]
                suma += w * valores[j]
                peso += w
            }
            suma / peso
        }
    }
}
