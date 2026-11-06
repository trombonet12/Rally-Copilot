package app.trombito.rallycopilot.ui.comun

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.Radios

private val ALTO_CHIP_INFORMATIVO = Medidas.zonaTactil - Espacio.s

/**
 * Chip de píldora (radio 24) con icono de 24 dp y texto. Informativo si [onClick] es nulo (40 dp de alto); con [onClick]
 * mide al menos 48 dp (zona táctil) y se marca como seleccionable con [seleccionado] (borde ámbar, redundante con el
 * icono). Sin relleno rojo: el estado se ve en el borde y el fondo `primaryContainer`.
 */
@Composable
fun ChipIconoWrc(
    icono: ImageVector,
    texto: String,
    modifier: Modifier = Modifier,
    seleccionado: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colores = MaterialTheme.colorScheme
    val fondo = if (seleccionado) colores.primaryContainer else colores.surfaceContainerHigh
    val colorContenido = if (seleccionado) colores.onPrimaryContainer else colores.onSurface
    val borde = if (seleccionado) BorderStroke(Medidas.bordeActivo, colores.primary) else null
    val contenido: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.s),
            modifier =
                Modifier
                    .defaultMinSize(minHeight = if (onClick != null) Medidas.zonaTactil else ALTO_CHIP_INFORMATIVO)
                    .padding(horizontal = Espacio.m, vertical = Espacio.xs),
        ) {
            Icon(icono, contentDescription = null, modifier = Modifier.size(Medidas.iconoLista))
            Text(text = texto, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = Radios.formaPildora,
            color = fondo,
            contentColor = colorContenido,
            border = borde,
            modifier =
                modifier
                    .semantics {
                        role = Role.Button
                        selected = seleccionado
                    },
            content = contenido,
        )
    } else {
        Surface(
            shape = Radios.formaPildora,
            color = fondo,
            contentColor = colorContenido,
            border = borde,
            modifier = modifier.semantics(mergeDescendants = true) {},
            content = contenido,
        )
    }
}
