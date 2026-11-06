package app.trombito.rallycopilot.ui.mapa

import androidx.compose.runtime.Composable
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

/** Menú desplegado desde la columna de botones: solo uno a la vez. */
internal enum class MenuMapa { NINGUNO, CAPAS, COPILOTO }

internal class MenuAbierto(
    val actual: MenuMapa,
    val cambiar: (MenuMapa) -> Unit,
) {
    /** Abre [menu], o lo cierra si ya estaba abierto. */
    fun alternar(menu: MenuMapa) = cambiar(if (actual == menu) MenuMapa.NINGUNO else menu)
}

/**
 * La hoja del copiloto, si está abierta (el panel de capas va junto a su botón, en [ColumnaBotonesMapa]). Es una hoja
 * modal a todo el ancho: se cierra al tocar fuera, al arrastrarla o con atrás.
 */
@Composable
internal fun MenuSobreMapa(
    copiloto: CopilotoEnMapa,
    menu: MenuAbierto,
    horizontal: Boolean,
) {
    if (menu.actual == MenuMapa.COPILOTO) {
        PanelAjustesRapidos(
            estado = copiloto.estado,
            acciones = copiloto.acciones,
            onCerrar = { menu.cambiar(MenuMapa.NINGUNO) },
            horizontal = horizontal,
        )
    }
}
