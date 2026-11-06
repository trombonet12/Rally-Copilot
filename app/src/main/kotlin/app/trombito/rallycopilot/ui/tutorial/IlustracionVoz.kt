package app.trombito.rallycopilot.ui.tutorial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.ChipIconoWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.AmbarSobreCarbon
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Rotulo
import app.trombito.rallycopilot.ui.theme.TextoSobreCarbon

private const val DURACION_VOZ_MS = 4_000
private const val ONDAS = 3
private const val CHIPS = 4

/**
 * Página 2: el micrófono con ondas que se expanden junto a la palabra «Copiloto», y debajo las frases de ejemplo,
 * resaltadas de una en una.
 */
@Composable
internal fun IlustracionVoz() {
    val progreso = rememberProgreso(DURACION_VOZ_MS)
    val activo = (progreso.value * CHIPS).toInt().coerceAtMost(CHIPS - 1)
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.m)) {
        PanelIlustracion(alto = 110.dp, modifier = Modifier.clearAndSetSemantics {}) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espacio.l),
            ) {
                OndasMicrofono(progreso)
                Text(
                    stringResource(R.string.tutorial_voz_palabra),
                    style = Rotulo,
                    color = TextoSobreCarbon,
                    modifier = Modifier.width(150.dp),
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Espacio.s),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            ChipIconoWrc(
                Iconos.Cronometro,
                stringResource(R.string.tutorial_voz_chip_falta),
                seleccionado = activo == 0,
            )
            ChipIconoWrc(Iconos.Notas, stringResource(R.string.tutorial_voz_chip_notas), seleccionado = activo == 1)
            ChipIconoWrc(
                Iconos.Satelite,
                stringResource(R.string.tutorial_voz_chip_satelite),
                seleccionado =
                    activo == 2,
            )
            ChipIconoWrc(
                Iconos.Direcciones,
                stringResource(R.string.tutorial_voz_chip_llevame),
                seleccionado =
                    activo == 3,
            )
        }
    }
}

/** El micrófono con tres ondas que se expanden y se desvanecen. */
@Composable
private fun OndasMicrofono(progreso: State<Float>) {
    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(84.dp)) {
            repeat(ONDAS) { onda ->
                val fase = (progreso.value * ONDAS * 2 + onda / ONDAS.toFloat()) % 1f
                val radio = size.minDimension / 2 * (0.45f + 0.55f * fase)
                drawCircle(
                    AmbarSobreCarbon.copy(alpha = 0.6f * (1f - fase)),
                    radio,
                    Offset(size.width / 2, size.height / 2),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        Icon(Iconos.Microfono, contentDescription = null, tint = AmbarSobreCarbon, modifier = Modifier.size(32.dp))
    }
}
