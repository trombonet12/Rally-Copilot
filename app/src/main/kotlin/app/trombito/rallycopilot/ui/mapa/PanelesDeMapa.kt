package app.trombito.rallycopilot.ui.mapa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    /** Parado planificando: el panel se encoge para dejar más mapa. */
    val compacto: Boolean = false,
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
        compacto = panel.compacto,
        modifier = modifier,
    )
}

/** Por debajo de esta velocidad (km/h), planificando, el panel inferior se encoge. */
internal const val VELOCIDAD_COMPACTAR_KMH = 5

/** Por encima de esta velocidad (km/h) el panel compacto vuelve a su tamaño. */
internal const val VELOCIDAD_AMPLIAR_KMH = 10

/**
 * Si el panel inferior debe ser compacto, con histéresis: planificando se encoge con menos de
 * [VELOCIDAD_COMPACTAR_KMH] y no se amplía hasta pasar de [VELOCIDAD_AMPLIAR_KMH]; entre una y otra se queda como
 * estaba. Sin planificar (al empezar la ruta, guiando) siempre es el panel completo. Sin velocidad cuenta como parado.
 */
internal fun panelCompacto(
    previo: Boolean,
    velocidadKmh: Int?,
    planificando: Boolean,
): Boolean {
    if (!planificando) return false
    val v = velocidadKmh ?: 0
    return if (previo) v <= VELOCIDAD_AMPLIAR_KMH else v < VELOCIDAD_COMPACTAR_KMH
}

/** [panelCompacto] como estado de la composición: guarda el valor anterior para la histéresis. */
@Composable
internal fun rememberPanelCompacto(
    velocidadKmh: Int?,
    planificando: Boolean,
): Boolean {
    var compacto by remember { mutableStateOf(false) }
    val nuevo = panelCompacto(compacto, velocidadKmh, planificando)
    LaunchedEffect(nuevo) { compacto = nuevo }
    return compacto
}
