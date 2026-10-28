package app.trombito.rallycopilot.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

private val RallyShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )

/** Tema de la app. Oscuro por defecto: decide quien llama (Ajustes en H10), no el sistema. */
@Composable
fun RallyCopilotTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val typography = remember { rallyTypography() }
    CompositionLocalProvider(LocalColoresRally provides if (darkTheme) RallyOscuro else RallyClaro) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = typography,
            shapes = RallyShapes,
            content = content,
        )
    }
}
