package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.edicion.TramoEditable
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.tramos.CortesTramo
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.edicion.EditorNota
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally

const val ETIQUETA_EDITAR_LIENZO = "editar-tramo-lienzo"
const val ETIQUETA_EDITAR_RESTAURAR = "editar-tramo-restaurar"

private val ALTO_LIENZO = 360.dp
private const val MARGEN_PX = 48f
private const val GROSOR_PX = 7f
private const val RADIO_NOTA_PX = 13f
private const val RADIO_CORTE_PX = 15f
private const val RADIO_MARCA_PX = 9f
private const val RADIO_MICRO_PX = 5f
private const val RELACION_BORDE = 1.6f
private const val TOQUE_NOTA_PX = 44f
private const val TOQUE_CORTE_PX = 56f
private const val TOQUE_TRAZO_PX = 60f
private val COLOR_CUIDADO = Color(0xFFFFB300)
private val COLOR_NOTA = Color(0xFFF2EDE0)

/** Callbacks de la edición de un tramo. */
data class AccionesEditarTramo(
    val onVolver: () -> Unit = {},
    val onTocarNota: (Nota) -> Unit = {},
    val onTocarPunto: (Double) -> Unit = {},
    val onMoverCorte: (Int, Double) -> Unit = { _, _ -> },
    val onSoltarCorte: () -> Unit = {},
    val onMicrosectores: (Int) -> Unit = {},
    val onRestaurar: () -> Unit = {},
    val onAplicar: (Nota) -> Unit = {},
    val onQuitar: () -> Unit = {},
    val onCerrarEditor: () -> Unit = {},
)

/**
 * Edición de las notas y los sectores de un tramo sobre su trazo: las notas son círculos que se tocan para corregirlas
 * (un punto vacío del trazo añade una), las marcas de «marca aquí» son cuadrados y los cortes de sector se arrastran.
 */
@Composable
fun EditarTramoScreen(
    estado: EditarTramoUiState,
    acciones: AccionesEditarTramo,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        BarraSuperior(titulo = stringResource(R.string.edicion_tramo_titulo), onVolver = acciones.onVolver)
        val datos = estado.datos
        when {
            estado.cargando || (datos == null && estado.encontrado) -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            datos == null -> {
                Text(
                    text = stringResource(R.string.edicion_no_encontrado),
                    modifier = Modifier.padding(Espacio.l),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            else -> {
                ContenidoEdicion(datos, estado, acciones)
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
    }
}

@Composable
private fun ContenidoEdicion(
    datos: TramoEditable,
    estado: EditarTramoUiState,
    acciones: AccionesEditarTramo,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Espacio.l),
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        Text(
            text = stringResource(R.string.edicion_ayuda),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LienzoEdicion(datos, estado.sectoresM, estado.microsectoresM, acciones)
        Text(
            text = stringResource(R.string.edicion_marcas, datos.marcas.size),
            style = MaterialTheme.typography.bodyMedium,
        )
        SelectorMicrosectores(estado.microsectoresPorKm, acciones.onMicrosectores)
        if (datos.editadas > 0) {
            TextButton(onClick = acciones.onRestaurar, modifier = Modifier.testTag(ETIQUETA_EDITAR_RESTAURAR)) {
                Text(stringResource(R.string.edicion_restaurar))
            }
        }
    }
}

@Composable
private fun LienzoEdicion(
    datos: TramoEditable,
    sectoresM: List<Double>,
    microsectoresM: List<Double>,
    acciones: AccionesEditarTramo,
) {
    val colorTrazo = MaterialTheme.colorScheme.primary
    val colorSalida = LocalColoresRally.current.deltaMejor
    val colorMeta = LocalColoresRally.current.franja
    val colorCorte = MaterialTheme.colorScheme.tertiary
    val descripcion = stringResource(R.string.edicion_lienzo)
    val densidad = LocalDensity.current.density
    var tamano by remember { mutableStateOf(Offset.Zero) }
    val lienzo =
        remember(datos.tramo, tamano) {
            LienzoTramo(datos.tramo.traza, tamano.x.coerceAtLeast(1f), tamano.y.coerceAtLeast(1f), MARGEN_PX)
        }
    val notas = datos.hoja.notas
    val cortes by rememberUpdatedState(sectoresM)
    val enMano by rememberUpdatedState(acciones)
    var arrastrando by remember { mutableStateOf<Int?>(null) }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(ALTO_LIENZO)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.medium)
            .onSizeChanged { tamano = Offset(it.width.toFloat(), it.height.toFloat()) }
            .testTag(ETIQUETA_EDITAR_LIENZO)
            .semantics { contentDescription = descripcion }
            .pointerInput(lienzo, notas) {
                detectTapGestures { toque ->
                    val cercana = notaCercana(lienzo, notas, toque, TOQUE_NOTA_PX * densidad)
                    if (cercana != null) {
                        enMano.onTocarNota(cercana)
                    } else {
                        lienzo.proyectar(toque)?.takeIf { it.distanciaPx <= TOQUE_TRAZO_PX * densidad }?.let {
                            enMano.onTocarPunto(it.metros)
                        }
                    }
                }
            }.pointerInput(lienzo) {
                detectDragGestures(
                    onDragStart = { inicio ->
                        arrastrando = corteCercano(lienzo, cortes, inicio, TOQUE_CORTE_PX * densidad)
                    },
                    onDragEnd = {
                        if (arrastrando != null) enMano.onSoltarCorte()
                        arrastrando = null
                    },
                    onDragCancel = { arrastrando = null },
                ) { cambio, _ ->
                    val indice = arrastrando ?: return@detectDragGestures
                    cambio.consume()
                    lienzo.proyectar(cambio.position)?.let { enMano.onMoverCorte(indice, it.metros) }
                }
            },
    ) {
        if (lienzo.trazo.size < 2) return@Canvas
        val colores = ColoresEdicion(colorTrazo, colorSalida, colorMeta, colorCorte)
        dibujarEdicion(lienzo, datos, Cortes(sectoresM, microsectoresM), arrastrando, colores, densidad)
    }
}

/** Los cortes que se dibujan: los de sector (se arrastran) y los microsectores (puntitos). */
private class Cortes(
    val sectoresM: List<Double>,
    val microsectoresM: List<Double>,
)

/** Los colores con que se dibuja el lienzo de edición. */
private class ColoresEdicion(
    val trazo: Color,
    val salida: Color,
    val meta: Color,
    val corte: Color,
)

@Suppress("LongParameterList") // el lienzo, lo que se pinta, el corte en mano y cómo pintarlo
private fun DrawScope.dibujarEdicion(
    lienzo: LienzoTramo,
    datos: TramoEditable,
    cortes: Cortes,
    arrastrando: Int?,
    colores: ColoresEdicion,
    densidad: Float,
) {
    dibujarTrazo(lienzo, colores.trazo)
    cortes.microsectoresM.forEach {
        val centro = lienzo.pixelEn(it)
        drawCircle(Color.Black, (RADIO_MICRO_PX + 2f) * densidad, centro)
        drawCircle(colores.corte, RADIO_MICRO_PX * densidad, centro)
    }
    datos.marcas.forEach { m ->
        val centro = lienzo.pixelEn(m.marca.posicionM)
        drawRect(
            Color.White,
            centro - Offset(RADIO_MARCA_PX + 2f, RADIO_MARCA_PX + 2f) * densidad,
            cuadrado(RADIO_MARCA_PX + 2f, densidad),
        )
        drawRect(
            colores.meta,
            centro - Offset(RADIO_MARCA_PX, RADIO_MARCA_PX) * densidad,
            cuadrado(RADIO_MARCA_PX, densidad),
        )
    }
    cortes.sectoresM.forEachIndexed { i, m ->
        val centro = lienzo.pixelEn(m)
        drawCircle(Color.White, (RADIO_CORTE_PX + 3f) * densidad, centro)
        drawCircle(if (arrastrando == i) colores.trazo else colores.corte, RADIO_CORTE_PX * densidad, centro)
    }
    datos.hoja.notas.forEach { n ->
        val centro = lienzo.pixelEn((n.posicionM + n.finM) / 2.0)
        drawCircle(Color.Black, (RADIO_NOTA_PX + 3f) * densidad, centro)
        drawCircle(if (n.cuidado) COLOR_CUIDADO else COLOR_NOTA, RADIO_NOTA_PX * densidad, centro)
    }
    drawCircle(colores.salida, RADIO_NOTA_PX * densidad, lienzo.trazo.first())
    drawCircle(colores.meta, RADIO_NOTA_PX * densidad, lienzo.trazo.last())
}

private fun cuadrado(
    radio: Float,
    densidad: Float,
) = androidx.compose.ui.geometry
    .Size(2 * radio * densidad, 2 * radio * densidad)

private fun DrawScope.dibujarTrazo(
    lienzo: LienzoTramo,
    color: Color,
) {
    val ruta = Path()
    lienzo.trazo.forEachIndexed { i, p -> if (i == 0) ruta.moveTo(p.x, p.y) else ruta.lineTo(p.x, p.y) }
    val trazo = { grosor: Float -> Stroke(grosor, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    drawPath(ruta, Color.Black, style = trazo(GROSOR_PX * RELACION_BORDE))
    drawPath(ruta, color, style = trazo(GROSOR_PX))
}

/** La nota cuyo círculo (a mitad de la curva) cae a menos de [radioPx] del [toque], la más cercana si hay varias. */
internal fun notaCercana(
    lienzo: LienzoTramo,
    notas: List<Nota>,
    toque: Offset,
    radioPx: Float,
): Nota? =
    notas
        .map { it to (lienzo.pixelEn((it.posicionM + it.finM) / 2.0) - toque).getDistance() }
        .filter { it.second <= radioPx }
        .minByOrNull { it.second }
        ?.first

/** El índice del corte de sector cuyo asa cae a menos de [radioPx] del [toque]; null si ninguno. */
internal fun corteCercano(
    lienzo: LienzoTramo,
    cortes: List<Double>,
    toque: Offset,
    radioPx: Float,
): Int? =
    cortes.indices
        .map { it to (lienzo.pixelEn(cortes[it]) - toque).getDistance() }
        .filter { it.second <= radioPx }
        .minByOrNull { it.second }
        ?.first
