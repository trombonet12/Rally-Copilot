package app.trombito.rallycopilot.ui.tutorial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.ChipIconoWrc
import app.trombito.rallycopilot.ui.theme.AmbarSobreCarbon
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono
import app.trombito.rallycopilot.ui.theme.Rotulo
import app.trombito.rallycopilot.ui.theme.TextoSobreCarbon

private const val DURACION_NOTAS_MS = 5_000
private const val FIN_DIBUJO = 0.75f
private const val INICIO_NOTA = 0.45f
private const val FIN_NOTA = 0.55f
private const val INICIO_SALIDA_NOTA = 0.93f
private const val GRADOS = 6
private val ICONOS_DERECHA =
    intArrayOf(
        R.drawable.ic_nota_der_1,
        R.drawable.ic_nota_der_2,
        R.drawable.ic_nota_der_3,
        R.drawable.ic_nota_der_4,
        R.drawable.ic_nota_der_5,
        R.drawable.ic_nota_der_6,
    )

/**
 * Página 1: una carretera que se dibuja con dos curvas a derechas, el coche que la recorre y la nota «DERECHA 4» que
 * aparece al llegar a la segunda; debajo, la escala del 6 (suave) al 1 (la más cerrada), la escuadra y la horquilla.
 */
@Composable
internal fun IlustracionNotas() {
    val progreso = rememberProgreso(DURACION_NOTAS_MS)
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.m)) {
        PanelIlustracion(alto = 150.dp, modifier = Modifier.clearAndSetSemantics {}) {
            Canvas(Modifier.fillMaxSize().padding(Espacio.m)) { dibujarCarreteraNotas(progreso.value) }
            NotaQueAparece(progreso, Modifier.align(BiasAlignment(-0.1f, 0.7f)))
        }
        EscalaNotas()
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s), modifier = Modifier.fillMaxWidth()) {
            ChipNota(R.drawable.ic_nota_der_3, R.string.tutorial_notas_escuadra)
            ChipNota(R.drawable.ic_nota_horquilla_der, R.string.tutorial_notas_horquilla)
        }
    }
}

@Composable
private fun ChipNota(
    icono: Int,
    texto: Int,
) {
    val vector: ImageVector = ImageVector.vectorResource(icono)
    ChipIconoWrc(icono = vector, texto = stringResource(texto))
}

/** El camino: sube desde abajo a la izquierda, gira a derechas y vuelve a girar, más cerrada, hacia abajo. */
private fun DrawScope.caminoNotas(): Path =
    Path().apply {
        moveTo(size.width * 0.15f, size.height)
        cubicTo(
            size.width * 0.15f,
            size.height * 0.55f,
            size.width * 0.25f,
            size.height * 0.20f,
            size.width * 0.45f,
            size.height * 0.20f,
        )
        cubicTo(
            size.width * 0.70f,
            size.height * 0.20f,
            size.width * 0.88f,
            size.height * 0.35f,
            size.width * 0.88f,
            size.height,
        )
    }

private fun DrawScope.dibujarCarreteraNotas(t: Float) {
    val medida = PathMeasure().apply { setPath(caminoNotas(), false) }
    val hasta = medida.length * tramoDe(t, 0f, FIN_DIBUJO)
    val trazo = Path()
    medida.getSegment(0f, hasta, trazo, true)
    drawPath(trazo, Asfalto, style = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Round))
    val discontinua = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 12.dp.toPx()))
    drawPath(trazo, TextoSobreCarbon.copy(alpha = 0.7f), style = Stroke(width = 2.dp.toPx(), pathEffect = discontinua))
    if (hasta > 0f) {
        val posicion = medida.getPosition(hasta)
        drawCircle(Color.Black.copy(alpha = 0.4f), 10.dp.toPx(), posicion)
        drawCircle(AmbarSobreCarbon, 7.dp.toPx(), posicion)
    }
}

/** El rótulo de la nota: aparece con un pequeño rebote al llegar el coche a la segunda curva y se va al repetirse. */
@Composable
private fun NotaQueAparece(
    progreso: State<Float>,
    modifier: Modifier = Modifier,
) {
    val entrada = tramoDe(progreso.value, INICIO_NOTA, FIN_NOTA)
    val salida = 1f - tramoDe(progreso.value, INICIO_SALIDA_NOTA, 1f)
    Row(
        modifier = modifier.alpha(entrada * salida).scale(0.8f + 0.2f * entrada),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        Icon(
            painterResource(R.drawable.ic_nota_der_4),
            contentDescription = null,
            tint = AmbarSobreCarbon,
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = stringResource(R.string.tutorial_notas_nota),
            style = Rotulo,
            color = TextoSobreCarbon,
        )
    }
}

/** Del 6 (suave) al 1 (la más cerrada), con el icono real de cada grado. */
@Composable
private fun EscalaNotas() {
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (grado in GRADOS downTo 1) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(ICONOS_DERECHA[grado - 1]),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(grado.toString(), style = EtiquetaIcono, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.tutorial_notas_suave),
                style = EtiquetaIcono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.tutorial_notas_cerrada),
                style = EtiquetaIcono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
