package app.trombito.rallycopilot.ui.perfil

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.io.File

/** Abre el menú Compartir con el [archivo]; false si el sistema no pudo (sin app que lo reciba o ruta no servida). */
private fun abrirCompartir(
    contexto: Context,
    archivo: File,
): Boolean =
    try {
        compartirPoster(contexto, archivo)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

/**
 * Conecta el perfil del piloto con su ViewModel. [onVerViaje] abre el resumen del viaje con ese id; [onVerViajes] y
 * [onVerCarreteras] abren «Mis tramos» (viajes) y «Mis carreteras». Compartir el resumen del mes dibuja la imagen fuera
 * del hilo principal y abre el menú Compartir de Android.
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
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var falloCompartir by remember { mutableStateOf(false) }
    PerfilScreen(
        estado = estado,
        acciones =
            AccionesPerfil(
                onVolver = onVolver,
                onVerViaje = onVerViaje,
                onVerViajes = onVerViajes,
                onVerCarreteras = onVerCarreteras,
                onCompartirMes = { resumen ->
                    alcance.launch {
                        val archivo = viewModel.escribirPosterMes(contexto, resumen)
                        falloCompartir = archivo == null || !abrirCompartir(contexto, archivo)
                    }
                },
            ),
        modifier = modifier,
        falloCompartir = falloCompartir,
    )
}
