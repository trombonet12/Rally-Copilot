package app.trombito.rallycopilot.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Escala de fuente máxima de los elementos del HUD de conducción (píldora, banner de maniobra y crono). */
const val ESCALA_FUENTE_MAXIMA_HUD = 1.3f

/** La escala de fuente que se aplica de verdad al HUD: la del sistema, sin pasar de [ESCALA_FUENTE_MAXIMA_HUD]. */
internal fun escalaFuenteHud(escalaSistema: Float): Float = escalaSistema.coerceAtMost(ESCALA_FUENTE_MAXIMA_HUD)

/**
 * Limita el escalado de fuente del [contenido] a [ESCALA_FUENTE_MAXIMA_HUD]: las cifras del HUD van sobre tarjetas de
 * alto y ancho contados, y con la fuente al 200 % del sistema se saldrían. El resto de la app escala libremente. No
 * cambia la densidad: los dp miden lo mismo.
 */
@Composable
fun LimitarEscalaHud(contenido: @Composable () -> Unit) {
    val densidad = LocalDensity.current
    val limitada = escalaFuenteHud(densidad.fontScale)
    if (limitada == densidad.fontScale) {
        contenido()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(densidad.density, limitada)) { contenido() }
    }
}
