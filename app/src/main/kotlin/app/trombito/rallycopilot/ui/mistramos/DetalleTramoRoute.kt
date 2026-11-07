package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trombito.rallycopilot.R

/** Conecta el detalle de un tramo con su ViewModel; «Ver análisis» entrega el número de la pasada. */
@Composable
@Suppress("LongParameterList") // un callback por destino que abre el detalle
fun DetalleTramoRoute(
    onVolver: () -> Unit,
    onVerAnalisis: (tramoId: String, pasadaId: Long) -> Unit,
    onEditarNotas: (tramoId: String) -> Unit,
    onReconocer: (tramoId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetalleTramoViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val tituloCompartir = stringResource(R.string.detalle_compartir_selector)
    LaunchedEffect(viewModel, contexto) {
        viewModel.archivosParaCompartir.collect { archivo ->
            if (!compartirTramo(contexto, archivo, tituloCompartir)) viewModel.onFalloAlCompartir()
        }
    }
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
                onEditarNotas = { estado.tramo?.let { onEditarNotas(it.id) } },
                onReconocer = { estado.tramo?.let { onReconocer(it.id) } },
                onCompartir = viewModel::onCompartir,
                onCompartirVisto = viewModel::onCompartirVisto,
            ),
        modifier = modifier,
        mapa = MapaTramoReal,
    )
}
