package app.trombito.rallycopilot.ui.mapa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Une el ViewModel, el permiso de ubicación y el mapa real con la pantalla sin lógica. */
@Composable
fun MapaRoute(
    onVolver: () -> Unit,
    onAbrirMapasOffline: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapaViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val permiso = rememberPermisoUbicacion()
    var siguiendo by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(permiso.estado) { viewModel.onPermisoUbicacion(permiso.estado == EstadoPermisoUbicacion.CONCEDIDO) }

    MapaScreen(
        estado = estado,
        vista = EstadoVistaMapa(permiso = permiso.estado, siguiendo = siguiendo),
        acciones =
            AccionesMapa(
                onVolver = onVolver,
                onCapa = viewModel::onCapa,
                onRecentrar = { siguiendo = true },
                onPedirPermiso = permiso.pedir,
                onAbrirAjustes = permiso.abrirAjustes,
                onDescartarAviso = viewModel::onDescartarAviso,
                onAbrirMapasOffline = onAbrirMapasOffline,
            ),
        mapa = { estiloJson, modificador ->
            VistaMapa(
                estiloJson = estiloJson,
                posicion = estado.posicion,
                siguiendo = siguiendo,
                onSeguimientoPerdido = { siguiendo = false },
                modifier = modificador,
            )
        },
        modifier = modifier,
    )
}
