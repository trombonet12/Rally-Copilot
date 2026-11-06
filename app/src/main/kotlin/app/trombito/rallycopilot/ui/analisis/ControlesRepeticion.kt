package app.trombito.rallycopilot.ui.analisis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.tramos.formatearTiempo

const val ETIQUETA_REPETICION_PLAY = "analisis-repeticion-play"
const val ETIQUETA_REPETICION_POSICION = "analisis-repeticion-posicion"
const val ETIQUETA_REPETICION_TIEMPO = "analisis-repeticion-tiempo"
const val ETIQUETA_REPETICION_VELOCIDAD = "analisis-repeticion-velocidad-"

private val ALTO_MANDO = 56.dp
private val LADO_BOTON = 48.dp
private val ALTO_SEGMENTOS = 30.dp
private val ANCHO_SEGMENTO = 40.dp
private val ALTO_BARRA = 24.dp

/**
 * Mando de la repetición, de 56 dp y bajo el mapa (no lo tapa): play/pausa a la izquierda y, a su lado, el instante
 * actual sobre la duración con la velocidad ×1 / ×4 / ×16 en segmentos y debajo la barra para moverse por el
 * recorrido. Sin lógica: todo son callbacks sobre el [estado].
 */
@Composable
fun ControlesRepeticion(
    estado: EstadoRepeticion,
    duracionMs: Long,
    acciones: AccionesAnalisis,
    modifier: Modifier = Modifier,
) {
    val actual = estado.tMs ?: 0L
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(ALTO_MANDO)
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
                .padding(end = Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotonPlay(estado.reproduciendo, acciones.onAlternarRepeticion)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${formatearTiempo(actual)} / ${formatearTiempo(duracionMs)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).testTag(ETIQUETA_REPETICION_TIEMPO),
                )
                SegmentosVelocidad(estado.velocidad, acciones.onVelocidad)
            }
            BarraRepeticion(actual, duracionMs, acciones.onPosicion)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarraRepeticion(
    actual: Long,
    duracionMs: Long,
    onPosicion: (Long) -> Unit,
) {
    val descripcion = stringResource(R.string.analisis_repeticion_posicion)
    // La barra ocupa 24 dp (el mando entero son 56): el gesto de arrastre sigue siendo de ancho completo.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Slider(
            value = actual.toFloat().coerceIn(0f, duracionMs.toFloat()),
            onValueChange = { onPosicion(it.toLong()) },
            valueRange = 0f..duracionMs.toFloat().coerceAtLeast(1f),
            colors =
                SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(ALTO_BARRA)
                    .testTag(ETIQUETA_REPETICION_POSICION)
                    .semantics { contentDescription = descripcion },
        )
    }
}

@Composable
private fun BotonPlay(
    reproduciendo: Boolean,
    onAlternar: () -> Unit,
) {
    val texto =
        stringResource(if (reproduciendo) R.string.analisis_repeticion_pausa else R.string.analisis_repeticion_play)
    IconButton(onClick = onAlternar, modifier = Modifier.size(LADO_BOTON).testTag(ETIQUETA_REPETICION_PLAY)) {
        Icon(
            imageVector = if (reproduciendo) Iconos.Pausa else Iconos.Empezar,
            contentDescription = texto,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Los tres ×1 / ×4 / ×16 juntos en un solo control con segmentos; el elegido va relleno. */
@Composable
private fun SegmentosVelocidad(
    elegida: VelocidadRepeticion,
    onElegir: (VelocidadRepeticion) -> Unit,
) {
    val forma = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .height(ALTO_SEGMENTOS)
            .clip(forma)
            .border(1.dp, MaterialTheme.colorScheme.outline, forma),
    ) {
        VelocidadRepeticion.entries.forEach { velocidad ->
            val marcada = velocidad == elegida
            Box(
                modifier =
                    Modifier
                        .width(ANCHO_SEGMENTO)
                        .fillMaxHeight()
                        .background(if (marcada) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .selectable(selected = marcada, role = Role.RadioButton, onClick = { onElegir(velocidad) })
                        .testTag(ETIQUETA_REPETICION_VELOCIDAD + velocidad.factor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.analisis_repeticion_velocidad, velocidad.factor),
                    style = MaterialTheme.typography.labelLarge,
                    color =
                        if (marcada) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
