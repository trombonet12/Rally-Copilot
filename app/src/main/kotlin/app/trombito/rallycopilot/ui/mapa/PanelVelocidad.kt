package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally

/** Etiquetas de prueba del panel. */
const val ETIQUETA_VELOCIDAD = "panel-velocidad"
const val ETIQUETA_LIMITE = "panel-limite"
const val ETIQUETA_VIA = "panel-via"

private val FondoSenal = Color.White
private val TextoSenal = Color.Black

/**
 * Velocidad actual (siempre visible; «—» sin dato) y señal de límite, que solo aparece si [limiteKmh] no es null.
 * En vertical va en una fila bajo el mapa; en horizontal ([enColumna]) en una columna al lado.
 */
@Composable
fun PanelVelocidad(
    velocidadKmh: Int?,
    limiteKmh: Int?,
    via: String?,
    enColumna: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.testTag("panel"),
    ) {
        val contenido: @Composable () -> Unit = {
            Velocidad(velocidadKmh)
            if (limiteKmh != null) SenalLimite(limiteKmh)
        }
        Column(
            modifier = Modifier.padding(horizontal = Espacio.l, vertical = Espacio.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Espacio.s, Alignment.CenterVertically),
        ) {
            if (enColumna) {
                contenido()
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Espacio.xl, Alignment.CenterHorizontally),
                ) { contenido() }
            }
            if (!via.isNullOrBlank()) {
                Text(
                    text = via,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(ETIQUETA_VIA),
                )
            }
        }
    }
}

@Composable
private fun Velocidad(velocidadKmh: Int?) {
    val descripcion =
        if (velocidadKmh == null) {
            stringResource(R.string.panel_velocidad_sin_dato)
        } else {
            stringResource(R.string.panel_velocidad_descripcion, velocidadKmh)
        }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .testTag(ETIQUETA_VELOCIDAD)
                .semantics(mergeDescendants = true) { contentDescription = descripcion },
    ) {
        Text(
            text = velocidadKmh?.toString() ?: stringResource(R.string.panel_velocidad_vacia),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 88.sp, lineHeight = 92.sp),
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.panel_unidad),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Señal circular de límite: aro rojo, fondo blanco y cifra negra (colores de la señal real, no del tema). */
@Composable
private fun SenalLimite(limiteKmh: Int) {
    val descripcion = stringResource(R.string.panel_limite_descripcion, limiteKmh)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .testTag(ETIQUETA_LIMITE)
                .size(92.dp)
                .semantics { contentDescription = descripcion }
                .background(FondoSenal, CircleShape)
                .border(BORDE_SENAL, LocalColoresRally.current.franja, CircleShape),
    ) {
        Text(
            text = limiteKmh.toString(),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = TextoSenal,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

private val BORDE_SENAL = 8.dp
