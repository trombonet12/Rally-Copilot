package app.trombito.rallycopilot.motor.geo

import kotlin.math.roundToLong

/** Regla de redondeo de distancias que comparten la notificación y la interfaz; la localización la pone cada capa. */
object FormatoDistancia {
    const val UN_KM = 1000.0

    /** Por debajo de esta distancia (m) se usa el paso fino. */
    const val LIMITE_PASO_FINO_M = 200.0

    /**
     * [metros] (los negativos cuentan como 0) redondeados al múltiplo de [pasoFinoM] por debajo de 200 m y al de
     * [pasoGruesoM] desde ahí. Con un solo paso, pasar el mismo valor en los dos.
     */
    fun redondeada(
        metros: Double,
        pasoFinoM: Int,
        pasoGruesoM: Int,
    ): Double {
        val m = metros.coerceAtLeast(0.0)
        val paso = (if (m < LIMITE_PASO_FINO_M) pasoFinoM else pasoGruesoM).toDouble()
        return (m / paso).roundToLong() * paso
    }
}
