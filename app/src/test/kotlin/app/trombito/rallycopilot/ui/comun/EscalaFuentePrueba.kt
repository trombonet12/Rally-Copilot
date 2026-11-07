package app.trombito.rallycopilot.ui.comun

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Pinta [contenido] como si el usuario tuviera la fuente del sistema a [escala] (1,3 = grande, 2,0 = la máxima). */
@Composable
fun ConEscalaDeFuente(
    escala: Float,
    contenido: @Composable () -> Unit,
) {
    val densidad = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(densidad.density, escala), content = contenido)
}
