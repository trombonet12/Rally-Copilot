package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import app.trombito.rallycopilot.R

/** Avisa (snackbar) de que no se pudo preparar el archivo para compartir y da el aviso por visto. */
@Composable
internal fun AvisoCompartir(
    estado: EstadoCompartir,
    avisos: SnackbarHostState,
    onVisto: () -> Unit,
) {
    val mensaje = if (estado == EstadoCompartir.FALLO) stringResource(R.string.detalle_compartir_fallo) else null
    LaunchedEffect(estado) {
        if (mensaje != null) {
            avisos.showSnackbar(mensaje, duration = SnackbarDuration.Long)
            onVisto()
        }
    }
}
