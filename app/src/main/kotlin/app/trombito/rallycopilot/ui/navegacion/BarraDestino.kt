package app.trombito.rallycopilot.ui.navegacion

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.LADO_TACTIL
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_BUSCADOR = "nav-buscador"
const val ETIQUETA_LIMPIAR = "nav-limpiar"
const val ETIQUETA_RESULTADOS = "nav-resultados"
const val ETIQUETA_RESULTADO = "nav-resultado-"
const val ETIQUETA_ENLACE_CORTO = "nav-enlace-corto"
const val ETIQUETA_SIN_RESULTADOS = "nav-sin-resultados"

private const val OPACIDAD_SOBRE_MAPA = 0.92f
private val RADIO_BARRA = 28.dp
private val ALTO_FILA = 56.dp
private val ALTO_LISTA_VERTICAL = 300.dp
private val ALTO_LISTA_HORIZONTAL = 144.dp

/** Campo «¿A dónde?» de esquinas muy redondeadas, con lupa y, si hay texto, un botón para borrarlo. */
@Composable
fun BarraDestino(
    texto: String,
    onTexto: (String) -> Unit,
    onLimpiar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(RADIO_BARRA),
        color = colores.surfaceContainerHigh.copy(OPACIDAD_SOBRE_MAPA),
        modifier = modifier.heightIn(min = LADO_TACTIL),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Iconos.Lupa,
                contentDescription = null,
                tint = colores.onSurfaceVariant,
                modifier = Modifier.padding(start = Espacio.m),
            )
            // Un solo nodo para TalkBack: la pista «¿A dónde?» (solo con el campo vacío) y el texto escrito.
            Box(
                modifier = Modifier.weight(1f).padding(horizontal = Espacio.s).semantics(mergeDescendants = true) {},
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = texto,
                    onValueChange = onTexto,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colores.onSurface),
                    cursorBrush = SolidColor(colores.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_BUSCADOR),
                    decorationBox = { campo ->
                        if (texto.isEmpty()) {
                            Text(
                                text = stringResource(R.string.navegacion_ui_donde),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colores.onSurfaceVariant,
                            )
                        }
                        campo()
                    },
                )
            }
            if (texto.isNotEmpty()) {
                IconButton(onClick = onLimpiar, modifier = Modifier.size(LADO_TACTIL).testTag(ETIQUETA_LIMPIAR)) {
                    Icon(
                        imageVector = Iconos.Cerrar,
                        contentDescription = stringResource(R.string.navegacion_ui_limpiar),
                        tint = colores.onSurface,
                    )
                }
            }
        }
    }
}

/** Lista bajo la barra: resultados del índice, la ubicación de un enlace o el aviso de enlace corto. */
@Composable
fun ListaDestinos(
    busqueda: BusquedaDestino,
    onElegir: (Destino) -> Unit,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
) {
    if (busqueda is BusquedaDestino.Inactiva) return
    val descripcion = stringResource(R.string.navegacion_ui_resultados)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(OPACIDAD_SOBRE_MAPA),
        modifier = modifier.fillMaxWidth().testTag(ETIQUETA_RESULTADOS).semantics { contentDescription = descripcion },
    ) {
        val alto: Dp = if (horizontal) ALTO_LISTA_HORIZONTAL else ALTO_LISTA_VERTICAL
        when (busqueda) {
            is BusquedaDestino.Resultados -> {
                FilasResultados(busqueda.items, onElegir, Modifier.heightIn(max = alto))
            }

            is BusquedaDestino.Ubicacion -> {
                val d = busqueda.destino
                val pegada = stringResource(R.string.navegacion_ui_ubicacion_pegada)
                val titulo = if (busqueda.sinNombre) pegada else d.nombre
                val detalle = if (busqueda.sinNombre) d.nombre else null
                FilaDestino(titulo, detalle, { onElegir(d) }, Modifier.testTag(ETIQUETA_RESULTADO + "0"))
            }

            BusquedaDestino.NecesitaRed -> {
                MensajeLista(R.string.navegacion_ui_enlace_corto, Modifier.testTag(ETIQUETA_ENLACE_CORTO))
            }

            BusquedaDestino.Inactiva -> {}
        }
    }
}

@Composable
private fun FilasResultados(
    items: List<ResultadoBusqueda>,
    onElegir: (Destino) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        MensajeLista(R.string.navegacion_ui_sin_resultados, Modifier.testTag(ETIQUETA_SIN_RESULTADOS))
        return
    }
    LazyColumn(modifier) {
        itemsIndexed(items) { indice, item ->
            val detalle = detalleResultado(item)
            FilaDestino(
                titulo = item.nombre,
                detalle = detalle,
                onClick = { onElegir(Destino(item.nombre, item.punto)) },
                modifier = Modifier.testTag(ETIQUETA_RESULTADO + indice),
            )
        }
    }
}

/** «categoría · municipio» (o solo la categoría si no se conoce el municipio). */
@Composable
private fun detalleResultado(item: ResultadoBusqueda): String =
    item.municipio?.let { stringResource(R.string.navegacion_ui_resultado_detalle, item.categoria, it) }
        ?: item.categoria

@Composable
private fun FilaDestino(
    titulo: String,
    detalle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_FILA)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Espacio.m, vertical = Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        Icon(Iconos.Lugar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (detalle != null) {
                Text(
                    text = detalle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MensajeLista(
    @StringRes texto: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(texto),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(Espacio.l),
    )
}
