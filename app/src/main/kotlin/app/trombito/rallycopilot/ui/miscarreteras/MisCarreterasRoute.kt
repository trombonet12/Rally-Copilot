package app.trombito.rallycopilot.ui.miscarreteras

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Conecta «Mis carreteras» con su ViewModel. */
@Composable
fun MisCarreterasRoute(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MisCarreterasViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    MisCarreterasScreen(estado = estado, onVolver = onVolver, modifier = modifier)
}
