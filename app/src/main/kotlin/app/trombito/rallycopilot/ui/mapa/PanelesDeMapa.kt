package app.trombito.rallycopilot.ui.mapa

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.trombito.rallycopilot.ui.alertas.AlertasUiState
import app.trombito.rallycopilot.ui.copiloto.CopilotoEnMapa
import app.trombito.rallycopilot.ui.copiloto.DialogoVozCopiloto

/** El diálogo para elegir la voz del copiloto en el primer uso. */
@Composable
internal fun DialogoVozSiToca(copiloto: CopilotoEnMapa) {
    if (!copiloto.estado.pedirVoz) return
    DialogoVozCopiloto(
        onElegir = copiloto.acciones.onElegirVoz,
        onProbar = copiloto.acciones.onProbarVoz,
        onPosponer = copiloto.acciones.onPosponerVoz,
    )
}

/** Lo que lleva el panel de velocidad: datos de conducción, botones y alertas; [enColumna] es el panel lateral. */
internal class PanelEnMapa(
    val estado: MapaUiState,
    val botones: BotonesPanel,
    val alertas: AlertasUiState,
    val enColumna: Boolean,
)

@Composable
internal fun PanelDeVelocidad(
    panel: PanelEnMapa,
    modifier: Modifier,
) {
    PanelVelocidad(
        velocidadKmh = panel.estado.velocidadKmh,
        limiteKmh = panel.estado.limiteKmh,
        via = panel.estado.via,
        enColumna = panel.enColumna,
        botonesIzquierda = panel.botones.izquierda,
        botonesDerecha = panel.botones.derecha,
        alertas = panel.alertas.panel,
        modifier = modifier,
    )
}
