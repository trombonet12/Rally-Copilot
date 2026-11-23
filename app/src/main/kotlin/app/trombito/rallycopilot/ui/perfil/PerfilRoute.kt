package app.trombito.rallycopilot.ui.perfil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Conecta el perfil del piloto con su ViewModel. [onVerViaje] abre el resumen del viaje con ese id; [onVerViajes] y
 * [onVerCarreteras] abren «Mis tramos» (viajes) y «Mis carreteras».
 */
@Suppress("LongParameterList") // cuatro destinos de navegación, el modificador y el ViewModel inyectado
@Composable
fun PerfilRoute(
    onVolver: () -> Unit,
    onVerViaje: (recorridoId: Long) -> Unit,
    onVerViajes: () -> Unit,
    onVerCarreteras: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PerfilViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    PerfilScreen(
        estado = estado,
        acciones =
            AccionesPerfil(
                onVolver = onVolver,
                onVerViaje = onVerViaje,
                onVerViajes = onVerViajes,
                onVerCarreteras = onVerCarreteras,
            ),
        modifier = modifier,
    )
}
