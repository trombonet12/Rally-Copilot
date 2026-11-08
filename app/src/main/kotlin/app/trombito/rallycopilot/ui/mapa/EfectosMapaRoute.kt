package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.ajustes.EstadoVozDescarga
import app.trombito.rallycopilot.ui.alertas.AlertasUiState
import app.trombito.rallycopilot.ui.alertas.AlertasViewModel
import app.trombito.rallycopilot.ui.comun.esperarPorFotogramas
import app.trombito.rallycopilot.ui.copiloto.AccionesCopiloto
import app.trombito.rallycopilot.ui.copiloto.CopilotoEnMapa
import app.trombito.rallycopilot.ui.copiloto.CopilotoViewModel
import app.trombito.rallycopilot.ui.edicion.AccionesEdicionMapa
import app.trombito.rallycopilot.ui.edicion.EdicionEnMapa
import app.trombito.rallycopilot.ui.edicion.EdicionMapaUiState
import app.trombito.rallycopilot.ui.edicion.EdicionMapaViewModel
import app.trombito.rallycopilot.ui.navegacion.AccionesNavegacion
import app.trombito.rallycopilot.ui.navegacion.NavegacionEnMapa
import app.trombito.rallycopilot.ui.navegacion.NavegacionUiState
import app.trombito.rallycopilot.ui.navegacion.NavegacionViewModel
import app.trombito.rallycopilot.ui.navegacion.PanelGuiado
import app.trombito.rallycopilot.ui.navegacion.notas.NotasViewModel
import app.trombito.rallycopilot.ui.navegacion.panelGuiadoMapa
import app.trombito.rallycopilot.ui.theme.LocalTemaOscuro
import app.trombito.rallycopilot.ui.tramos.AccionesSalidaCrono
import app.trombito.rallycopilot.ui.tramos.CronoViewModel
import app.trombito.rallycopilot.ui.tramos.CuentaAtrasPorVoz
import app.trombito.rallycopilot.ui.tramos.LocalAbrirAnalisis
import app.trombito.rallycopilot.ui.tramos.LocalAccionesSalidaCrono
import kotlin.math.floor

/**
 * Pasa el permiso de ubicación a los ViewModels. El modo libre arranca solo al abrir el mapa con permiso (o al
 * concederlo) y se para al salir (onCleared).
 */
@Composable
internal fun InformarDelPermiso(
    estado: EstadoPermisoUbicacion,
    viewModel: MapaViewModel,
    navegacionViewModel: NavegacionViewModel,
) {
    LaunchedEffect(estado) {
        val concedido = estado == EstadoPermisoUbicacion.CONCEDIDO
        viewModel.onPermisoUbicacion(concedido)
        navegacionViewModel.onPermisoUbicacion(concedido)
    }
}

/**
 * Durante la vista previa la cámara deja de seguir al coche (encuadra la ruta); al empezar o al cancelar vuelve al
 * seguimiento. No toca nada al entrar en la pantalla si no ha habido vista previa.
 */
@Composable
internal fun SeguimientoSegunVistaPrevia(
    enVistaPrevia: Boolean,
    viewModel: MapaViewModel,
) {
    var hubo by remember { mutableStateOf(false) }
    LaunchedEffect(enVistaPrevia) {
        if (enVistaPrevia) {
            hubo = true
            viewModel.onSeguimientoPerdido()
        } else if (hubo) {
            hubo = false
            viewModel.onRecentrar()
        }
    }
}

/** Sin tocar el mapa durante este tiempo (ms), en marcha, la cámara vuelve a seguir al coche. */
internal const val RECENTRAR_TRAS_MS = 10_000L

/** Por encima de esta velocidad (km/h) se considera que el coche va en marcha (parado no se recentra solo). */
internal const val VELOCIDAD_RECENTRAR_KMH = 5

/**
 * Recentrado automático: con la cámara suelta ([sigueAlCoche] falso), un gesto del usuario ([gestos], null si no hubo
 * ninguno desde que se soltó) y el coche en marcha, [RECENTRAR_TRAS_MS] después del último gesto se llama a
 * [onRecentrar]. Cada gesto reinicia la cuenta; si el coche para, se cancela y al reanudar la marcha empieza de nuevo.
 */
@Composable
internal fun RecentrarSolo(
    sigueAlCoche: Boolean,
    enMarcha: Boolean,
    gestos: Int?,
    onRecentrar: () -> Unit,
) {
    val alRecentrar by rememberUpdatedState(onRecentrar)
    LaunchedEffect(sigueAlCoche, enMarcha, gestos) {
        if (!sigueAlCoche && enMarcha && gestos != null) {
            esperarPorFotogramas(RECENTRAR_TRAS_MS)
            alRecentrar()
        }
    }
}

/** Efectos de la pantalla del mapa: permiso, posición para la navegación, vista previa y punto elegido. */
@Composable
internal fun EfectosDelMapa(
    permiso: EstadoPermisoUbicacion,
    estado: MapaUiState,
    navegacion: NavegacionUiState,
    viewModel: MapaViewModel,
    navegacionViewModel: NavegacionViewModel,
) {
    InformarDelPermiso(permiso, viewModel, navegacionViewModel)
    LaunchedEffect(estado.posicion) { navegacionViewModel.onPosicion(estado.posicion?.punto) }
    SeguimientoSegunVistaPrevia(navegacion.encuadrar, viewModel)
    // Con un punto elegido la cámara lo centra sobre su tarjeta: deja de seguir al coche (recentrar lo vuelve a pedir).
    val hayPunto = navegacion.puntoElegido != null
    LaunchedEffect(hayPunto) { if (hayPunto) viewModel.onSeguimientoPerdido() }
    val gestos by viewModel.gestos.collectAsStateWithLifecycle()
    RecentrarSolo(
        sigueAlCoche = estado.siguiendo,
        enMarcha = (estado.velocidadKmh ?: 0) > VELOCIDAD_RECENTRAR_KMH,
        gestos = gestos,
        onRecentrar = viewModel::onRecentrar,
    )
}
