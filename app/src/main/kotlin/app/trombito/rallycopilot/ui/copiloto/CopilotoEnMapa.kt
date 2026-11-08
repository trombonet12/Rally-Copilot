package app.trombito.rallycopilot.ui.copiloto

import app.trombito.rallycopilot.domain.voz.VelocidadHabla
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.ajustes.EstadoVozDescarga

/** Callbacks del copiloto en la pantalla de conducción. */
data class AccionesCopiloto(
    val onModo: (ModoCopiloto) -> Unit = {},
    val onElegirVoz: (VozCopiloto) -> Unit = {},
    val onProbarVoz: (VozCopiloto) -> Unit = {},
    val onDescargarVoz: (VozCopiloto) -> Unit = {},
    val onPosponerVoz: () -> Unit = {},
    val onEscuchar: () -> Unit = {},
    val onAnticipacion: (NivelAnticipacion) -> Unit = {},
    val onNotasSimplificadas: (Boolean) -> Unit = {},
    val onVolumenVoz: (Int) -> Unit = {},
    val onEscucharEjemplo: () -> Unit = {},
    val onNotasGrandes: (Boolean) -> Unit = {},
    val onAvisarCruces: (Boolean) -> Unit = {},
    val onAvisarPuentes: (Boolean) -> Unit = {},
    val onAvisarSalidasAutopista: (Boolean) -> Unit = {},
    val onAvisarRotondas: (Boolean) -> Unit = {},
    val onAvisarRadares: (Boolean) -> Unit = {},
    val onAvisarIncidencias: (Boolean) -> Unit = {},
    val onAvisarVelocidad: (Boolean) -> Unit = {},
    val onVelocidadHabla: (VelocidadHabla) -> Unit = {},
    val onSalidaManual: (Boolean) -> Unit = {},
)

/**
 * Estado y acciones del copiloto que la pantalla de mapa pinta: selector de modo y diálogo de voz. [vozSharvard] es
 * cómo está la descarga de Sharvard (por defecto, instalada: el diálogo no enseña nada de descarga).
 */
data class CopilotoEnMapa(
    val estado: CopilotoUiState = CopilotoUiState(),
    val acciones: AccionesCopiloto = AccionesCopiloto(),
    val vozSharvard: EstadoVozDescarga = EstadoVozDescarga(),
)
