package app.trombito.rallycopilot.ui.alertas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import app.trombito.rallycopilot.ui.theme.RallyOscuro

private const val RELACION_ARO = 7
private const val RELACION_CIFRA = 0.4f

/** Señal redonda de límite en pequeño (aro rojo, fondo blanco, cifra negra: colores de la señal real). */
@Composable
internal fun SenalLimiteMini(
    limiteKmh: Int,
    lado: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(lado)
                .background(Color.White, CircleShape)
                .border(lado / RELACION_ARO, RallyOscuro.franja, CircleShape),
    ) {
        Text(
            text = limiteKmh.toString(),
            color = Color.Black,
            fontWeight = FontWeight.Black,
            // La cifra va en dp, no en sp: la señal tiene lado fijo y con fuentes grandes se salía del aro.
            fontSize = with(LocalDensity.current) { (lado * RELACION_CIFRA).toSp() },
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}
