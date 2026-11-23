package app.trombito.rallycopilot.ui.resumen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Conecta el resumen de un viaje con su ViewModel y el mapa real. */
@Composable
fun ResumenViajeRoute(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResumenViajeViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    ResumenViajeScreen(estado = estado, onVolver = onVolver, mapa = MapaResumenReal, modifier = modifier)
}
