package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Radios
import app.trombito.rallycopilot.ui.theme.Rotulo
import app.trombito.rallycopilot.ui.tramos.formatearTiempo

const val ETIQUETA_FILA_PREFIJO = "tramo-fila-"
const val ETIQUETA_FAVORITO_PREFIJO = "tramo-favorito-"

private val LADO_TACTIL = 48.dp
private val ANCHO_BARRA_FAVORITO = 3.dp
private val LADO_MINIATURA = 56.dp

/** Fila que se borra deslizando hacia la izquierda; también por la acción de accesibilidad «Borrar». */
@Composable
fun FilaTramoDeslizable(
    tramo: ResumenTramo,
    acciones: AccionesMisTramos,
    modifier: Modifier = Modifier,
    traza: List<PuntoGeo>? = null,
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
        FilaTramo(tramo, traza, acciones)
    }
}

@Composable
private fun FondoBorrar() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.errorContainer, Radios.formaTarjeta)
                .padding(horizontal = Espacio.l),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(Iconos.Borrar, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun FilaTramo(
    tramo: ResumenTramo,
    traza: List<PuntoGeo>?,
    acciones: AccionesMisTramos,
) {
    val borrarTexto = stringResource(R.string.mistramos_borrar)
    val renombrarTexto = stringResource(R.string.mistramos_renombrar)
    val colorBarra = MaterialTheme.colorScheme.primary
    val barraPx = with(LocalDensity.current) { ANCHO_BARRA_FAVORITO.toPx() }
    TarjetaWrc(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(Radios.formaTarjeta)
                .drawWithContent {
                    drawContent()
                    if (tramo.favorito) drawRect(colorBarra, Offset.Zero, Size(barraPx, size.height))
                }.testTag(ETIQUETA_FILA_PREFIJO + tramo.id)
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
        relleno = Espacio.m,
        onClick = { acciones.onAbrir(tramo) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
            Miniatura(traza)
            DatosTramo(tramo, Modifier.weight(1f))
            MejorTiempo(tramo.mejorTotalMs)
            BotonFavorito(
                favorito = tramo.favorito,
                onClick = { acciones.onFavorito(tramo) },
                modifier = Modifier.testTag(ETIQUETA_FAVORITO_PREFIJO + tramo.id),
            )
        }
    }
}

/** Silueta del tramo; mientras se lee (o si no se puede), un hueco con la bandera. */
@Composable
private fun Miniatura(traza: List<PuntoGeo>?) {
    if (traza != null) {
        MiniaturaTraza(traza, LADO_MINIATURA)
    } else {
        Box(
            Modifier
                .size(LADO_MINIATURA)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, Radios.formaTarjeta),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Iconos.Bandera, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun DatosTramo(
    tramo: ResumenTramo,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        Text(
            text = tramo.nombre,
            style = Cuerpo.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = textoMedidas(tramo),
            style = Ayuda,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Km y pasadas: el nombre automático ya dice por dónde va, así que el origen y el destino no se derivan de él. */
@Composable
private fun textoMedidas(tramo: ResumenTramo): String =
    stringResource(
        R.string.mistramos_medidas,
        formatearDistanciaTramo(tramo.longitudM),
        pluralStringResource(R.plurals.mistramos_pasadas, tramo.pasadas, tramo.pasadas),
    )

/** Mejor tiempo en 20 sp, ámbar y cursiva; sin pasadas, un guion apagado. */
@Composable
private fun MejorTiempo(
    mejorMs: Long?,
    modifier: Modifier = Modifier,
) {
    val tiempo = mejorMs?.let { formatearTiempo(it) } ?: stringResource(R.string.mistramos_sin_tiempo)
    val color = if (mejorMs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = tiempo,
        style = Rotulo.copy(fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
        color = color,
        maxLines = 1,
        modifier = modifier,
    )
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
