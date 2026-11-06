package app.trombito.rallycopilot.ui.tutorial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.theme.AmbarSobreCarbon
import app.trombito.rallycopilot.ui.theme.CifraMedia
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono
import app.trombito.rallycopilot.ui.theme.RallyOscuro
import app.trombito.rallycopilot.ui.theme.TextoSecundarioSobreCarbon
import app.trombito.rallycopilot.ui.theme.TextoSobreCarbon
import java.util.Locale

private const val DURACION_CRONO_MS = 7_000
private const val FIN_LISTO = 0.2f
private const val FIN_TRAMO = 0.8f
private const val TIEMPO_FINAL_DECIMAS = 834
private const val DECIMAS_POR_SEGUNDO = 10
private const val SEGUNDOS_POR_MINUTO = 60

/** Tiempo del crono con coma decimal: «01:23,4» a partir de las décimas de segundo. */
internal fun tiempoDeIlustracion(decimas: Int): String {
    val segundosTotales = decimas / DECIMAS_POR_SEGUNDO
    val minutos = segundosTotales / SEGUNDOS_POR_MINUTO
    val segundos = segundosTotales % SEGUNDOS_POR_MINUTO
    return String.format(Locale.ROOT, "%02d:%02d,%d", minutos, segundos, decimas % DECIMAS_POR_SEGUNDO)
}

/**
 * Página 3: LISTO, la salida y la meta. Una barra de progreso del tramo se llena mientras el crono cuenta; al llegar
 * a meta sale el delta en verde (▼, ganas tiempo).
 */
@Composable
internal fun IlustracionCrono() {
    val progreso = rememberProgreso(DURACION_CRONO_MS)
    val t = progreso.value
    val fase =
        if (t < FIN_LISTO) {
            0
        } else if (t < FIN_TRAMO) {
            1
        } else {
            2
        }
    val decimas = (tramoDe(t, FIN_LISTO, FIN_TRAMO) * TIEMPO_FINAL_DECIMAS).toInt()
    PanelIlustracion(alto = 190.dp, modifier = Modifier.clearAndSetSemantics {}) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Espacio.l),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Etapa(stringResource(R.string.tutorial_crono_listo), fase == 0)
                Etapa(stringResource(R.string.tutorial_crono_salida), fase == 1)
                Etapa(stringResource(R.string.tutorial_crono_meta), fase == 2)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tiempoDeIlustracion(decimas),
                    style = CifraMedia,
                    color = if (fase == 0) TextoSecundarioSobreCarbon else TextoSobreCarbon,
                    modifier = Modifier.weight(1f),
                )
                if (fase == 2) {
                    Text(
                        text = stringResource(R.string.tutorial_crono_delta),
                        style = CifraMedia.copy(fontSize = CifraDelta),
                        color = RallyOscuro.deltaMejor,
                    )
                }
            }
            BarraTramo(tramoDe(t, FIN_LISTO, FIN_TRAMO))
        }
    }
}

private val CifraDelta = CifraMedia.fontSize * 0.6f

@Composable
private fun Etapa(
    texto: String,
    activa: Boolean,
) {
    Text(
        text = texto,
        style = EtiquetaIcono,
        color = if (activa) AmbarSobreCarbon else TextoSecundarioSobreCarbon,
    )
}

/** Barra de progreso del tramo con el coche como marcador. */
@Composable
private fun BarraTramo(avance: Float) {
    Canvas(Modifier.fillMaxWidth().height(18.dp)) {
        val alto = 8.dp.toPx()
        val y = (size.height - alto) / 2
        val radio = CornerRadius(alto / 2)
        drawRoundRect(Color.White.copy(alpha = 0.16f), Offset(0f, y), Size(size.width, alto), radio)
        drawRoundRect(AmbarSobreCarbon, Offset(0f, y), Size(size.width * avance, alto), radio)
        drawCircle(TextoSobreCarbon, 8.dp.toPx(), Offset(size.width * avance, size.height / 2))
    }
}
