package app.trombito.rallycopilot.ui.tramos

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Cada cuánto se refresca el cronómetro grande (décimas legibles sin saltos). */
internal const val PERIODO_HUD_MS = 50L

/** Cada cuánto se refresca el cronómetro pequeño (solo segundos). */
internal const val PERIODO_MINI_MS = 250L

/**
 * El tiempo que marca el cronómetro ahora: el de la última fijación ([CronoEnCurso.transcurridoMs]) más lo que ha
 * pasado desde entonces según [reloj] (ms epoch), para que siga contando entre fijaciones. Con el resultado ya
 * publicado se queda quieto en el total. El reloj se consulta cada [periodoMs].
 */
@Composable
internal fun rememberTranscurridoMs(
    crono: CronoEnCurso,
    reloj: () -> Long,
    periodoMs: Long,
): Long {
    val relojActual by rememberUpdatedState(reloj)
    val corriendo = crono.resultado == null && crono.enMarcha
    var ahora by remember { mutableLongStateOf(reloj()) }
    LaunchedEffect(corriendo, periodoMs) {
        while (corriendo) {
            ahora = relojActual()
            esperarEnPrincipal(periodoMs)
        }
    }
    return if (corriendo) {
        crono.transcurridoMs +
            (ahora - crono.actualizadoEnMs).coerceAtLeast(
                0L,
            )
    } else {
        crono.transcurridoMs
    }
}

private const val ALFA_LISTO_MIN = 0.45f
private const val PERIODO_PULSO_LISTO_MS = 900

/** Pulso suave de alfa del texto «LISTO» (entre [ALFA_LISTO_MIN] y 1), infinito y sin tiempo corriendo. */
@Composable
internal fun rememberAlfaListo(): Float {
    val transicion = rememberInfiniteTransition(label = "listo")
    val alfa by transicion.animateFloat(
        initialValue = 1f,
        targetValue = ALFA_LISTO_MIN,
        animationSpec = infiniteRepeatable(tween(PERIODO_PULSO_LISTO_MS), RepeatMode.Reverse),
        label = "alfa-listo",
    )
    return alfa
}

/**
 * Espera [ms] en el hilo principal «de verdad» (no en el despachador de Compose, que usaría el reloj real): así el
 * reloj de los tests gobierna las esperas del HUD.
 */
internal suspend fun esperarEnPrincipal(ms: Long) = withContext(Dispatchers.Main) { delay(ms) }
