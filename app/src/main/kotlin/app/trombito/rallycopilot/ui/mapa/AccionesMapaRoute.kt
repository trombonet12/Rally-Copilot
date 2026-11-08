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

internal fun accionesCopiloto(vm: CopilotoViewModel) =
    AccionesCopiloto(
        onModo = vm::onModo,
        onElegirVoz = vm::onElegirVoz,
        onProbarVoz = vm::onProbarVoz,
        onDescargarVoz = vm::onDescargarVoz,
        onPosponerVoz = vm::onPosponerVoz,
        onEscuchar = vm::onEscuchar,
        onAnticipacion = vm::onAnticipacion,
        onNotasSimplificadas = vm::onNotasSimplificadas,
        onVolumenVoz = vm::onVolumenVoz,
        onEscucharEjemplo = vm::onEscucharEjemplo,
        onNotasGrandes = vm::onNotasGrandes,
        onAvisarCruces = vm::onAvisarCruces,
        onAvisarPuentes = vm::onAvisarPuentes,
        onAvisarSalidasAutopista = vm::onAvisarSalidasAutopista,
        onAvisarRotondas = vm::onAvisarRotondas,
        onAvisarRadares = vm::onAvisarRadares,
        onAvisarIncidencias = vm::onAvisarIncidencias,
        onAvisarVelocidad = vm::onAvisarVelocidad,
        onVelocidadHabla = vm::onVelocidadHabla,
        onSalidaManual = vm::onSalidaManual,
    )

internal fun accionesMapa(
    viewModel: MapaViewModel,
    permiso: PermisoUbicacionUi,
    alertas: AlertasViewModel,
    reservaPuntoPx: MutableIntState,
) = AccionesMapa(
    onCapa = viewModel::onCapa,
    onRelieve = viewModel::onRelieve,
    onOrientacion = viewModel::onOrientacion,
    onRecentrar = viewModel::onRecentrar,
    onPedirPermiso = permiso.pedir,
    onAbrirAjustes = permiso.abrirAjustes,
    onDescartarAviso = viewModel::onDescartarAviso,
    onCerrarIncidencia = { alertas.onSeleccionar(null) },
    onReservaInferiorPunto = { reservaPuntoPx.intValue = it },
)

internal fun accionesNavegacion(vm: NavegacionViewModel) =
    AccionesNavegacion(
        onTexto = vm::onTexto,
        onElegirDestino = vm::elegirDestino,
        onCancelar = vm::cancelar,
        onPerfil = vm::elegirPerfil,
        onEmpezar = vm::empezar,
        onTerminar = vm::terminar,
        onSeguir = vm::seguirPendiente,
        onDescartarSeguir = vm::descartarSeguir,
        onIrAqui = vm::irAqui,
        onAnadirParadaDelPunto = vm::anadirParadaDelPunto,
        onFijarCasa = vm::fijarCasaDelPunto,
        onAbrirBusqueda = vm::abrirBusquedaGuiando,
        onTripACero = vm::reiniciarTrip,
        onCerrarBusqueda = vm::cerrarBusquedaGuiando,
        onCerrarPunto = vm::cerrarPunto,
        onAnadirParada = vm::anadirParada,
        onReintentar = vm::reintentarRutas,
        onQuitarParada = vm::quitarParada,
        onMoverParada = vm::moverParada,
    )

/** Las acciones de la pantalla del mapa, recordadas: con instancias nuevas en cada fijación nada se podría saltar. */
@Composable
@Suppress("LongParameterList") // ViewModels, permiso, reserva y las dos salidas de la pantalla
internal fun rememberAccionesDelMapa(
    viewModel: MapaViewModel,
    alertas: AlertasViewModel,
    permiso: PermisoUbicacionUi,
    reservaPuntoPx: MutableIntState,
    onAbrirMapasOffline: () -> Unit,
    onSalir: () -> Unit,
): AccionesMapa =
    remember(viewModel, alertas, permiso.pedir, permiso.abrirAjustes, onAbrirMapasOffline, onSalir) {
        accionesMapa(viewModel, permiso, alertas, reservaPuntoPx)
            .copy(onAbrirMapasOffline = onAbrirMapasOffline, onSalir = onSalir)
    }
