package app.trombito.rallycopilot.ui.navegacion

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.ui.maniobras.VarianteManiobra

/** Callbacks de la navegación sobre el mapa. */
data class AccionesNavegacion(
    val onTexto: (String) -> Unit = {},
    val onElegirDestino: (Destino) -> Unit = {},
    /** Borra el campo o, con una vista previa abierta, la cancela. */
    val onCancelar: () -> Unit = {},
    val onPerfil: (PerfilRuta) -> Unit = {},
    val onEmpezar: () -> Unit = {},
    val onTerminar: () -> Unit = {},
)

/**
 * Hueco del panel de guiado (banner de maniobra arriba y pie de guiado abajo). Se pinta dentro de la capa de
 * superposición del mapa (el `Box` a pantalla completa de la zona del mapa, ya con los insets del sistema y 8 dp de
 * margen), solo mientras el estado es [EstadoNavegacion.Guiando]; el segundo parámetro dice si la pantalla es
 * apaisada. Los botones están en el panel negro inferior, así que la franja superior es toda del banner.
 */
typealias PanelGuiado = @Composable BoxScope.(guiando: EstadoNavegacion.Guiando, horizontal: Boolean) -> Unit

/** Hueco vacío: no pinta nada. */
val SinPanelGuiado: PanelGuiado = { _, _ -> }

/**
 * El panel de guiado real: banner arriba a todo el ancho y pie abajo. [variante] es el estilo de los iconos de
 * maniobra elegido en Ajustes (por defecto, la placa inclinada).
 */
fun panelGuiadoMapa(variante: VarianteManiobra = VarianteManiobra.PLACA_INCLINADA): PanelGuiado =
    { guiando, _ ->
        app.trombito.rallycopilot.ui.navegacion.guiado.PanelGuiado(
            estado = guiando,
            variante = variante,
            modifier = Modifier.fillMaxSize(),
        )
    }

/** Lo que [app.trombito.rallycopilot.ui.mapa.MapaScreen] necesita de la navegación, agrupado. */
data class NavegacionEnMapa(
    val estado: NavegacionUiState = NavegacionUiState(),
    val acciones: AccionesNavegacion = AccionesNavegacion(),
    val panelGuiado: PanelGuiado = SinPanelGuiado,
)
