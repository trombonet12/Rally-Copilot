package app.trombito.rallycopilot.ui.mistramos

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trombito.rallycopilot.R

/** Conecta «Mis tramos» con su ViewModel. Al salir confirma el borrado que estuviera a la espera. */
@Composable
fun MisTramosRoute(
    onVolver: () -> Unit,
    onAbrirTramo: (String) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes titulo: Int = R.string.inicio_mis_tramos,
    viewModel: MisTramosViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) { onDispose { viewModel.onSalir() } }
    MisTramosScreen(
        estado = estado,
        acciones =
            AccionesMisTramos(
                onVolver = onVolver,
                onAbrir = { onAbrirTramo(it.id) },
                onFiltro = viewModel::onFiltro,
                onFavorito = viewModel::onFavorito,
                onBorrar = viewModel::onBorrar,
                onDeshacer = viewModel::onDeshacer,
                onBorradoConfirmado = viewModel::onBorradoConfirmado,
                onPedirRenombrar = viewModel::onPedirRenombrar,
                onRenombrar = viewModel::onRenombrar,
                onCancelarRenombrar = viewModel::onCancelarRenombrar,
                onFalloVisto = viewModel::onFalloVisto,
            ),
        modifier = modifier,
        titulo = titulo,
    )
}
