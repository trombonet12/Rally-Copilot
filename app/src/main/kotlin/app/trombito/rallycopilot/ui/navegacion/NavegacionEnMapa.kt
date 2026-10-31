package app.trombito.rallycopilot.ui.navegacion

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
 * apaisada. Arriba a la izquierda queda el botón de volver (48 dp) y arriba a la derecha la columna de controles
 * (48 dp): conviene dejar ~56 dp libres a cada lado en la franja superior.
 */
typealias PanelGuiado = @Composable BoxScope.(guiando: EstadoNavegacion.Guiando, horizontal: Boolean) -> Unit

/** Hueco vacío: no pinta nada. */
val SinPanelGuiado: PanelGuiado = { _, _ -> }

/** Margen lateral del panel: deja libres el botón de volver (izquierda) y la columna de controles (derecha). */
private val MARGEN_LATERAL_PANEL = 48.dp

/**
 * El panel de guiado real: banner arriba entre los botones laterales y pie abajo a todo el ancho. [variante] es el
 * estilo de los iconos de maniobra elegido en Ajustes (por defecto, la placa inclinada).
 */
fun panelGuiadoMapa(variante: VarianteManiobra = VarianteManiobra.PLACA_INCLINADA): PanelGuiado =
    { guiando, _ ->
        app.trombito.rallycopilot.ui.navegacion.guiado.PanelGuiado(
            estado = guiando,
            variante = variante,
            modifier = Modifier.fillMaxSize(),
            margenLateralBanner = MARGEN_LATERAL_PANEL,
        )
    }

/** Lo que [app.trombito.rallycopilot.ui.mapa.MapaScreen] necesita de la navegación, agrupado. */
data class NavegacionEnMapa(
    val estado: NavegacionUiState = NavegacionUiState(),
    val acciones: AccionesNavegacion = AccionesNavegacion(),
    val panelGuiado: PanelGuiado = SinPanelGuiado,
)
