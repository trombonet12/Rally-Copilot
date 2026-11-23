package app.trombito.rallycopilot.ui.tutorial

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import app.trombito.rallycopilot.ui.comun.MascotaPose
import app.trombito.rallycopilot.ui.comun.PoseMascota
import app.trombito.rallycopilot.ui.comun.rememberReducirMovimiento
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Duración del gesto de entrada de Trombito: corto, para no hacer esperar. */
internal const val DURACION_SALUDO_MS = 380
private const val ESCALA_INICIAL = 0.6f
private const val GIRO_INICIAL_GRADOS = -10f
private const val ALFA_INICIAL = 0f

/**
 * Trombito saludando en la bienvenida. Al aparecer entra con un gesto corto de escala y giro ([DURACION_SALUDO_MS]) una
 * sola vez (el estado sobrevive a que el pager descarte y recomponga la página); con las animaciones del sistema
 * apagadas queda quieto desde el principio. Es decorativo.
 */
@Composable
internal fun MascotaSaluda(
    lado: Dp,
    modifier: Modifier = Modifier,
) {
    val reducir = rememberReducirMovimiento()
    var yaSaludo by rememberSaveable { mutableStateOf(false) }
    val quieto = reducir || yaSaludo
    val escala = remember { Animatable(if (quieto) 1f else ESCALA_INICIAL) }
    val giro = remember { Animatable(if (quieto) 0f else GIRO_INICIAL_GRADOS) }
    val alfa = remember { Animatable(if (quieto) 1f else ALFA_INICIAL) }
    LaunchedEffect(Unit) {
        if (quieto) return@LaunchedEffect
        yaSaludo = true
        val curva = tween<Float>(DURACION_SALUDO_MS, easing = FastOutSlowInEasing)
        coroutineScope {
            launch { escala.animateTo(1f, curva) }
            launch { giro.animateTo(0f, curva) }
            launch { alfa.animateTo(1f, curva) }
        }
    }
    MascotaPose(
        pose = PoseMascota.SALUDANDO,
        lado = lado,
        modifier =
            modifier
                .testTag(ETIQUETA_BIENVENIDA_MASCOTA)
                .alpha(alfa.value)
                .graphicsLayer {
                    scaleX = escala.value
                    scaleY = escala.value
                    rotationZ = giro.value
                },
    )
}
