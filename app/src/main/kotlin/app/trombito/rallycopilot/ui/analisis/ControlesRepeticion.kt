package app.trombito.rallycopilot.ui.analisis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.navegacion.guiado.ColoresGuiado
import app.trombito.rallycopilot.ui.tramos.formatearTiempo

const val ETIQUETA_REPETICION_PLAY = "analisis-repeticion-play"
const val ETIQUETA_REPETICION_POSICION = "analisis-repeticion-posicion"
const val ETIQUETA_REPETICION_TIEMPO = "analisis-repeticion-tiempo"
const val ETIQUETA_REPETICION_VELOCIDAD = "analisis-repeticion-velocidad-"

private const val OPACIDAD_FONDO = 0.88f
private val LADO_BOTON = 48.dp

/**
 * Mando de la repetición, sobre el mapa: play/pausa, velocidad ×1 / ×4 / ×16, el instante actual sobre la duración y
 * una barra para moverse por el recorrido. Sin lógica: todo son callbacks sobre el [estado].
 */
@Composable
fun ControlesRepeticion(
    estado: EstadoRepeticion,
    duracionMs: Long,
    acciones: AccionesAnalisis,
    modifier: Modifier = Modifier,
) {
    val actual = estado.tMs ?: 0L
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(ColoresGuiado.Carbon.copy(alpha = OPACIDAD_FONDO), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BotonPlay(estado.reproduciendo, acciones.onAlternarRepeticion)
            VelocidadRepeticion.entries.forEach { velocidad ->
                ChipVelocidad(velocidad, velocidad == estado.velocidad) { acciones.onVelocidad(velocidad) }
            }
            Text(
                text = "${formatearTiempo(actual)} / ${formatearTiempo(duracionMs)}",
                style = MaterialTheme.typography.labelLarge,
                color = ColoresGuiado.Texto,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = 4.dp).testTag(ETIQUETA_REPETICION_TIEMPO),
            )
        }
        val descripcion = stringResource(R.string.analisis_repeticion_posicion)
        Slider(
            value = actual.toFloat().coerceIn(0f, duracionMs.toFloat()),
            onValueChange = { acciones.onPosicion(it.toLong()) },
            valueRange = 0f..duracionMs.toFloat().coerceAtLeast(1f),
            colors =
                SliderDefaults.colors(
                    thumbColor = ColoresGuiado.Laton,
                    activeTrackColor = ColoresGuiado.Laton,
                    inactiveTrackColor = ColoresGuiado.Texto.copy(alpha = 0.3f),
                ),
            modifier = Modifier.testTag(ETIQUETA_REPETICION_POSICION).semantics { contentDescription = descripcion },
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
            tint = ColoresGuiado.Laton,
        )
    }
}

@Composable
private fun ChipVelocidad(
    velocidad: VelocidadRepeticion,
    elegida: Boolean,
    onElegir: () -> Unit,
) {
    val texto = stringResource(R.string.analisis_repeticion_velocidad, velocidad.factor)
    FilterChip(
        selected = elegida,
        onClick = onElegir,
        label = { Text(texto) },
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = ColoresGuiado.Carbon,
                labelColor = ColoresGuiado.Texto,
                selectedContainerColor = ColoresGuiado.Laton,
                selectedLabelColor = ColoresGuiado.Carbon,
            ),
        modifier = Modifier.testTag(ETIQUETA_REPETICION_VELOCIDAD + velocidad.factor),
    )
}
