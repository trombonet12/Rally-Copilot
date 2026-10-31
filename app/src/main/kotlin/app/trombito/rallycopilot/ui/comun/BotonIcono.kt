package app.trombito.rallycopilot.ui.comun

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Lado mínimo de un objetivo táctil. */
val LADO_TACTIL: Dp = 48.dp

private const val OPACIDAD_SOBRE_MAPA = 0.88f
private const val OPACIDAD_DESHABILITADO = 0.38f
private val GROSOR_SELECCION = 2.dp

/** Cómo se presenta un [BotonIcono]: resaltado, activo y qué lee TalkBack además de su descripción. */
data class EstadoBotonIcono(
    val seleccionado: Boolean = false,
    val habilitado: Boolean = true,
    /** Estado hablado («Activado», «Seleccionado»…); null si no hace falta. */
    val descripcionEstado: String? = null,
    val rol: Role = Role.Button,
)

/**
 * Botón redondo de icono, sin texto: objetivo táctil de 48 dp, resaltado con el latón de la marca cuando está
 * seleccionado. [descripcion] y el estado son lo que lee TalkBack; el [contenido] pinta el icono.
 */
@Composable
fun BotonIcono(
    descripcion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    estado: EstadoBotonIcono = EstadoBotonIcono(),
    contenido: @Composable () -> Unit,
) {
    val colores = MaterialTheme.colorScheme
    val color = if (estado.seleccionado) colores.primary else colores.onSurface
    val borde = BorderStroke(GROSOR_SELECCION, colores.primary)
    Surface(
        shape = CircleShape,
        color =
            if (estado.seleccionado) {
                colores.primaryContainer
            } else {
                colores.surfaceContainerHigh.copy(OPACIDAD_SOBRE_MAPA)
            },
        modifier =
            modifier
                .defaultMinSize(LADO_TACTIL, LADO_TACTIL)
                .alpha(if (estado.habilitado) 1f else OPACIDAD_DESHABILITADO)
                .then(if (estado.seleccionado) Modifier.border(borde, CircleShape) else Modifier)
                .clickable(enabled = estado.habilitado, role = estado.rol, onClick = onClick)
                .semantics {
                    contentDescription = descripcion
                    selected = estado.seleccionado
                    estado.descripcionEstado?.let { stateDescription = it }
                },
    ) {
        Box(Modifier.defaultMinSize(LADO_TACTIL, LADO_TACTIL), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides color) { contenido() }
        }
    }
}
