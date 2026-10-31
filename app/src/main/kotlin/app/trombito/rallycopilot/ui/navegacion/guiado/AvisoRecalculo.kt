package app.trombito.rallycopilot.ui.navegacion.guiado

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R

const val ETIQUETA_AVISO_RECALCULO = "aviso_recalculo"

private const val MS_VUELTA = 1200
private const val GRADOS_VUELTA = 360f
private val FORMA = RoundedCornerShape(50)

/** Píldora «Recalculando» con icono girando; solo existe mientras [recalculando]. Región viva: TalkBack la anuncia. */
@Composable
fun AvisoRecalculo(
    recalculando: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!recalculando) return
    val giro by
        rememberInfiniteTransition(label = "recalculo").animateFloat(
            initialValue = 0f,
            targetValue = GRADOS_VUELTA,
            animationSpec = infiniteRepeatable(tween(MS_VUELTA, easing = LinearEasing), RepeatMode.Restart),
            label = "giro",
        )
    Row(
        modifier =
            modifier
                .testTag(ETIQUETA_AVISO_RECALCULO)
                .background(ColoresGuiado.Carbon, FORMA)
                .border(2.dp, ColoresGuiado.Laton, FORMA)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = IconosGuiado.Recalcular,
            contentDescription = null,
            tint = ColoresGuiado.Laton,
            modifier = Modifier.size(24.dp).rotate(giro),
        )
        Text(
            text = stringResource(R.string.guiado_recalculando),
            color = ColoresGuiado.Texto,
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                ),
        )
    }
}
