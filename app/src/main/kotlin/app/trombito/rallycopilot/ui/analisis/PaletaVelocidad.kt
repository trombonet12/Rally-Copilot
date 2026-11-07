package app.trombito.rallycopilot.ui.analisis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.EscalaVelocidad
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_LEYENDA_VELOCIDAD = "analisis-leyenda"

/**
 * Degradado de la velocidad sobre el mapa: violeta (lento) – magenta – naranja – amarillo (rápido). Va de azul a
 * amarillo para que se distinga también con daltonismo rojo-verde, y es claro sobre el mapa oscuro (≥ 3:1).
 * `fraccion` es la posición 0..1 de `EscalaVelocidad.fraccion`.
 */
object PaletaVelocidad {
    /** Paradas del degradado: posición 0..1 y color ARGB. */
    val paradas: List<Pair<Float, Long>> =
        listOf(
            0.00f to 0xFF7B61FF,
            0.33f to 0xFFD946B6,
            0.66f to 0xFFFF7A3D,
            1.00f to 0xFFFFE14D,
        )

    /** Color (ARGB) para la [fraccion] de la escala, interpolado entre las dos paradas vecinas. */
    fun argb(fraccion: Float): Int = color(fraccion).toArgb()

    fun color(fraccion: Float): Color {
        val f = fraccion.coerceIn(0f, 1f)
        val j = paradas.indexOfLast { it.first <= f }.coerceAtMost(paradas.size - 2)
        val (fa, ca) = paradas[j]
        val (fb, cb) = paradas[j + 1]
        val t = if (fb > fa) (f - fa) / (fb - fa) else 0f
        return lerp(Color(ca), Color(cb), t)
    }
}

/**
 * Leyenda de la velocidad, en una sola línea de 24 dp bajo el mapa: título, la barra con el degradado entre las
 * velocidades mínima y máxima de la [escala].
 */
@Composable
fun LeyendaVelocidad(
    escala: EscalaVelocidad,
    modifier: Modifier = Modifier,
) {
    val minima = escala.cortesKmh.first()
    val maxima = escala.cortesKmh.last()
    val descripcion =
        stringResource(
            R.string.analisis_leyenda_descripcion,
            formatearKmhEntero(minima),
            formatearKmhEntero(maxima),
        )
    val degradado = Brush.horizontalGradient(PaletaVelocidad.paradas.map { (_, c) -> Color(c) })
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(ALTO_LEYENDA)
                .testTag(ETIQUETA_LEYENDA_VELOCIDAD)
                .clearAndSetSemantics { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        EtiquetaLeyenda(stringResource(R.string.analisis_leyenda_titulo).uppercase())
        EtiquetaLeyenda(stringResource(R.string.analisis_leyenda_unidad, formatearKmhCelda(minima)))
        Box(Modifier.weight(1f).height(ALTO_BARRA_LEYENDA).background(degradado, RoundedCornerShape(4.dp)))
        EtiquetaLeyenda(stringResource(R.string.analisis_leyenda_unidad, formatearKmhCelda(maxima)))
    }
}

@Composable
private fun EtiquetaLeyenda(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

private val ALTO_LEYENDA = 24.dp
private val ALTO_BARRA_LEYENDA = 8.dp
