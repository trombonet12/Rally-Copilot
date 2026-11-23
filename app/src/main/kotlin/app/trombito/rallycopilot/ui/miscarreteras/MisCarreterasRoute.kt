package app.trombito.rallycopilot.ui.miscarreteras

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Conecta «Mis carreteras» con su ViewModel. [onFicha] abre la ficha de la carretera tocada (recibe el id de
 * `FichaCarretera`, el `ref` de `RutaFichaCarretera`).
 */
@Composable
fun MisCarreterasRoute(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    onFicha: (String) -> Unit = {},
    viewModel: MisCarreterasViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    MisCarreterasScreen(
        estado = estado,
        acciones =
            AccionesMisCarreteras(
                onVolver = onVolver,
                onFicha = onFicha,
                onOrden = viewModel::onOrden,
                onModo = viewModel::onModo,
            ),
        modifier = modifier,
    )
}
