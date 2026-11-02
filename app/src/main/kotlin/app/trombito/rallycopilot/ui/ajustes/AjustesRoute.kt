package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Une el ViewModel de ajustes con la pantalla sin lógica. */
@Composable
fun AjustesRoute(
    onMapasOffline: () -> Unit,
    onAcercaDe: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AjustesViewModel = hiltViewModel(),
) {
    val ajustes by viewModel.uiState.collectAsStateWithLifecycle()
    AjustesScreen(
        ajustes = ajustes,
        acciones =
            AccionesAjustes(
                onVolver = onVolver,
                onMapasOffline = onMapasOffline,
                onAcercaDe = onAcercaDe,
                onNivelZoom = viewModel::onNivelZoom,
                onIcono = viewModel::onIcono,
                onOrientacion = viewModel::onOrientacion,
                onEstiloManiobras = viewModel::onEstiloManiobras,
                onAnticipacion = viewModel::onAnticipacion,
                onNotasSimplificadas = viewModel::onNotasSimplificadas,
                onModoCopiloto = viewModel::onModoCopiloto,
                onVozCopiloto = viewModel::onVozCopiloto,
                onProbarVoz = viewModel::onProbarVoz,
                onActivarConCopiloto = viewModel::onActivarConCopiloto,
                onVolumenVoz = viewModel::onVolumenVoz,
                onEscucharEjemplo = viewModel::onEscucharEjemplo,
                onTema = viewModel::onTema,
                onCapa = viewModel::onCapa,
                onRelieve = viewModel::onRelieve,
            ),
        modifier = modifier,
    )
}
