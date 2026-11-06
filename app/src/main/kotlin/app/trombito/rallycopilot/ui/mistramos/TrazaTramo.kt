package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.theme.LocalColoresRally
import app.trombito.rallycopilot.ui.theme.Radios
import app.trombito.rallycopilot.ui.theme.RojoAcento

const val ETIQUETA_TRAZA = "tramo-traza"

private val ALTO_TRAZA = 200.dp
private const val MAX_PUNTOS = 600
private const val MARGEN_PX = 36f
private const val GROSOR_PX = 7f
private const val RADIO_MARCA_PX = 11f
private const val RADIO_HALO_PX = 17f
private const val OPACIDAD_HALO = 0.35f
private const val RELACION_BORDE = 1.6f

/**
 * Trazo del tramo dibujado en un lienzo (sin cargar el mapa vectorial: robusto y sin red). Salida en verde, meta en
 * rojo, norte arriba.
 */
@Composable
fun TrazaTramo(
    traza: List<PuntoGeo>,
    modifier: Modifier = Modifier,
) {
    val colorTrazo = MaterialTheme.colorScheme.primary
    val colorSalida = LocalColoresRally.current.deltaMejor
    val colorMeta = LocalColoresRally.current.franja
    val descripcion = stringResource(R.string.detalle_traza_descripcion)
    val aligerada = remember(traza) { aligerar(traza, MAX_PUNTOS) }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(ALTO_TRAZA)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.medium)
            .testTag(ETIQUETA_TRAZA)
            .semantics { contentDescription = descripcion },
    ) {
        val puntos = proyectarTraza(aligerada, size.width, size.height, MARGEN_PX)
        if (puntos.size >= 2) {
            val ruta = Path()
            puntos.forEachIndexed { i, p -> if (i == 0) ruta.moveTo(p.x, p.y) else ruta.lineTo(p.x, p.y) }
            drawPath(ruta, Color.Black, style = trazo(GROSOR_PX * RELACION_BORDE))
            drawPath(ruta, colorTrazo, style = trazo(GROSOR_PX))
            marca(puntos.first(), colorSalida)
            marca(puntos.last(), colorMeta)
        }
    }
}

private fun trazo(grosor: Float) = Stroke(grosor, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.marca(
    centro: Offset,
    color: Color,
) {
    drawCircle(color.copy(alpha = OPACIDAD_HALO), RADIO_HALO_PX, centro)
    drawCircle(Color.White, RADIO_MARCA_PX + 3f, centro)
    drawCircle(color, RADIO_MARCA_PX, centro)
}

/** Etiqueta de prueba de la miniatura de traza. */
const val ETIQUETA_MINIATURA = "tramo-miniatura"

private const val MAX_PUNTOS_MINIATURA = 80
private val MARGEN_MINIATURA = 8.dp
private val GROSOR_MINIATURA = 3.dp
private val RADIO_PUNTA_MINIATURA = 3.dp

/**
 * Miniatura cuadrada de [lado] con la silueta del tramo: trazo ámbar sobre fondo oscuro, sin mapa ni datos de red. Es
 * decorativa (el nombre del tramo ya lo dice todo) y no aparece en accesibilidad. Sin traza, queda el fondo vacío.
 */
@Composable
fun MiniaturaTraza(
    traza: List<PuntoGeo>,
    lado: Dp,
    modifier: Modifier = Modifier,
) {
    val colorTrazo = MaterialTheme.colorScheme.primary
    val colorSalida = LocalColoresRally.current.deltaMejor
    val aligerada = remember(traza) { aligerar(traza, MAX_PUNTOS_MINIATURA) }
    Canvas(
        modifier
            .size(lado)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, Radios.formaTarjeta)
            .testTag(ETIQUETA_MINIATURA),
    ) {
        val puntos = proyectarTraza(aligerada, size.width, size.height, MARGEN_MINIATURA.toPx())
        if (puntos.size >= 2) {
            val ruta = Path()
            puntos.forEachIndexed { i, p -> if (i == 0) ruta.moveTo(p.x, p.y) else ruta.lineTo(p.x, p.y) }
            drawPath(ruta, colorTrazo, style = trazo(GROSOR_MINIATURA.toPx()))
            drawCircle(colorSalida, RADIO_PUNTA_MINIATURA.toPx(), puntos.first())
            drawCircle(RojoAcento, RADIO_PUNTA_MINIATURA.toPx(), puntos.last())
        }
    }
}
