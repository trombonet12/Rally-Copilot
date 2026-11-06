package app.trombito.rallycopilot.ui.navegacion

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
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
import app.trombito.rallycopilot.ui.comun.EstadoVacioWrc
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.LADO_TACTIL
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Radios

const val ETIQUETA_BUSCADOR = "nav-buscador"
const val ETIQUETA_LIMPIAR = "nav-limpiar"
const val ETIQUETA_RESULTADOS = "nav-resultados"
const val ETIQUETA_RESULTADO = "nav-resultado-"
const val ETIQUETA_ENLACE_CORTO = "nav-enlace-corto"
const val ETIQUETA_SIN_RESULTADOS = "nav-sin-resultados"
const val ETIQUETA_RESULTADO_PARADA = "nav-resultado-parada-"
const val ETIQUETA_RESULTADO_IR = "nav-resultado-ir-"

private val ALTO_FILA = 56.dp
private val ALTO_LISTA_VERTICAL = 300.dp
private val ALTO_LISTA_HORIZONTAL = 144.dp

/**
 * Campo «¿A dónde?» en píldora opaca (`surfaceContainerHigh`), con lupa y una sola ✕ al final: con [onCerrar] cierra
 * la búsqueda (siempre visible, con el campo vacío o no); sin él borra el texto (solo si lo hay). [onFoco] avisa de si
 * el campo tiene el foco (y de que lo pierde al salir de la composición).
 */
@Composable
@Suppress("LongParameterList") // campo, foco y las dos acciones de la ✕ final
fun BarraDestino(
    texto: String,
    onTexto: (String) -> Unit,
    onLimpiar: () -> Unit,
    modifier: Modifier = Modifier,
    onFoco: (Boolean) -> Unit = {},
    onCerrar: (() -> Unit)? = null,
    pedirFoco: Boolean = false,
    onFocoPedido: () -> Unit = {},
) {
    val colores = MaterialTheme.colorScheme
    DisposableEffect(Unit) { onDispose { onFoco(false) } }
    val foco = remember { FocusRequester() }
    LaunchedEffect(pedirFoco) {
        if (pedirFoco) {
            foco.requestFocus()
            onFocoPedido()
        }
    }
    val cerrarTeclado = cerrarTeclado()
    Surface(
        shape = Radios.formaPildora,
        color = colores.surfaceContainerHigh,
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
                    keyboardActions = KeyboardActions(onSearch = { cerrarTeclado() }),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(foco)
                            .onFocusChanged { onFoco(it.isFocused) }
                            .testTag(ETIQUETA_BUSCADOR),
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
            AccionFinal(texto, onLimpiar, onCerrar)
        }
    }
}

/** La ✕ final de la barra: cierra la búsqueda con [onCerrar]; sin él, borra el texto (solo si lo hay). */
@Composable
private fun AccionFinal(
    texto: String,
    onLimpiar: () -> Unit,
    onCerrar: (() -> Unit)?,
) {
    if (onCerrar == null && texto.isEmpty()) return
    val cierra = onCerrar != null
    val etiqueta = if (cierra) ETIQUETA_BUSQUEDA_GUIANDO_CERRAR else ETIQUETA_LIMPIAR
    val descripcion = if (cierra) R.string.navegacion_ui_buscar_guiando_cerrar else R.string.navegacion_ui_limpiar
    IconButton(onClick = onCerrar ?: onLimpiar, modifier = Modifier.size(LADO_TACTIL).testTag(etiqueta)) {
        Icon(
            imageVector = Iconos.Cerrar,
            contentDescription = stringResource(descripcion),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Lista bajo la barra: resultados del índice, la ubicación de un enlace o el aviso de enlace corto. Con una ruta en
 * vista previa o guiando ([anadirParada] no es null) cada resultado lleva «+ Parada»; todos llevan «Ir» (cambia el
 * destino y, guiando, conserva las paradas; tocar la fila hace lo mismo) y la distancia a ti.
 */
@Composable
fun ListaDestinos(
    busqueda: BusquedaDestino,
    onElegir: (Destino) -> Unit,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
    anadirParada: ((Destino) -> Unit)? = null,
) {
    if (busqueda is BusquedaDestino.Inactiva) return
    val cerrarTeclado = cerrarTeclado()
    val elegir: (Destino) -> Unit = {
        cerrarTeclado()
        onElegir(it)
    }
    val descripcion = stringResource(R.string.navegacion_ui_resultados)
    val anadir: ((Destino) -> Unit)? =
        anadirParada?.let { alAnadir ->
            { destino ->
                cerrarTeclado()
                alAnadir(destino)
            }
        }
    Surface(
        shape = Radios.formaPanel,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().testTag(ETIQUETA_RESULTADOS).semantics { contentDescription = descripcion },
    ) {
        val alto: Dp = if (horizontal) ALTO_LISTA_HORIZONTAL else ALTO_LISTA_VERTICAL
        when (busqueda) {
            is BusquedaDestino.Resultados -> {
                FilasResultados(busqueda, AccionesFila(elegir, anadir), Modifier.heightIn(max = alto))
            }

            is BusquedaDestino.Ubicacion -> {
                val d = busqueda.destino
                val pegada = stringResource(R.string.navegacion_ui_ubicacion_pegada)
                val titulo = if (busqueda.sinNombre) pegada else d.nombre
                val detalle = if (busqueda.sinNombre) d.nombre else null
                FilaDestino(
                    titulo = titulo,
                    detalle = detalle,
                    onClick = { elegir(d) },
                    modifier = Modifier.testTag(ETIQUETA_RESULTADO + "0"),
                    alAnadirParada = anadir?.let { alAnadir -> { alAnadir(d) } },
                    etiquetaParada = ETIQUETA_RESULTADO_PARADA + "0",
                    alIr = { elegir(d) },
                    etiquetaIr = ETIQUETA_RESULTADO_IR + "0",
                )
            }

            BusquedaDestino.Resolviendo -> {
                MensajeLista(R.string.navegacion_ui_enlace_resolviendo, Modifier.testTag(ETIQUETA_ENLACE_CORTO))
            }

            BusquedaDestino.SinRed -> {
                MensajeLista(R.string.navegacion_ui_enlace_sin_red, Modifier.testTag(ETIQUETA_ENLACE_CORTO))
            }

            BusquedaDestino.EnlaceNoAbierto -> {
                MensajeLista(R.string.navegacion_ui_enlace_no_abierto, Modifier.testTag(ETIQUETA_ENLACE_CORTO))
            }

            BusquedaDestino.Inactiva -> {}
        }
    }
}

/** Lo que se puede hacer con un resultado: elegirlo («Ir») y añadirlo como parada. */
private class AccionesFila(
    val elegir: (Destino) -> Unit,
    val anadir: ((Destino) -> Unit)?,
)

@Composable
private fun FilasResultados(
    busqueda: BusquedaDestino.Resultados,
    acciones: AccionesFila,
    modifier: Modifier = Modifier,
) {
    val items = busqueda.items
    if (items.isEmpty()) {
        EstadoVacioWrc(
            icono = Iconos.Lupa,
            titulo = stringResource(R.string.navegacion_ui_sin_resultados),
            texto = stringResource(R.string.navegacion_ui_sin_resultados_ayuda),
            etiqueta = ETIQUETA_SIN_RESULTADOS,
        )
        return
    }
    LazyColumn(modifier) {
        itemsIndexed(items) { indice, item ->
            val detalle = detalleResultado(item, busqueda.distanciasM.getOrNull(indice))
            FilaDestino(
                titulo = item.nombre,
                detalle = detalle,
                onClick = { acciones.elegir(Destino(item.nombre, item.punto)) },
                modifier = Modifier.testTag(ETIQUETA_RESULTADO + indice),
                alAnadirParada =
                    acciones.anadir?.let { alAnadir -> { alAnadir(Destino(item.nombre, item.punto)) } },
                etiquetaParada = ETIQUETA_RESULTADO_PARADA + indice,
                nombreParada = item.nombre,
                alIr = { acciones.elegir(Destino(item.nombre, item.punto)) },
                etiquetaIr = ETIQUETA_RESULTADO_IR + indice,
            )
        }
    }
}

/** «categoría · municipio · 12 km» (sin el municipio o sin la distancia si no se conocen). */
@Composable
private fun detalleResultado(
    item: ResultadoBusqueda,
    distanciaM: Double?,
): String {
    val base =
        item.municipio?.let { stringResource(R.string.navegacion_ui_resultado_detalle, item.categoria, it) }
            ?: item.categoria
    return if (distanciaM == null) {
        base
    } else {
        stringResource(R.string.navegacion_ui_resultado_con_distancia, base, FormatoEs.distancia(distanciaM))
    }
}

@Composable
@Suppress("LongParameterList") // fila con su acción opcional de añadir parada
private fun FilaDestino(
    titulo: String,
    detalle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alAnadirParada: (() -> Unit)? = null,
    etiquetaParada: String = "",
    nombreParada: String = titulo,
    alIr: (() -> Unit)? = null,
    etiquetaIr: String = "",
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_FILA)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(
                    start = Espacio.m,
                    top = Espacio.s,
                    bottom = Espacio.s,
                    end =
                        if (alAnadirParada == null && alIr == null) Espacio.m else Espacio.xs,
                ),
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
        if (alIr != null) {
            AccionTexto(
                texto = stringResource(R.string.navegacion_ui_resultado_ir_texto),
                descripcion = stringResource(R.string.navegacion_ui_resultado_ir, nombreParada),
                onClick = alIr,
                etiqueta = etiquetaIr,
            )
        }
        if (alAnadirParada != null) {
            AccionTexto(
                texto = stringResource(R.string.navegacion_ui_parada_anadir_texto),
                descripcion = stringResource(R.string.navegacion_ui_parada_anadir_resultado, nombreParada),
                onClick = alAnadirParada,
                etiqueta = etiquetaParada,
            )
        }
    }
}

/** Botón de texto de una fila de resultado («Ir», «+ Parada»): 48 dp de alto y descripción completa para TalkBack. */
@Composable
private fun AccionTexto(
    texto: String,
    descripcion: String,
    onClick: () -> Unit,
    etiqueta: String,
) {
    TextButton(
        onClick = onClick,
        modifier =
            Modifier
                .heightIn(min = LADO_TACTIL)
                .testTag(etiqueta)
                .semantics { contentDescription = descripcion },
        contentPadding = PaddingValues(horizontal = Espacio.s),
    ) {
        Text(text = texto, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
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

/** Quita el foco del campo y esconde el teclado (al elegir un destino el mapa recupera todo su alto). */
@Composable
private fun cerrarTeclado(): () -> Unit {
    val foco: FocusManager = LocalFocusManager.current
    val teclado: SoftwareKeyboardController? = LocalSoftwareKeyboardController.current
    return {
        foco.clearFocus()
        teclado?.hide()
    }
}
