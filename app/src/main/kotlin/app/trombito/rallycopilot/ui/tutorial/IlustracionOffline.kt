package app.trombito.rallycopilot.ui.tutorial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.ChipIconoWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.AmbarSobreCarbon
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.RojoAcento
import app.trombito.rallycopilot.ui.theme.TextoSobreCarbon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val DURACION_OFFLINE_MS = 5_000
private const val FIN_MAPA = 0.5f
private const val FIN_TACHADO = 0.7f
private const val ARCOS_WIFI = 3

/**
 * Página 5: un mapa que se va dibujando sin que haga falta cobertura: el símbolo de la red aparece tachado, la
 * ubicación sigue viva y debajo van los chips del mapa y del satélite opcional.
 */
@Composable
internal fun IlustracionOffline() {
    val progreso = rememberProgreso(DURACION_OFFLINE_MS)
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.m)) {
        PanelIlustracion(alto = 170.dp, modifier = Modifier.clearAndSetSemantics {}) {
            Canvas(Modifier.fillMaxSize()) {
                dibujarCalles(tramoDe(progreso.value, 0f, FIN_MAPA))
                dibujarRedTachada(tramoDe(progreso.value, FIN_MAPA, FIN_TACHADO))
            }
            Icon(
                imageVector = Iconos.Lugar,
                contentDescription = null,
                tint = AmbarSobreCarbon,
                modifier = Modifier.align(Alignment.Center).size(40.dp),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Espacio.s),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            ChipIconoWrc(Iconos.Mapa, stringResource(R.string.tutorial_offline_mapa), seleccionado = true)
            ChipIconoWrc(Iconos.Satelite, stringResource(R.string.tutorial_offline_satelite))
        }
    }
}

/** Cuadrícula de calles que se traza de izquierda a derecha, con una carretera principal curva más gruesa. */
private fun DrawScope.dibujarCalles(avance: Float) {
    val ancho = size.width * avance
    val paso = 32.dp.toPx()
    var x = paso / 2
    while (x < size.width) {
        if (x <= ancho) drawLine(Color.White.copy(alpha = 0.10f), Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
        x += paso
    }
    var y = paso / 2
    while (y < size.height) {
        drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, y), Offset(ancho, y), 2.dp.toPx())
        y += paso
    }
    val carretera =
        Path().apply {
            moveTo(0f, size.height * 0.75f)
            cubicTo(
                size.width * 0.3f,
                size.height * 0.95f,
                size.width * 0.4f,
                size.height * 0.1f,
                size.width,
                size.height * 0.3f,
            )
        }
    val medida = PathMeasure().apply { setPath(carretera, false) }
    val trazo = Path()
    medida.getSegment(0f, medida.length * avance, trazo, true)
    drawPath(trazo, Asfalto, style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round))
    drawPath(trazo, AmbarSobreCarbon.copy(alpha = 0.8f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
}

/** El símbolo de la red (tres arcos y un punto) arriba a la derecha, con una raya roja que lo tacha poco a poco. */
private fun DrawScope.dibujarRedTachada(tachado: Float) {
    val centro = Offset(size.width - 44.dp.toPx(), 56.dp.toPx())
    repeat(ARCOS_WIFI) { i ->
        val radio = (10 + 9 * i).dp.toPx()
        drawArc(
            color = TextoSobreCarbon.copy(alpha = 0.85f),
            startAngle = -135f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(centro.x - radio, centro.y - radio),
            size = Size(radio * 2, radio * 2),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
    drawCircle(TextoSobreCarbon.copy(alpha = 0.85f), 3.dp.toPx(), centro)
    val largo = 34.dp.toPx() * tachado
    val inicio = Offset(centro.x - 17.dp.toPx(), centro.y - 20.dp.toPx())
    val angulo = PI.toFloat() / 4
    drawLine(
        RojoAcento,
        inicio,
        Offset(inicio.x + largo * cos(angulo), inicio.y + largo * sin(angulo) * 1.2f),
        strokeWidth = 4.dp.toPx(),
        cap = StrokeCap.Round,
    )
}
