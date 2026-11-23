package app.trombito.rallycopilot.ui.mistramos

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trombito.rallycopilot.R
import kotlinx.coroutines.flow.MutableStateFlow

/** Conecta «Mis tramos» con su ViewModel. Al salir confirma el borrado que estuviera a la espera. */
@Composable
@Suppress("LongParameterList") // importar y dibujar son opcionales: sin ellos la pantalla no ofrece los botones
fun MisTramosRoute(
    onVolver: () -> Unit,
    onAbrirTramo: (String) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes titulo: Int = R.string.inicio_mis_tramos,
    viewModel: MisTramosViewModel = hiltViewModel(),
    importacion: ImportarViewModel? = null,
    onDibujar: (() -> Unit)? = null,
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val estadoImportacion by (importacion?.estado ?: SIN_IMPORTACION).collectAsStateWithLifecycle()
    // Selector de archivos del sistema (Storage Access Framework): sin permisos de almacenamiento.
    val selector =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importacion?.importar(uri)
        }
    val alImportar: (() -> Unit)? = importacion?.let { { selector.launch(arrayOf("*/*")) } }
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
                onImportar = alImportar,
                onDibujar = onDibujar,
                onImportacionVista = { importacion?.onVisto() },
                onConfirmarImportacion = { importacion?.confirmar() },
                onCancelarImportacion = { importacion?.cancelar() },
            ),
        modifier = modifier,
        titulo = titulo,
        importacion = estadoImportacion,
    )
}

private val SIN_IMPORTACION = MutableStateFlow<EstadoImportacion>(EstadoImportacion.Inactivo)
