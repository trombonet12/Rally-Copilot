package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally
import app.trombito.rallycopilot.ui.tramos.formatearTiempo

const val ETIQUETA_FILA_PREFIJO = "tramo-fila-"
const val ETIQUETA_FAVORITO_PREFIJO = "tramo-favorito-"
const val ETIQUETA_RENOMBRAR_PREFIJO = "tramo-renombrar-"

private val LADO_TACTIL = 48.dp
private val ANCHO_FRANJA = 4.dp

/** Fila que se borra deslizando hacia la izquierda; también por la acción de accesibilidad «Borrar». */
@Composable
fun FilaTramoDeslizable(
    tramo: ResumenTramo,
    acciones: AccionesMisTramos,
    modifier: Modifier = Modifier,
) {
    val estado =
        rememberSwipeToDismissBoxState(
            confirmValueChange = {
                val borrar = it == SwipeToDismissBoxValue.EndToStart
                if (borrar) acciones.onBorrar(tramo)
                borrar
            },
        )
    SwipeToDismissBox(
        state = estado,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = { FondoBorrar() },
    ) {
        FilaTramo(tramo, acciones)
    }
}

@Composable
private fun FondoBorrar() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
                .padding(horizontal = Espacio.l),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(Iconos.Borrar, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun FilaTramo(
    tramo: ResumenTramo,
    acciones: AccionesMisTramos,
) {
    val borrarTexto = stringResource(R.string.mistramos_borrar)
    val renombrarTexto = stringResource(R.string.mistramos_renombrar)
    Surface(
        onClick = { acciones.onAbrir(tramo) },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ETIQUETA_FILA_PREFIJO + tramo.id)
                .semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(borrarTexto) {
                                acciones.onBorrar(tramo)
                                true
                            },
                            CustomAccessibilityAction(renombrarTexto) {
                                acciones.onPedirRenombrar(tramo)
                                true
                            },
                        )
                },
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(ANCHO_FRANJA).fillMaxHeight().background(LocalColoresRally.current.franja))
            Row(Modifier.padding(start = Espacio.m), verticalAlignment = Alignment.CenterVertically) {
                DatosTramo(tramo, Modifier.weight(1f).padding(vertical = Espacio.m))
                BotonesFila(tramo, acciones)
            }
        }
    }
}

@Composable
private fun DatosTramo(
    tramo: ResumenTramo,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            Text(
                text = tramo.nombre,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = textoMedidas(tramo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MejorTiempo(tramo.mejorTotalMs, Modifier.padding(horizontal = Espacio.s))
    }
}

@Composable
private fun textoMedidas(tramo: ResumenTramo): String =
    stringResource(
        R.string.mistramos_medidas,
        formatearDistanciaTramo(tramo.longitudM),
        pluralStringResource(R.plurals.mistramos_pasadas, tramo.pasadas, tramo.pasadas),
    )

@Composable
private fun MejorTiempo(
    mejorMs: Long?,
    modifier: Modifier = Modifier,
) {
    val tiempo = mejorMs?.let { formatearTiempo(it) } ?: stringResource(R.string.mistramos_sin_tiempo)
    val color = if (mejorMs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(
            text = stringResource(R.string.mistramos_mejor).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = tiempo, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun BotonesFila(
    tramo: ResumenTramo,
    acciones: AccionesMisTramos,
) {
    Column {
        BotonFavorito(
            favorito = tramo.favorito,
            onClick = { acciones.onFavorito(tramo) },
            modifier = Modifier.testTag(ETIQUETA_FAVORITO_PREFIJO + tramo.id),
        )
        IconButton(
            onClick = { acciones.onPedirRenombrar(tramo) },
            modifier = Modifier.size(LADO_TACTIL).testTag(ETIQUETA_RENOMBRAR_PREFIJO + tramo.id),
        ) {
            Icon(
                imageVector = Iconos.Editar,
                contentDescription = stringResource(R.string.mistramos_renombrar),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Estrella de favorito: llena en latón si lo es, con borde si no. */
@Composable
fun BotonFavorito(
    favorito: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color: Color =
        if (favorito) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(onClick = onClick, modifier = modifier.size(LADO_TACTIL)) {
        Icon(
            imageVector = if (favorito) Iconos.Estrella else Iconos.EstrellaVacia,
            contentDescription =
                stringResource(
                    if (favorito) R.string.mistramos_quitar_favorito else R.string.mistramos_marcar_favorito,
                ),
            tint = color,
        )
    }
}
