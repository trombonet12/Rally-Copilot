package app.trombito.rallycopilot.ui.comun

import androidx.compose.runtime.withFrameMillis

/**
 * Espera [ms] contando fotogramas (no con `delay`): así el reloj de los fotogramas es el que se adelanta en las pruebas
 * y la espera no cuesta nada mientras no se pinta nada.
 */
suspend fun esperarPorFotogramas(ms: Long) {
    val inicio = withFrameMillis { it }
    var transcurrido = 0L
    while (transcurrido < ms) transcurrido = withFrameMillis { it } - inicio
}
