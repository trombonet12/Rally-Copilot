package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.ajustes.ControlVolumen
import app.trombito.rallycopilot.ui.ajustes.ETIQUETA_NOTAS_SIMPLIFICADAS
import app.trombito.rallycopilot.ui.ajustes.FilaInterruptor
import app.trombito.rallycopilot.ui.ajustes.OpcionesAnticipacion
import app.trombito.rallycopilot.ui.ajustes.VistaPreviaNotasReducida
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.EstadoBotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.copiloto.AccionesCopiloto
import app.trombito.rallycopilot.ui.copiloto.CopilotoUiState
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_RAPIDOS_BOTON = "mapa-rapidos-boton"
const val ETIQUETA_RAPIDOS_PANEL = "mapa-rapidos-panel"

private const val OPACIDAD_PANEL = 0.94f
private val RADIO_PANEL = 28.dp
private val ANCHO_MAXIMO = 340.dp
private val ALTO_MAXIMO = 420.dp

/** Botón de deslizadores del panel de conducción: abre la hoja de ajustes rápidos sobre el mapa. */
@Composable
internal fun BotonAjustesRapidos(
    abierto: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BotonIcono(
        descripcion = stringResource(R.string.rapidos_boton),
        onClick = onClick,
        modifier = modifier.testTag(ETIQUETA_RAPIDOS_BOTON),
        estado = EstadoBotonIcono(seleccionado = abierto),
    ) { Icon(Iconos.Deslizadores, contentDescription = null) }
}

/**
 * Hoja pequeña de ajustes rápidos, pegada a su botón: la vista previa reducida de la nota, la anticipación (5 niveles),
 * las notas simplificadas y el volumen de la voz. Cada cambio se guarda y vale al momento, sin cerrar la hoja ni parar
 * la conducción.
 */
@Composable
internal fun PanelAjustesRapidos(
    estado: CopilotoUiState,
    acciones: AccionesCopiloto,
    modifier: Modifier = Modifier,
) {
    val descripcion = stringResource(R.string.rapidos_panel)
    Surface(
        shape = RoundedCornerShape(RADIO_PANEL),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(OPACIDAD_PANEL),
        modifier =
            modifier
                .widthIn(max = ANCHO_MAXIMO)
                .testTag(ETIQUETA_RAPIDOS_PANEL)
                .semantics { contentDescription = descripcion },
    ) {
        Column(
            modifier =
                Modifier
                    .heightIn(max = ALTO_MAXIMO)
                    .verticalScroll(rememberScrollState())
                    .padding(Espacio.m),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            VistaPreviaNotasReducida(
                anticipacion = estado.anticipacion,
                simplificadas = estado.notasSimplificadas,
                onEscuchar = acciones.onEscucharEjemplo,
            )
            OpcionesAnticipacion(estado.anticipacion, acciones.onAnticipacion)
            FilaInterruptor(
                titulo = stringResource(R.string.ajustes_notas_simplificadas),
                marcado = estado.notasSimplificadas,
                onCambio = acciones.onNotasSimplificadas,
                etiqueta = ETIQUETA_NOTAS_SIMPLIFICADAS,
            )
            ControlVolumen(estado.volumenVoz, acciones.onVolumenVoz)
        }
    }
}
