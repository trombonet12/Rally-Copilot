package app.trombito.rallycopilot.ui.edicion

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.trombito.rallycopilot.ui.copiloto.BotonMicrofono
import app.trombito.rallycopilot.ui.theme.Espacio

/**
 * Lo que se superpone al mapa para editar notas: el editor de la nota tocada (solo con el coche parado) y, en
 * reconocimiento, un botón de micrófono grande para dictar sin apartar la vista (en el borde derecho, a media altura).
 */
@Composable
fun BoxScope.EdicionEnMapa(
    estado: EdicionMapaUiState,
    acciones: AccionesEdicionMapa,
    reconocimiento: Boolean,
    escuchando: Boolean,
    onEscuchar: () -> Unit,
) {
    if (reconocimiento) {
        BotonMicrofono(
            escuchando = escuchando,
            onEscuchar = onEscuchar,
            grande = true,
            modifier = Modifier.align(Alignment.CenterEnd).padding(Espacio.l),
        )
    }
    estado.editor?.let { editor ->
        EditorNota(
            inicial = editor.nota,
            simplificadas = editor.simplificadas,
            onAplicar = acciones.onAplicar,
            onQuitar = acciones.onQuitar,
            onCerrar = acciones.onCerrar,
        )
    }
}
