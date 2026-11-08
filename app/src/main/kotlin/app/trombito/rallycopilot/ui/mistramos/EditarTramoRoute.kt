package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Conecta la edición de notas y sectores de un tramo con su ViewModel. */
@Composable
fun EditarTramoRoute(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditarTramoViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    EditarTramoScreen(
        estado = estado,
        acciones =
            AccionesEditarTramo(
                onVolver = onVolver,
                onTocarNota = viewModel::onTocarNota,
                onSeleccionar = viewModel::onSeleccionar,
                onTocarPunto = viewModel::onTocarPunto,
                onMoverCorte = viewModel::onMoverCorte,
                onSoltarCorte = viewModel::onSoltarCorte,
                onMicrosectores = viewModel::onMicrosectores,
                onRestaurar = viewModel::onRestaurar,
                onAplicar = viewModel::onAplicar,
                onQuitar = viewModel::onQuitar,
                onFusionar = viewModel::onFusionar,
                onDividir = viewModel::onDividir,
                onAnadirAviso = viewModel::onAnadirAviso,
                onQuitarAviso = viewModel::onQuitarAviso,
                onCerrarEditor = viewModel::onCerrarEditor,
            ),
        modifier = modifier,
    )
}
