package app.trombito.rallycopilot.ui.comun

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.Radios

/** Prefijo de la etiqueta de prueba de cada segmento: `selector-segmento-<indice>`. */
const val ETIQUETA_SEGMENTO = "selector-segmento-"

/** Una opción de [SelectorSegmentadoWrc]: el [valor] que devuelve, su [texto] y un [icono] opcional. */
data class OpcionSegmento<T>(
    val valor: T,
    val texto: String,
    val icono: ImageVector? = null,
)

/**
 * Selector segmentado del sistema WRC, con el mismo aspecto que [ChipIconoWrc]: píldora de `surfaceContainerHigh` y,
 * en el segmento elegido, fondo `primaryContainer` con borde ámbar de 1,5 dp (nada de azul de Material ni relleno
 * rojo). Cada segmento mide al menos 48 dp, reparte el ancho a partes iguales y se anuncia como botón de opción
 * seleccionado o no. Sustituye a los `FilterChip`, botones segmentados y pestañas de Material.
 */
@Composable
fun <T> SelectorSegmentadoWrc(
    opciones: List<OpcionSegmento<T>>,
    seleccionada: T,
    onSeleccion: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = Radios.formaPildora,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.selectableGroup().padding(Espacio.xs),
            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            opciones.forEachIndexed { indice, opcion ->
                Segmento(
                    opcion = opcion,
                    seleccionado = opcion.valor == seleccionada,
                    onClick = { onSeleccion(opcion.valor) },
                    modifier = Modifier.weight(1f).testTag("$ETIQUETA_SEGMENTO$indice"),
                )
            }
        }
    }
}

@Composable
private fun <T> Segmento(
    opcion: OpcionSegmento<T>,
    seleccionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colores = MaterialTheme.colorScheme
    val fondo = if (seleccionado) colores.primaryContainer else colores.surfaceContainerHigh
    val colorContenido = if (seleccionado) colores.onPrimaryContainer else colores.onSurface
    val borde = if (seleccionado) BorderStroke(Medidas.bordeActivo, colores.primary) else null
    Surface(
        shape = Radios.formaPildora,
        color = fondo,
        contentColor = colorContenido,
        border = borde,
        modifier = modifier.selectable(selected = seleccionado, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = Medidas.zonaTactil)
                    .padding(horizontal = Espacio.m, vertical = Espacio.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.s, Alignment.CenterHorizontally),
        ) {
            if (opcion.icono != null) {
                Icon(opcion.icono, contentDescription = null, modifier = Modifier.size(Medidas.iconoLista))
            }
            Text(
                text = opcion.texto,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
