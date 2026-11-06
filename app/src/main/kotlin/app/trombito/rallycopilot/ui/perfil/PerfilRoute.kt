package app.trombito.rallycopilot.ui.perfil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Conecta el perfil del piloto con su ViewModel; tocar una pasada entrega su tramo y su número para el análisis. */
@Composable
fun PerfilRoute(
    onVolver: () -> Unit,
    onVerAnalisis: (tramoId: String, pasadaId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PerfilViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    PerfilScreen(
        estado = estado,
        acciones =
            AccionesPerfil(
                onVolver = onVolver,
                onSeleccionar = viewModel::onSeleccionar,
                onVerAnalisis = onVerAnalisis,
            ),
        modifier = modifier,
    )
}
