package app.trombito.rallycopilot.ui.mapasoffline

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Mapas offline. Al pulsar Descargar se pide el permiso de notificaciones (API 33 o superior) para ver el progreso;
 * si se deniega, la descarga empieza igualmente.
 */
@Composable
fun MapasOfflineRoute(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapasOfflineViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val lanzador =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onDescargar() }
    val descargar = {
        val notificaciones = contexto.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        val faltaPermiso =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && notificaciones != PackageManager.PERMISSION_GRANTED
        if (faltaPermiso) lanzador.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.onDescargar()
    }
    MapasOfflineScreen(
        estado = estado,
        acciones =
            AccionesMapasOffline(
                onVolver = onVolver,
                onDescargar = descargar,
                onCancelar = viewModel::onCancelar,
                onBorrar = viewModel::onBorrar,
                onSoloWifi = viewModel::onSoloWifi,
                onLimiteGb = viewModel::onLimiteGb,
            ),
        modifier = modifier,
    )
}
