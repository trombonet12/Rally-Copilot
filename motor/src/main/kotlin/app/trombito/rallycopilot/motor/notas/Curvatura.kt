package app.trombito.rallycopilot.motor.notas

import kotlin.math.hypot

/** Curvatura de una traza plana: inversa del circunradio de tres muestras, con signo. */
object Curvatura {
    const val SEMIVENTANA_MUESTRAS = 3

    /**
     * Curvatura en 1/m de cada muestra i como inversa del circunradio de las muestras i−[semiventana], i e
     * i+[semiventana] (±15 m con paso de 5 m). Positiva si gira a la izquierda, negativa a la derecha, 0 si los
     * tres puntos son colineales y 0 en los extremos, donde no hay ventana completa.
     */
    fun calcular(
        traza: TrazaPlana,
        semiventana: Int = SEMIVENTANA_MUESTRAS,
    ): DoubleArray {
        require(semiventana >= 1) { "Semiventana inválida: $semiventana" }
        val k = DoubleArray(traza.size)
        for (i in semiventana until traza.size - semiventana) {
            k[i] = inversaCircunradio(traza, i - semiventana, i, i + semiventana)
        }
        return k
    }

    private fun inversaCircunradio(
        t: TrazaPlana,
        a: Int,
        b: Int,
        c: Int,
    ): Double {
        val abx = t.x[b] - t.x[a]
        val aby = t.y[b] - t.y[a]
        val acx = t.x[c] - t.x[a]
        val acy = t.y[c] - t.y[a]
        val cruz = abx * acy - aby * acx
        val lados = hypot(abx, aby) * hypot(t.x[c] - t.x[b], t.y[c] - t.y[b]) * hypot(acx, acy)
        return if (lados == 0.0) 0.0 else 2.0 * cruz / lados
    }
}
