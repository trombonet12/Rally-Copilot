package app.trombito.rallycopilot.ui.copiloto

import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto

/** Callbacks del copiloto en la pantalla de conducción. */
data class AccionesCopiloto(
    val onModo: (ModoCopiloto) -> Unit = {},
    val onElegirVoz: (VozCopiloto) -> Unit = {},
    val onProbarVoz: (VozCopiloto) -> Unit = {},
    val onPosponerVoz: () -> Unit = {},
    val onEscuchar: () -> Unit = {},
    val onAnticipacion: (NivelAnticipacion) -> Unit = {},
    val onNotasSimplificadas: (Boolean) -> Unit = {},
    val onVolumenVoz: (Int) -> Unit = {},
    val onEscucharEjemplo: () -> Unit = {},
    val onNotasGrandes: (Boolean) -> Unit = {},
    val onAvisarCruces: (Boolean) -> Unit = {},
    val onAvisarSalidasAutopista: (Boolean) -> Unit = {},
    val onAvisarRotondas: (Boolean) -> Unit = {},
    val onSalidaManual: (Boolean) -> Unit = {},
)

/** Estado y acciones del copiloto que la pantalla de mapa pinta: selector de modo y diálogo de voz. */
data class CopilotoEnMapa(
    val estado: CopilotoUiState = CopilotoUiState(),
    val acciones: AccionesCopiloto = AccionesCopiloto(),
)
