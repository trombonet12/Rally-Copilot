package app.trombito.rallycopilot.ui.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Radios

private val ALTO_BARRA = 10.dp

/**
 * Barra de progreso 0..1 del sistema WRC: pista oscura y relleno ámbar con extremos redondos. Se anuncia como barra de
 * progreso con la [descripcion] (p. ej. «Piloto de club, 52 % del camino al siguiente nivel»).
 */
@Composable
internal fun BarraProgresoWrc(
    progreso: Float,
    descripcion: String,
    modifier: Modifier = Modifier,
) {
    val valor = progreso.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(ALTO_BARRA)
            .clip(Radios.formaPildora)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics {
                contentDescription = descripcion
                progressBarRangeInfo = ProgressBarRangeInfo(valor, 0f..1f)
            },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(valor)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** Título de una sección de la temporada, anunciado como encabezado. */
@Composable
internal fun TituloSeccion(
    texto: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = texto,
        style = Cuerpo.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics { heading() },
    )
}
