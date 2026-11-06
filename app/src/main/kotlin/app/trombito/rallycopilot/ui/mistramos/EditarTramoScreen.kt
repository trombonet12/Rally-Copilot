package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.edicion.TramoEditable
import app.trombito.rallycopilot.motor.notas.FrasesNota
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.ui.analisis.formatearKmDesdeM
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.edicion.AccionesMapaEdicion
import app.trombito.rallycopilot.ui.edicion.CentradoMapa
import app.trombito.rallycopilot.ui.edicion.ColoresMarcador
import app.trombito.rallycopilot.ui.edicion.EditorNota
import app.trombito.rallycopilot.ui.edicion.EscenaEdicion
import app.trombito.rallycopilot.ui.edicion.MapaEdicionTramo
import app.trombito.rallycopilot.ui.edicion.MarcadorEdicion
import app.trombito.rallycopilot.ui.edicion.TipoMarcador
import app.trombito.rallycopilot.ui.edicion.TrazaMetros
import app.trombito.rallycopilot.ui.edicion.idMarcadorNota
import app.trombito.rallycopilot.ui.edicion.marcadoresDe
import app.trombito.rallycopilot.ui.edicion.textoCortoNota
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.tutorial.ConPista
import app.trombito.rallycopilot.ui.tutorial.LadoPista
import app.trombito.rallycopilot.ui.tutorial.Pista

const val ETIQUETA_EDITAR_MAPA = "editar-tramo-mapa"
const val ETIQUETA_EDITAR_SIN_MAPA = "editar-tramo-sin-mapa"
const val ETIQUETA_EDITAR_CARGANDO = "editar-tramo-cargando"
const val ETIQUETA_EDITAR_RESTAURAR = "editar-tramo-restaurar"
const val ETIQUETA_EDITAR_AYUDA = "editar-tramo-ayuda"
const val ETIQUETA_EDITAR_AYUDA_DIALOGO = "editar-tramo-ayuda-dialogo"
const val ETIQUETA_EDITAR_NOTA = "editar-tramo-nota-"
const val ETIQUETA_EDITAR_NOTA_EDITAR = "editar-tramo-nota-editar-"
const val ETIQUETA_EDITAR_LEYENDA = "editar-tramo-leyenda-"

private const val PESO_MAPA_VERTICAL = 0.46f
private const val PESO_LISTA_VERTICAL = 0.54f
private val ALTO_FILA_NOTA = 56.dp
private val ANCHO_PILDORA_LISTA = 52.dp
private val ICONO_LEYENDA = 16.dp

/** Callbacks de la edición de un tramo. */
data class AccionesEditarTramo(
    val onVolver: () -> Unit = {},
    val onTocarNota: (Nota) -> Unit = {},
    val onSeleccionar: (Nota, Boolean) -> Unit = { _, _ -> },
    val onTocarPunto: (Double) -> Unit = {},
    val onMoverCorte: (Int, Double) -> Unit = { _, _ -> },
    val onSoltarCorte: () -> Unit = {},
    val onMicrosectores: (Int) -> Unit = {},
    val onRestaurar: () -> Unit = {},
    val onAplicar: (Nota) -> Unit = {},
    val onQuitar: () -> Unit = {},
    val onCerrarEditor: () -> Unit = {},
)

/** El mapa de edición: recibe el estilo, la escena (traza y marcadores) y los gestos. */
typealias MapaDeEdicion = @Composable (
    estiloJson: String,
    escena: EscenaEdicion,
    acciones: AccionesMapaEdicion,
    modifier: Modifier,
) -> Unit

/** El mapa real, MapLibre con la traza, las píldoras de las notas y los cortes de sector. */
val MapaEdicionReal: MapaDeEdicion = { estiloJson, escena, acciones, modificador ->
    MapaEdicionTramo(estiloJson, escena, acciones, modificador)
}

/**
 * Edición de las notas y los sectores de un tramo sobre un mapa: la traza con las notas como píldoras («D4»), las
 * marcas y los cortes de sector, y debajo la lista de notas por kilómetro. Tocar una nota (en el mapa o en la lista) la
 * elige; el lápiz de la fila (o tocar la nota ya elegida) abre el editor, un punto del trazo añade una nota y los
 * cortes se arrastran por el mapa. Sin lógica de negocio: estado inmutable y callbacks.
 */
@Composable
fun EditarTramoScreen(
    estado: EditarTramoUiState,
    acciones: AccionesEditarTramo,
    modifier: Modifier = Modifier,
    mapa: MapaDeEdicion = MapaEdicionReal,
) {
    var ayuda by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        BarraSuperior(titulo = stringResource(R.string.edicion_tramo_titulo), onVolver = acciones.onVolver) {
            IconButton(onClick = { ayuda = true }, modifier = Modifier.size(48.dp).testTag(ETIQUETA_EDITAR_AYUDA)) {
                Icon(
                    imageVector = Iconos.Informacion,
                    contentDescription = stringResource(R.string.edicion_ayuda_boton),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        val datos = estado.datos
        when {
            estado.cargando || (datos == null && estado.encontrado) -> {
                Cargando()
            }

            datos == null -> {
                Text(
                    text = stringResource(R.string.edicion_no_encontrado),
                    modifier = Modifier.padding(Espacio.l),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            else -> {
                ContenidoEdicion(datos, estado, acciones, mapa)
            }
        }
        estado.editor?.let { abierto ->
            EditorNota(
                inicial = abierto.nota,
                simplificadas = estado.simplificadas,
                onAplicar = acciones.onAplicar,
                onQuitar = acciones.onQuitar.takeIf { abierto.nota != null },
                onCerrar = acciones.onCerrarEditor,
            )
        }
        if (ayuda) DialogoAyuda { ayuda = false }
    }
}

@Composable
private fun Cargando() {
    Box(Modifier.fillMaxSize().testTag(ETIQUETA_EDITAR_CARGANDO), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.edicion_cargando),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DialogoAyuda(onCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(stringResource(R.string.edicion_ayuda_titulo)) },
        text = {
            Text(
                text = stringResource(R.string.edicion_ayuda),
                modifier = Modifier.testTag(ETIQUETA_EDITAR_AYUDA_DIALOGO),
            )
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text(stringResource(R.string.edicion_ayuda_entendido)) } },
    )
}

/** Lo derivado del tramo que necesitan el mapa y la lista; se recalcula solo si cambia el tramo o los cortes. */
private class VistaEdicion(
    val notas: List<Nota>,
    val marcadores: List<MarcadorEdicion>,
    val escena: EscenaEdicion,
)

@Composable
private fun ContenidoEdicion(
    datos: TramoEditable,
    estado: EditarTramoUiState,
    acciones: AccionesEditarTramo,
    mapa: MapaDeEdicion,
) {
    val traza = remember(datos.tramo.traza) { TrazaMetros(datos.tramo.traza) }
    val notas = datos.hoja.notas
    val marcadores = remember(datos, estado.sectoresM, traza) { marcadoresDe(datos, estado.sectoresM, traza) }
    val seleccion = notas.firstOrNull { it.posicionM == estado.seleccionM }
    val centrado =
        seleccion
            ?.takeIf { estado.centrarSeleccion }
            ?.let { n -> CentradoMapa(traza.puntoEn((n.posicionM + n.finM) / 2.0), estado.centrarVersion) }
    val escena =
        remember(datos, marcadores, estado.microsectoresM, seleccion, centrado) {
            EscenaEdicion(
                traza = datos.tramo.traza,
                marcadores = marcadores,
                microsectores = estado.microsectoresM.map { traza.puntoEn(it) },
                seleccionId = seleccion?.let(::idMarcadorNota),
                centrado = centrado,
            )
        }
    val vista = VistaEdicion(notas, marcadores, escena)
    val gestos = gestosDelMapa(vista, seleccion, acciones)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                ZonaMapa(estado.estiloMapaJson, vista, gestos, mapa, Modifier.weight(1f).fillMaxHeight())
                ListaNotas(estado, vista, seleccion, acciones, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                ZonaMapa(estado.estiloMapaJson, vista, gestos, mapa, Modifier.weight(PESO_MAPA_VERTICAL))
                ListaNotas(estado, vista, seleccion, acciones, Modifier.weight(PESO_LISTA_VERTICAL))
            }
        }
    }
}

/** Gestos del mapa: tocar una nota la elige (si ya lo estaba, abre su editor); el resto pasa tal cual. */
private fun gestosDelMapa(
    vista: VistaEdicion,
    seleccion: Nota?,
    acciones: AccionesEditarTramo,
): AccionesMapaEdicion =
    AccionesMapaEdicion(
        onMarcador = { marcador ->
            vista.notas.firstOrNull { idMarcadorNota(it) == marcador.id }?.let { nota ->
                if (nota == seleccion) acciones.onTocarNota(nota) else acciones.onSeleccionar(nota, false)
            }
        },
        onPuntoTraza = acciones.onTocarPunto,
        onMoverCorte = acciones.onMoverCorte,
        onSoltarCorte = acciones.onSoltarCorte,
    )

/** El mapa y, bajo él, la leyenda en chips; sin mapa instalado, un aviso (la lista sigue sirviendo). */
@Composable
private fun ZonaMapa(
    estilo: String?,
    vista: VistaEdicion,
    gestos: AccionesMapaEdicion,
    mapa: MapaDeEdicion,
    modifier: Modifier,
) {
    Column(modifier.padding(horizontal = Espacio.s), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .testTag(ETIQUETA_EDITAR_MAPA),
        ) {
            if (estilo != null) {
                mapa(estilo, vista.escena, gestos, Modifier.fillMaxSize())
            } else {
                Text(
                    text = stringResource(R.string.edicion_sin_mapa),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(Espacio.xl).testTag(ETIQUETA_EDITAR_SIN_MAPA),
                )
            }
        }
        LeyendaMarcadores(vista.marcadores)
    }
}

/** Chips con el color de cada tipo y cuántos hay, y una línea que explica qué son las marcas y los cortes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeyendaMarcadores(marcadores: List<MarcadorEdicion>) {
    val cuenta = { tipo: TipoMarcador -> marcadores.count { it.tipo == tipo } }
    Column(Modifier.padding(vertical = Espacio.xs)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Espacio.s),
            verticalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            FichaLeyenda(TipoMarcador.NOTA, R.string.edicion_leyenda_nota, cuenta(TipoMarcador.NOTA))
            FichaLeyenda(TipoMarcador.CUIDADO, R.string.edicion_leyenda_cuidado, cuenta(TipoMarcador.CUIDADO))
            FichaLeyenda(TipoMarcador.MARCA, R.string.edicion_leyenda_marca, cuenta(TipoMarcador.MARCA))
            FichaLeyenda(TipoMarcador.RADAR, R.string.edicion_leyenda_radar, cuenta(TipoMarcador.RADAR))
            FichaLeyenda(TipoMarcador.CORTE, R.string.edicion_leyenda_corte, cuenta(TipoMarcador.CORTE))
        }
        Text(
            text = stringResource(R.string.edicion_leyenda_explicacion),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Espacio.xs),
        )
    }
}

@Composable
private fun FichaLeyenda(
    tipo: TipoMarcador,
    nombre: Int,
    cantidad: Int,
) {
    val texto = stringResource(nombre)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CircleShape,
        modifier = Modifier.testTag(ETIQUETA_EDITAR_LEYENDA + tipo.name).semantics(mergeDescendants = true) {},
    ) {
        Row(
            Modifier.padding(horizontal = Espacio.s, vertical = Espacio.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            if (tipo == TipoMarcador.RADAR) {
                Icon(
                    painterResource(R.drawable.ic_alerta_radar),
                    contentDescription = null,
                    tint = Color(ColoresMarcador.fondo(tipo)),
                    modifier = Modifier.size(ICONO_LEYENDA),
                )
            } else {
                Box(Modifier.size(10.dp).background(Color(ColoresMarcador.fondo(tipo)), CircleShape))
            }
            Text("$texto $cantidad", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Las notas por kilómetro, y tras ellas los microsectores y restaurar las automáticas. */
@Composable
private fun ListaNotas(
    estado: EditarTramoUiState,
    vista: VistaEdicion,
    seleccion: Nota?,
    acciones: AccionesEditarTramo,
    modifier: Modifier,
) {
    val estadoLista = rememberLazyListState()
    val indiceElegida = vista.notas.indexOf(seleccion)
    // Elegir en el mapa lleva la lista a esa fila (el +1 es el encabezado).
    LaunchedEffect(seleccion) { if (indiceElegida >= 0) estadoLista.animateScrollToItem(indiceElegida + 1) }
    LazyColumn(
        state = estadoLista,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Espacio.m),
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        item {
            Text(
                text = stringResource(R.string.edicion_notas_titulo, vista.notas.size).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
        }
        itemsIndexed(vista.notas, key = { _, nota -> idMarcadorNota(nota) }) { indice, nota ->
            FilaNota(nota, nota == seleccion, estado.simplificadas, acciones, conPista = indice == 0)
        }
        item { SelectorMicrosectores(estado.microsectoresPorKm, acciones.onMicrosectores) }
        if ((estado.datos?.editadas ?: 0) > 0) {
            item {
                TextButton(onClick = acciones.onRestaurar, modifier = Modifier.testTag(ETIQUETA_EDITAR_RESTAURAR)) {
                    Text(stringResource(R.string.edicion_restaurar))
                }
            }
        }
    }
}

@Composable
private fun FilaNota(
    nota: Nota,
    elegida: Boolean,
    simplificadas: Boolean,
    acciones: AccionesEditarTramo,
    conPista: Boolean,
) {
    val tipo = if (nota.cuidado) TipoMarcador.CUIDADO else TipoMarcador.NOTA
    val frase = FrasesNota.frase(nota.copy(cuidado = false, distanciaSiguienteM = null), simplificadas)
    val km = formatearKmDesdeM(nota.posicionM)
    val superficies = MaterialTheme.colorScheme
    val fondo = if (elegida) superficies.surfaceContainerHighest else superficies.surfaceContainerHigh
    val forma = RoundedCornerShape(12.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_FILA_NOTA)
                .clip(forma)
                .background(fondo)
                .then(if (elegida) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, forma) else Modifier)
                .clickable(role = Role.Button) { acciones.onSeleccionar(nota, true) }
                .padding(start = Espacio.s)
                .testTag(ETIQUETA_EDITAR_NOTA + idMarcadorNota(nota))
                .semantics { selected = elegida },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        ConPista(Pista.EDITAR_TRAMO, stringResource(R.string.pista_editar_tramo), activa = conPista) {
            Box(
                Modifier
                    .width(ANCHO_PILDORA_LISTA)
                    .heightIn(min = 32.dp)
                    .background(Color(ColoresMarcador.fondo(tipo)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = textoCortoNota(nota),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(ColoresMarcador.texto(tipo)),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = frase.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = km,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val descripcion = stringResource(R.string.edicion_editar_nota_descripcion, frase, km)
        IconButton(
            onClick = { acciones.onTocarNota(nota) },
            modifier = Modifier.size(48.dp).testTag(ETIQUETA_EDITAR_NOTA_EDITAR + idMarcadorNota(nota)),
        ) {
            Icon(Iconos.Editar, contentDescription = descripcion, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
