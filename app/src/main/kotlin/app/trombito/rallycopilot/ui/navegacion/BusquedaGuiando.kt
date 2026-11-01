package app.trombito.rallycopilot.ui.navegacion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_BUSQUEDA_GUIANDO = "nav-busqueda-guiando"
const val ETIQUETA_BUSQUEDA_GUIANDO_CERRAR = "nav-busqueda-guiando-cerrar"

private const val OPACIDAD_SOBRE_MAPA = 0.96f

/**
 * Búsqueda encima del mapa mientras se guía (la abre la lupa del panel): el campo con una X que la cierra y la lista de
 * resultados, cada uno con «Añadir parada» e «Ir aquí» (cambia el destino y conserva las paradas pendientes). El atrás
 * del sistema también la cierra.
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
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.background.copy(OPACIDAD_SOBRE_MAPA),
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(ETIQUETA_BUSQUEDA_GUIANDO)
                .semantics { contentDescription = descripcion },
    ) {
        Column(Modifier.padding(Espacio.s), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espacio.s),
            ) {
                // Borrar el texto no cierra la búsqueda: solo la X de al lado (o atrás) lo hace.
                BarraDestino(
                    texto = estado.texto,
                    onTexto = acciones.onTexto,
                    onLimpiar = { acciones.onTexto("") },
                    modifier = Modifier.weight(1f),
                    onFoco = onFoco,
                )
                BotonIcono(
                    descripcion = stringResource(R.string.navegacion_ui_buscar_guiando_cerrar),
                    onClick = acciones.onCerrarBusqueda,
                    modifier = Modifier.testTag(ETIQUETA_BUSQUEDA_GUIANDO_CERRAR),
                ) { Icon(Iconos.Cerrar, contentDescription = null) }
            }
            ListaDestinos(
                busqueda = estado.busqueda,
                onElegir = acciones.onElegirDestino,
                horizontal = horizontal,
                anadirParada = acciones.onAnadirParada.takeUnless { estado.paradasLlenas },
                irAqui = true,
            )
        }
    }
}
