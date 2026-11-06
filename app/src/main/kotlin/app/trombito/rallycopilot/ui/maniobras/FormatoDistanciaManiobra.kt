package app.trombito.rallycopilot.ui.maniobras

import app.trombito.rallycopilot.ui.comun.FormatoEs
import kotlin.math.roundToInt

private const val UN_KM = 1000.0
private const val LIMITE_PASO_FINO_M = 200.0
private const val PASO_FINO_M = 10
private const val PASO_GRUESO_M = 50

/**
 * Distancia para el banner: por debajo de 200 m se redondea a 10 m, hasta 1 km a 50 m («350 m») y desde 1 km se da
 * en km con una decimal y coma («1,2 km»).
 */
fun formatearDistanciaManiobra(metros: Double): String {
    val m = metros.coerceAtLeast(0.0)
    if (m < UN_KM) {
        val paso = if (m < LIMITE_PASO_FINO_M) PASO_FINO_M else PASO_GRUESO_M
        val redondeado = (m / paso).roundToInt() * paso
        if (redondeado < UN_KM) return FormatoEs.distancia(redondeado.toDouble())
    }
    return FormatoEs.kilometrosDesdeMetros(m)
}
