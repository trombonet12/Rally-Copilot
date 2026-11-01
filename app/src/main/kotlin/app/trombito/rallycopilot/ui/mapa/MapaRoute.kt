package app.trombito.rallycopilot.ui.mapa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trombito.rallycopilot.ui.copiloto.AccionesCopiloto
import app.trombito.rallycopilot.ui.copiloto.CopilotoEnMapa
import app.trombito.rallycopilot.ui.copiloto.CopilotoViewModel
import app.trombito.rallycopilot.ui.maniobras.variante
import app.trombito.rallycopilot.ui.navegacion.AccionesNavegacion
import app.trombito.rallycopilot.ui.navegacion.NavegacionEnMapa
import app.trombito.rallycopilot.ui.navegacion.NavegacionUiState
import app.trombito.rallycopilot.ui.navegacion.NavegacionViewModel
import app.trombito.rallycopilot.ui.navegacion.PanelGuiado
import app.trombito.rallycopilot.ui.navegacion.notas.NotasViewModel
import app.trombito.rallycopilot.ui.navegacion.panelGuiadoMapa

/**
 * Une los ViewModels (mapa y navegación), el permiso de ubicación y el mapa real con la pantalla sin lógica.
 * [panelGuiado] es el hueco del banner de maniobra y el pie de guiado (por defecto, el panel real con el estilo de
 * maniobras de los ajustes).
 */
@Composable
@Suppress("LongParameterList") // un ViewModel por bloque (mapa, navegación, notas, copiloto) más los huecos de prueba
fun MapaRoute(
    onAbrirMapasOffline: () -> Unit,
    modifier: Modifier = Modifier,
    panelGuiado: PanelGuiado? = null,
    viewModel: MapaViewModel = hiltViewModel(),
    navegacionViewModel: NavegacionViewModel = hiltViewModel(),
    notasViewModel: NotasViewModel = hiltViewModel(),
    copilotoViewModel: CopilotoViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val navegacion by navegacionViewModel.uiState.collectAsStateWithLifecycle()
    val notas by notasViewModel.uiState.collectAsStateWithLifecycle()
    val copiloto by copilotoViewModel.uiState.collectAsStateWithLifecycle()
    val permiso = rememberPermisoUbicacion()
    LaunchedEffect(permiso.estado) {
        val concedido = permiso.estado == EstadoPermisoUbicacion.CONCEDIDO
        viewModel.onPermisoUbicacion(concedido)
        // El modo libre arranca solo al abrir el mapa con permiso (o al concederlo) y se para al salir (onCleared).
        navegacionViewModel.onPermisoUbicacion(concedido)
    }
    LaunchedEffect(estado.posicion) { navegacionViewModel.onPosicion(estado.posicion?.punto) }
    SeguimientoSegunVistaPrevia(navegacion.encuadrar, viewModel)
    val ruta = rutaParaMapa(navegacion)
    val panel = panelGuiado ?: remember(estado.estiloManiobras) { panelGuiadoMapa(estado.estiloManiobras.variante()) }

    MapaScreen(
        estado = estado,
        vista = EstadoVistaMapa(permiso = permiso.estado, siguiendo = estado.siguiendo),
        acciones = accionesMapa(viewModel, permiso, onAbrirMapasOffline),
        mapa = { estiloJson, modificador ->
            VistaMapa(
                estiloJson = estiloJson,
                posicion = estado.posicionMostrada,
                camara = camaraVistaMapa(viewModel),
                preferencias = preferenciasVista(estado, ruta),
                modifier = modificador,
            )
        },
        modifier = modifier,
        navegacion = NavegacionEnMapa(navegacion, accionesNavegacion(navegacionViewModel), panel, notas),
        copiloto = CopilotoEnMapa(copiloto, accionesCopiloto(copilotoViewModel)),
    )
}

private fun accionesCopiloto(vm: CopilotoViewModel) =
    AccionesCopiloto(
        onModo = vm::onModo,
        onElegirVoz = vm::onElegirVoz,
        onProbarVoz = vm::onProbarVoz,
        onPosponerVoz = vm::onPosponerVoz,
    )

private fun accionesMapa(
    viewModel: MapaViewModel,
    permiso: PermisoUbicacionUi,
    onAbrirMapasOffline: () -> Unit,
) = AccionesMapa(
    onCapa = viewModel::onCapa,
    onRelieve = viewModel::onRelieve,
    onOrientacion = viewModel::onOrientacion,
    onRecentrar = viewModel::onRecentrar,
    onPedirPermiso = permiso.pedir,
    onAbrirAjustes = permiso.abrirAjustes,
    onDescartarAviso = viewModel::onDescartarAviso,
    onAbrirMapasOffline = onAbrirMapasOffline,
)

private fun camaraVistaMapa(viewModel: MapaViewModel) =
    CamaraVistaMapa(
        inicial = viewModel.camaraGuardada,
        onCambio = viewModel::onCamara,
        onSeguimientoPerdido = viewModel::onSeguimientoPerdido,
        onZoomManual = viewModel::onZoomManual,
    )

private fun preferenciasVista(
    estado: MapaUiState,
    ruta: RutaEnMapa?,
) = PreferenciasVistaMapa(
    siguiendo = estado.siguiendo,
    relieve = estado.relieve,
    icono = estado.icono,
    orientacion = estado.orientacion,
    zoomObjetivo = estado.zoomObjetivo,
    ruta = ruta,
)

private fun accionesNavegacion(vm: NavegacionViewModel) =
    AccionesNavegacion(
        onTexto = vm::onTexto,
        onElegirDestino = vm::elegirDestino,
        onCancelar = vm::cancelar,
        onPerfil = vm::elegirPerfil,
        onEmpezar = vm::empezar,
        onTerminar = vm::terminar,
        onSeguir = vm::seguirPendiente,
        onDescartarSeguir = vm::descartarSeguir,
    )

/** La ruta del guiado o de la vista previa lista para pintar; conserva la instancia mientras no cambien los puntos. */
@Composable
private fun rutaParaMapa(navegacion: NavegacionUiState): RutaEnMapa? {
    val ruta = navegacion.rutaEnMapa
    val destino = navegacion.destinoEnMapa
    val encuadrar = navegacion.encuadrar
    return remember(ruta, destino, encuadrar) {
        if (ruta == null && destino == null) null else RutaEnMapa(ruta?.puntos.orEmpty(), encuadrar, destino)
    }
}

/**
 * Durante la vista previa la cámara deja de seguir al coche (encuadra la ruta); al empezar o al cancelar vuelve al
 * seguimiento. No toca nada al entrar en la pantalla si no ha habido vista previa.
 */
@Composable
private fun SeguimientoSegunVistaPrevia(
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
