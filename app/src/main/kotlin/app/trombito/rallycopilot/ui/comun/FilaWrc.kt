package app.trombito.rallycopilot.ui.comun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Radios

/** Alto mínimo de una [FilaWrc]: 56 dp, por encima de la zona táctil de 48 dp. */
val ALTO_MINIMO_FILA_WRC = 56.dp

private val TituloFila = Cuerpo.copy(fontWeight = FontWeight.SemiBold)

/**
 * Fila de lista del sistema WRC: [icono] opcional en ámbar, [titulo], [detalle] opcional (hasta dos líneas) y, a la
 * derecha, un [valor] y/o la flecha de [navega]. Mide al menos 56 dp. Con [onClick] es pulsable (rol botón, un solo
 * nodo accesible); sin él es informativa. [habilitada] la apaga sin quitarla.
 */
@Suppress("LongParameterList") // API de componente: cada parte de la fila es un parámetro con valor por defecto
@Composable
fun FilaWrc(
    titulo: String,
    modifier: Modifier = Modifier,
    detalle: String? = null,
    icono: ImageVector? = null,
    valor: String? = null,
    navega: Boolean = false,
    habilitada: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val contenido: @Composable () -> Unit = {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ALTO_MINIMO_FILA_WRC)
                    .padding(horizontal = Espacio.l, vertical = Espacio.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            if (icono != null) {
                Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(titulo, style = TituloFila, color = MaterialTheme.colorScheme.onSurface)
                if (detalle != null) {
                    Text(detalle, style = Ayuda, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            if (valor != null) {
                Text(valor, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (navega) {
                Icon(Iconos.Siguiente, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            enabled = habilitada,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = Radios.formaTarjeta,
            modifier = modifier.fillMaxWidth(),
            content = contenido,
        )
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = Radios.formaTarjeta,
            modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            content = contenido,
        )
    }
}
