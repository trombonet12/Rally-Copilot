package app.trombito.rallycopilot.ui.ajustes

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_SECCION_PREFIJO = "ajustes-seccion-"
const val ETIQUETA_SECCION_PREVISTA_PREFIJO = "ajustes-prevista-de-"

private val LADO_ICONO_SECCION = 28.dp

/**
 * Una sección de Ajustes: cabecera con icono, título y una línea que dice para qué sirve; la [vistaPrevia] en vivo
 * arriba (cambia al tocar una opción) y las opciones debajo. [clave] identifica la sección en las pruebas.
 */
@Composable
@Suppress("LongParameterList") // cabecera (clave, icono, títulos), vista previa y contenido de la sección
internal fun TarjetaSeccion(
    clave: String,
    icono: ImageVector,
    @StringRes titulo: Int,
    @StringRes detalle: Int,
    modifier: Modifier = Modifier,
    vistaPrevia: (@Composable () -> Unit)? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().testTag(ETIQUETA_SECCION_PREFIJO + clave),
    ) {
        Column(Modifier.padding(Espacio.l), verticalArrangement = Arrangement.spacedBy(Espacio.m)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espacio.m),
            ) {
                Icon(
                    icono,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(LADO_ICONO_SECCION),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(titulo),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(detalle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (vistaPrevia != null) CajaVistaPrevia(clave, vistaPrevia)
            contenido()
        }
    }
}

/** El recuadro de la vista previa: se distingue de las opciones y se queda arriba de la sección. */
@Composable
private fun CajaVistaPrevia(
    clave: String,
    contenido: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_SECCION_PREVISTA_PREFIJO + clave),
    ) {
        Column(Modifier.padding(Espacio.m)) { contenido() }
    }
}

/** Un ajuste dentro de una sección: su nombre, una línea que lo explica y los controles. */
@Composable
internal fun Opcion(
    @StringRes titulo: Int,
    @StringRes detalle: Int?,
    contenido: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Column {
            Text(
                text = stringResource(titulo),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            if (detalle != null) {
                Text(
                    text = stringResource(detalle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        contenido()
    }
}
