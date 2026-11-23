package app.trombito.rallycopilot.ui.miscarreteras

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Conecta la ficha de una carretera (`RutaFichaCarretera`) con su ViewModel. [onAbrirTramo] abre el detalle de un
 * tramo de vía (recibe el id del tramo, el de `RutaTramo`).
 */
@Composable
fun FichaCarreteraRoute(
    onVolver: () -> Unit,
    onAbrirTramo: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FichaCarreteraViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    FichaCarreteraScreen(
        estado = estado,
        acciones =
            AccionesFichaCarretera(
                onVolver = onVolver,
                onAbrirTramo = onAbrirTramo,
                onFranja = viewModel::onTramo,
                onModo = viewModel::onModo,
            ),
        modifier = modifier,
    )
}
