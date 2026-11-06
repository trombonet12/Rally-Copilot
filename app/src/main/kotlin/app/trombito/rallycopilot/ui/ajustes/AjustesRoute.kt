package app.trombito.rallycopilot.ui.ajustes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trombito.rallycopilot.ui.theme.Espacio

private const val TIPO_ZIP = "application/zip"

/** Algunos proveedores (descargas, Windows) dan al zip otro tipo; la validación del contenido hace de filtro. */
private val TIPOS_COPIA = arrayOf(TIPO_ZIP, "application/x-zip-compressed", "application/octet-stream")

/** Une el ViewModel de ajustes con la pantalla sin lógica; la copia usa el selector de archivos del sistema. */
@Composable
@Suppress("LongParameterList") // navegación, modificador y los dos ViewModel de Hilt
fun AjustesRoute(
    onMapasOffline: () -> Unit,
    onAcercaDe: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AjustesViewModel = hiltViewModel(),
    copia: CopiaViewModel = hiltViewModel(),
) {
    val ajustes by viewModel.uiState.collectAsStateWithLifecycle()
    val estadoCopia by copia.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val avisos = remember { SnackbarHostState() }
    var confirmando by remember { mutableStateOf(false) }
    val guardarCopia =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(TIPO_ZIP)) { uri ->
            if (uri != null) copia.exportar { contexto.contentResolver.openOutputStream(uri) }
        }
    val elegirCopia =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) copia.importar { contexto.contentResolver.openInputStream(uri) }
        }
    Box(modifier) {
        AjustesScreen(
            ajustes = ajustes,
            acciones =
                AccionesAjustes(
                    onVolver = onVolver,
                    onMapasOffline = onMapasOffline,
                    onAcercaDe = onAcercaDe,
                    onNivelZoom = viewModel::onNivelZoom,
                    onIcono = viewModel::onIcono,
                    onOrientacion = viewModel::onOrientacion,
                    onEstiloManiobras = viewModel::onEstiloManiobras,
                    onAnticipacion = viewModel::onAnticipacion,
                    onNotasSimplificadas = viewModel::onNotasSimplificadas,
                    onModoCopiloto = viewModel::onModoCopiloto,
                    onVozCopiloto = viewModel::onVozCopiloto,
                    onProbarVoz = viewModel::onProbarVoz,
                    onActivarConCopiloto = viewModel::onActivarConCopiloto,
                    onVolumenVoz = viewModel::onVolumenVoz,
                    onEscucharEjemplo = viewModel::onEscucharEjemplo,
                    onTema = viewModel::onTema,
                    onCapa = viewModel::onCapa,
                    onRelieve = viewModel::onRelieve,
                    onCronometrar = viewModel::onCronometrar,
                    onSalidaManual = viewModel::onSalidaManual,
                    onNotasGrandes = viewModel::onNotasGrandes,
                    onExportarCopia = { guardarCopia.launch(nombreArchivoCopia()) },
                    onImportarCopia = { confirmando = true },
                ),
            copiaEnCurso = estadoCopia is EstadoCopia.Trabajando || estadoCopia is EstadoCopia.Importada,
        )
        SnackbarHost(avisos, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(Espacio.l))
    }
    if (confirmando) {
        DialogoImportarCopia(
            onConfirmar = {
                confirmando = false
                elegirCopia.launch(TIPOS_COPIA)
            },
            onCancelar = { confirmando = false },
        )
    }
    AvisoCopia(estadoCopia, avisos, copia::avisoVisto)
}
