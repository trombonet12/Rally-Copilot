package app.trombito.rallycopilot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.domain.ajustes.Tema

private val RallyShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )

/** Si el tema aplicado es oscuro; lo usan quienes dependen de él fuera de Material (estilo del mapa). */
val LocalTemaOscuro = staticCompositionLocalOf { true }

/** Tema de la app. Oscuro por defecto: decide quien llama (el ajuste de tema), no el sistema. */
@Composable
fun RallyCopilotTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val typography = remember { rallyTypography() }
    CompositionLocalProvider(
        LocalColoresRally provides if (darkTheme) RallyOscuro else RallyClaro,
        LocalTemaOscuro provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = typography,
            shapes = RallyShapes,
            content = content,
        )
    }
}

/** Si este tema se pinta en oscuro ahora; con [Tema.SISTEMA] sigue el modo del móvil y recompone al cambiarlo. */
@Composable
fun Tema.oscuroAhora(): Boolean = esOscuro(isSystemInDarkTheme())
