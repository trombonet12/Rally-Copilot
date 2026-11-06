package app.trombito.rallycopilot.ui.inicio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Conecta el inicio con su ViewModel (la última salida). */
@Composable
fun InicioRoute(
    onEntrada: (EntradaInicio) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InicioViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    InicioScreen(modifier = modifier, estado = estado, onEntrada = onEntrada)
}
