package app.trombito.rallycopilot.ui.navegacion

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.ui.alertas.AlertasUiState
import app.trombito.rallycopilot.ui.navegacion.notas.NotasUiState

/** Callbacks de la navegación sobre el mapa. */
data class AccionesNavegacion(
    val onTexto: (String) -> Unit = {},
    val onElegirDestino: (Destino) -> Unit = {},
    /** Borra el campo o, con una vista previa abierta, la cancela. */
    val onCancelar: () -> Unit = {},
    val onPerfil: (PerfilRuta) -> Unit = {},
    val onEmpezar: () -> Unit = {},
    val onTerminar: () -> Unit = {},
    /** Toca «Seguir a <destino>»: recalcula desde la posición actual y empieza el guiado. */
    val onSeguir: () -> Unit = {},
    /** Descarta «Seguir a <destino>» (borra el guiado pendiente). */
    val onDescartarSeguir: () -> Unit = {},
    /** «Ir aquí» de la tarjeta del punto: abre la vista previa de rutas hasta él. */
    val onIrAqui: () -> Unit = {},
    /** «Añadir parada» de la tarjeta del punto. */
    val onAnadirParadaDelPunto: () -> Unit = {},
    /** La lupa del panel (solo guiando): abre la búsqueda encima del mapa. */
    val onAbrirBusqueda: () -> Unit = {},
    /** Cierra esa búsqueda (X o atrás). */
    val onCerrarBusqueda: () -> Unit = {},
    /** Cierra la tarjeta del punto (X o toque fuera). */
    val onCerrarPunto: () -> Unit = {},
    /** «Añadir como parada» en un resultado de la búsqueda. */
    val onAnadirParada: (Destino) -> Unit = {},
    val onQuitarParada: (Int) -> Unit = {},
    /** Mueve la parada de la posición `desde` a la posición `hasta`. */
    val onMoverParada: (Int, Int) -> Unit = { _, _ -> },
)

/**
 * Hueco del panel de guiado (banner de maniobra arriba y pie de guiado abajo). Se pinta dentro de la capa de
 * superposición del mapa (el `Box` a pantalla completa de la zona del mapa, ya con los insets del sistema y 8 dp de
 * margen), solo mientras el estado es [EstadoNavegacion.Guiando]; el segundo parámetro dice si la pantalla es
 * apaisada, el tercero trae las notas que van en la franja bajo el banner y el cuarto las alertas (banner de radar o
 * incidencia bajo la franja) y el quinto el cronómetro (HUD del tramo conocido o tarjeta de meta, bajo todo lo
 * anterior). Los botones están en el panel negro inferior, así que la franja superior es toda del banner.
 */
typealias PanelGuiado =
    @Composable BoxScope.(
        guiando: EstadoNavegacion.Guiando,
        horizontal: Boolean,
        notas: NotasUiState,
        alertas: AlertasUiState,
        crono: CronoEnCurso?,
    ) -> Unit

/** Hueco vacío: no pinta nada. */
val SinPanelGuiado: PanelGuiado = { _, _, _, _, _ -> }

/** El panel de guiado real: banner arriba a todo el ancho, carrete de notas debajo y pie abajo. */
fun panelGuiadoMapa(): PanelGuiado =
    { guiando, _, notas, alertas, crono ->
        app.trombito.rallycopilot.ui.navegacion.guiado.PanelGuiado(
            estado = guiando,
            modifier = Modifier.fillMaxSize(),
            notas = notas,
            alertas = alertas,
            crono = crono,
        )
    }

/** Lo que [app.trombito.rallycopilot.ui.mapa.MapaScreen] necesita de la navegación, agrupado. */
data class NavegacionEnMapa(
    val estado: NavegacionUiState = NavegacionUiState(),
    val acciones: AccionesNavegacion = AccionesNavegacion(),
    val panelGuiado: PanelGuiado = SinPanelGuiado,
    /** Notas de la franja: bajo el banner al guiar y arriba del todo en modo libre. */
    val notas: NotasUiState = NotasUiState(),
)
