package app.trombito.rallycopilot.ui.tutorial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.SenalLimiteWrc
import app.trombito.rallycopilot.ui.theme.AmbarSobreCarbon
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono
import app.trombito.rallycopilot.ui.theme.TextoSecundarioSobreCarbon
import app.trombito.rallycopilot.ui.theme.TextoSobreCarbon

private const val DURACION_RADAR_MS = 5_000
private const val SALIDA_BOCADILLO = 0.85f
private const val X_RADAR = 0.62f

/**
 * Página 4: el coche se acerca por su carril a un radar fijo; antes de llegar sale el bocadillo «Radar fijo 80». En el
 * carril contrario hay otro radar, marcado como sentido contrario.
 */
@Composable
internal fun IlustracionRadar() {
    val progreso = rememberProgreso(DURACION_RADAR_MS)
    PanelIlustracion(alto = 190.dp, modifier = Modifier.clearAndSetSemantics {}) {
        Canvas(Modifier.fillMaxSize()) {
            val alto = size.height * 0.30f
            val y = size.height * 0.64f
            drawRect(Asfalto, Offset(0f, y - alto / 2), Size(size.width, alto))
            val discontinua = PathEffect.dashPathEffect(floatArrayOf(16.dp.toPx(), 12.dp.toPx()))
            drawLine(
                TextoSobreCarbon.copy(alpha = 0.7f),
                Offset(0f, y),
                Offset(size.width, y),
                strokeWidth = 2.dp.toPx(),
                pathEffect = discontinua,
            )
            val cocheX = size.width * (-0.1f + 0.85f * progreso.value)
            val ancho = 34.dp.toPx()
            drawRoundRect(
                AmbarSobreCarbon,
                Offset(cocheX, y - alto * 0.42f),
                Size(ancho, alto * 0.30f),
                CornerRadius(6.dp.toPx()),
            )
            drawCircle(Color.Black.copy(alpha = 0.35f), 4.dp.toPx(), Offset(cocheX + ancho * 0.72f, y - alto * 0.27f))
        }
        Bocadillo(progreso.value, Modifier.align(BiasAlignment(X_RADAR * 2 - 1, -0.88f)))
        IconoRadar(Modifier.align(BiasAlignment(X_RADAR * 2 - 1, -0.15f)))
        ChipContrario(Modifier.align(BiasAlignment(-0.7f, 0.82f)))
    }
}

@Composable
private fun IconoRadar(modifier: Modifier) {
    Box(
        modifier.size(34.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_alerta_radar),
            contentDescription = null,
            tint = TextoSobreCarbon,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Aparece cuando el coche está cerca del radar y desaparece al pasarlo. */
@Composable
private fun Bocadillo(
    t: Float,
    modifier: Modifier = Modifier,
) {
    val visible = tramoDe(t, 0.3f, 0.4f) * (1f - tramoDe(t, SALIDA_BOCADILLO, 0.95f))
    Row(
        modifier =
            modifier
                .alpha(visible)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                .padding(horizontal = Espacio.m, vertical = Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        SenalLimiteWrc(limiteKmh = 80, lado = 32.dp)
        Text(
            text = stringResource(R.string.tutorial_radar_bocadillo),
            style = EtiquetaIcono.copy(fontSize = BocadilloTexto),
            color = TextoSobreCarbon,
        )
    }
}

private val BocadilloTexto = 15.sp

@Composable
private fun ChipContrario(modifier: Modifier) {
    Row(
        modifier =
            modifier
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                .padding(horizontal = Espacio.m, vertical = Espacio.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_alerta_radar),
            contentDescription = null,
            tint = TextoSecundarioSobreCarbon,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.tutorial_radar_contrario),
            style = EtiquetaIcono,
            color = TextoSecundarioSobreCarbon,
        )
    }
}
