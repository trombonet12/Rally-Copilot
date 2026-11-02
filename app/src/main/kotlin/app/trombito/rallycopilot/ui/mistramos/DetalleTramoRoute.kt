package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Conecta el detalle de un tramo con su ViewModel; «Ver análisis» entrega el número de la pasada. */
@Composable
fun DetalleTramoRoute(
    onVolver: () -> Unit,
    onVerAnalisis: (tramoId: String, pasadaId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetalleTramoViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    DetalleTramoScreen(
        estado = estado,
        acciones =
            AccionesDetalleTramo(
                onVolver = onVolver,
                onFavorito = viewModel::onFavorito,
                onPedirRenombrar = viewModel::onPedirRenombrar,
                onRenombrar = viewModel::onRenombrar,
                onCancelarRenombrar = viewModel::onCancelarRenombrar,
                onVerAnalisis = { pasadaId -> estado.tramo?.let { onVerAnalisis(it.id, pasadaId) } },
            ),
        modifier = modifier,
    )
}
