package app.trombito.rallycopilot.ui.analisis

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Conecta el análisis de una pasada con su ViewModel y el mapa real. */
@Composable
fun AnalisisRoute(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnalisisViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    AnalisisScreen(
        estado = estado,
        acciones =
            AccionesAnalisis(
                onVolver = onVolver,
                onComparar = viewModel::onComparar,
                onAlternarRepeticion = viewModel::onAlternarRepeticion,
                onVelocidad = viewModel::onVelocidad,
                onPosicion = viewModel::onPosicion,
                onCursor = viewModel::onCursor,
            ),
        mapa = MapaAnalisisReal,
        modifier = modifier,
    )
}
