package app.trombito.rallycopilot.ui.navegacion.notas

import androidx.compose.runtime.Immutable
import app.trombito.rallycopilot.motor.notas.NotaVisible

/**
 * Lo que enseña la franja de notas: los próximos elementos de la hoja (el primero se pinta grande) y si las notas van
 * simplificadas (CERRADA / MEDIA / SUAVE en vez del grado). Vacío = no hay franja. [enPausa]: el copiloto no canta
 * notas nuevas porque no se fía de la posición; la franja lo indica con un rótulo discreto.
 */
@Immutable
data class NotasUiState(
    val visibles: List<NotaVisible> = emptyList(),
    val simplificadas: Boolean = false,
    val enPausa: Boolean = false,
)
