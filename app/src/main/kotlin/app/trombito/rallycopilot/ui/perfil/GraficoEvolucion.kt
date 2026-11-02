package app.trombito.rallycopilot.ui.perfil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.PuntoEvolucion
import app.trombito.rallycopilot.ui.mistramos.formatearFechaCorta
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.tramos.formatearTiempo

const val ETIQUETA_GRAFICO = "perfil-grafico"

private val ALTO_GRAFICO = 180.dp
private val ANCHO_EJE = 64.dp
private const val MARGEN_PX = 28f
private const val GROSOR_LINEA_PX = 5f
private const val GROSOR_GUIA_PX = 2f
private const val RADIO_PUNTO_PX = 8f
private const val RADIO_RECORD_PX = 14f
private const val OPACIDAD_GUIA = 0.5f

/**
 * Gráfico del tiempo por fecha de un tramo, dibujado en un lienzo (sin librerías): el mejor tiempo arriba, la línea
 * une las pasadas y los récords van marcados con un punto grande de latón.
 */
@Composable
fun GraficoEvolucion(
    evolucion: List<PuntoEvolucion>,
    modifier: Modifier = Modifier,
) {
    val escala = remember(evolucion) { escalarEvolucion(evolucion) }
    val records = evolucion.count { it.esRecord }
    val descripcion =
        stringResource(
            R.string.perfil_grafico_descripcion,
            evolucion.size,
            formatearTiempo(escala.mejorMs),
            records,
        )
    Column(modifier.fillMaxWidth().testTag(ETIQUETA_GRAFICO).semantics { contentDescription = descripcion }) {
        Row(Modifier.fillMaxWidth().height(ALTO_GRAFICO)) {
            EjeTiempos(escala, Modifier.width(ANCHO_EJE).fillMaxHeight())
            Lienzo(escala, Modifier.weight(1f).fillMaxHeight())
        }
        EjeFechas(escala)
    }
}

@Composable
private fun EjeTiempos(
    escala: EscalaEvolucion,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        EtiquetaEje(formatearTiempo(escala.mejorMs))
        EtiquetaEje(formatearTiempo(escala.peorMs))
    }
}

@Composable
private fun EjeFechas(escala: EscalaEvolucion) {
    Row(Modifier.fillMaxWidth().padding(start = ANCHO_EJE), horizontalArrangement = Arrangement.SpaceBetween) {
        EtiquetaEje(formatearFechaCorta(escala.desdeMs))
        if (escala.hastaMs != escala.desdeMs) EtiquetaEje(formatearFechaCorta(escala.hastaMs))
    }
}

@Composable
private fun EtiquetaEje(texto: String) {
    Text(text = texto, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Lienzo(
    escala: EscalaEvolucion,
    modifier: Modifier,
) {
    val colores = MaterialTheme.colorScheme
    val linea = colores.secondary
    val guia = colores.outlineVariant
    val normal = colores.onSurfaceVariant
    val record = colores.primary
    Canvas(modifier.background(colores.surfaceContainerLowest, MaterialTheme.shapes.small)) {
        val posiciones = escala.puntos.map { Offset(MARGEN_PX + it.x * (size.width - 2 * MARGEN_PX), margenY(it.y)) }
        drawGuia(margenY(0f), guia)
        drawGuia(margenY(1f), guia)
        posiciones.zipWithNext().forEach { (a, b) -> drawLine(linea, a, b, GROSOR_LINEA_PX, StrokeCap.Round) }
        escala.puntos.forEachIndexed { i, p ->
            if (p.esRecord) {
                drawCircle(record.copy(alpha = OPACIDAD_GUIA), RADIO_RECORD_PX, posiciones[i])
                drawCircle(record, RADIO_PUNTO_PX, posiciones[i])
            } else {
                drawCircle(normal, RADIO_PUNTO_PX, posiciones[i])
            }
        }
    }
}

private fun DrawScope.margenY(y: Float): Float = MARGEN_PX + y * (size.height - 2 * MARGEN_PX)

private fun DrawScope.drawGuia(
    y: Float,
    color: Color,
) {
    drawLine(color, Offset(0f, y), Offset(size.width, y), GROSOR_GUIA_PX)
}

/** Leyenda corta bajo el gráfico: qué es el punto grande. */
@Composable
fun LeyendaRecords(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.perfil_grafico_leyenda),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = Espacio.xs),
    )
}
