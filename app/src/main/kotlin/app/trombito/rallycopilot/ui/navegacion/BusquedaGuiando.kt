package app.trombito.rallycopilot.ui.navegacion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_BUSQUEDA_GUIANDO = "nav-busqueda-guiando"
const val ETIQUETA_BUSQUEDA_GUIANDO_CERRAR = "nav-busqueda-guiando-cerrar"

/**
 * Búsqueda encima del mapa mientras se guía (la abre la lupa del panel): la píldora del campo con una sola ✕ que la
 * cierra y, debajo, la lista de resultados, cada uno con «Añadir parada» e «Ir aquí» (cambia el destino y conserva las
 * paradas pendientes). Mismo aspecto que la barra «¿A dónde?» del modo libre. El atrás del sistema también la cierra.
 */
@Composable
fun BusquedaGuiando(
    estado: NavegacionUiState,
    acciones: AccionesNavegacion,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
    onFoco: (Boolean) -> Unit = {},
) {
    BackHandler(onBack = acciones.onCerrarBusqueda)
    val descripcion = stringResource(R.string.navegacion_ui_buscar_guiando_panel)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(ETIQUETA_BUSQUEDA_GUIANDO)
                .semantics { contentDescription = descripcion },
        verticalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        BarraDestino(
            texto = estado.texto,
            onTexto = acciones.onTexto,
            onLimpiar = { acciones.onTexto("") },
            modifier = Modifier.fillMaxWidth(),
            onFoco = onFoco,
            onCerrar = acciones.onCerrarBusqueda,
        )
        ListaDestinos(
            busqueda = estado.busqueda,
            onElegir = acciones.onElegirDestino,
            horizontal = horizontal,
            anadirParada = acciones.onAnadirParada.takeUnless { estado.paradasLlenas },
        )
    }
}
