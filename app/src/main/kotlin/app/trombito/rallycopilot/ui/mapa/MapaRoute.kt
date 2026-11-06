package app.trombito.rallycopilot.ui.mapa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.alertas.AlertasUiState
import app.trombito.rallycopilot.ui.alertas.AlertasViewModel
import app.trombito.rallycopilot.ui.copiloto.AccionesCopiloto
import app.trombito.rallycopilot.ui.copiloto.CopilotoEnMapa
import app.trombito.rallycopilot.ui.copiloto.CopilotoViewModel
import app.trombito.rallycopilot.ui.edicion.AccionesEdicionMapa
import app.trombito.rallycopilot.ui.edicion.EdicionEnMapa
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
import app.trombito.rallycopilot.ui.tramos.LocalAbrirAnalisis
import app.trombito.rallycopilot.ui.tramos.LocalAccionesSalidaCrono
import kotlin.math.floor

/**
 * Une los ViewModels (mapa y navegación), el permiso de ubicación y el mapa real con la pantalla sin lógica.
 * [panelGuiado] es el hueco del banner de maniobra y el pie de guiado (por defecto, el panel real con el estilo de
 * maniobras de los ajustes).
 */
@Composable
@Suppress("LongParameterList") // un ViewModel por bloque (mapa, navegación, notas, copiloto, alertas, crono)
fun MapaRoute(
    onAbrirMapasOffline: () -> Unit,
    modifier: Modifier = Modifier,
    onVerAnalisis: ((tramoId: String, pasadaId: Long) -> Unit)? = null,
    panelGuiado: PanelGuiado? = null,
    viewModel: MapaViewModel = hiltViewModel(),
    navegacionViewModel: NavegacionViewModel = hiltViewModel(),
    notasViewModel: NotasViewModel = hiltViewModel(),
    copilotoViewModel: CopilotoViewModel = hiltViewModel(),
    alertasViewModel: AlertasViewModel = hiltViewModel(),
    cronoViewModel: CronoViewModel = hiltViewModel(),
    edicionViewModel: EdicionMapaViewModel = hiltViewModel(),
) {
    val estadoBase by viewModel.uiState.collectAsStateWithLifecycle()
    val temaOscuro = LocalTemaOscuro.current
    val estado = remember(estadoBase, temaOscuro) { estadoBase.copy(temaOscuro = temaOscuro) }
    val navegacion by navegacionViewModel.uiState.collectAsStateWithLifecycle()
    val notas by notasViewModel.uiState.collectAsStateWithLifecycle()
    val copiloto by copilotoViewModel.uiState.collectAsStateWithLifecycle()
    val alertas by alertasViewModel.uiState.collectAsStateWithLifecycle()
    val crono by cronoViewModel.crono.collectAsStateWithLifecycle()
    val edicion by edicionViewModel.uiState.collectAsStateWithLifecycle()
    val estela by viewModel.estela.collectAsStateWithLifecycle()
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
    val panel = panelGuiado ?: remember { panelGuiadoMapa() }

    val salida = remember(cronoViewModel) { AccionesSalidaCrono(cronoViewModel::decir, cronoViewModel::darSalida) }
    CompositionLocalProvider(LocalAbrirAnalisis provides onVerAnalisis, LocalAccionesSalidaCrono provides salida) {
        MapaScreen(
            estado = estado,
            vista = EstadoVistaMapa(permiso = permiso.estado, siguiendo = estado.siguiendo),
            acciones = accionesMapa(viewModel, permiso, onAbrirMapasOffline, alertasViewModel),
            mapa = { estiloJson, modificador ->
                VistaMapa(
                    estiloJson = estiloJson,
                    posicion = estado.posicionMostrada,
                    camara = camaraVistaMapa(viewModel, alertasViewModel, navegacionViewModel, edicionViewModel),
                    preferencias = preferenciasVista(estado, ruta, alertas, edicion.marcadores, estela),
                    modifier = modificador,
                )
            },
            modifier = modifier,
            navegacion = NavegacionEnMapa(navegacion, accionesNavegacion(navegacionViewModel), panel, notas),
            copiloto = CopilotoEnMapa(copiloto, accionesCopiloto(copilotoViewModel)),
            alertas = alertas,
            crono = crono,
            superposicion = {
                val control = navegacion.control
                EdicionEnMapa(
                    estado = edicion,
                    acciones =
                        AccionesEdicionMapa(
                            onAplicar = edicionViewModel::onAplicar,
                            onQuitar = edicionViewModel::onQuitar,
                            onCerrar = edicionViewModel::onCerrar,
                        ),
                    reconocimiento = control is EstadoNavegacion.Libre && control.reconocimientoTramoId != null,
                    escuchando = copiloto.escuchando,
                    onEscuchar = copilotoViewModel::onEscuchar,
                )
            },
        )
    }
}

private fun accionesCopiloto(vm: CopilotoViewModel) =
    AccionesCopiloto(
        onModo = vm::onModo,
        onElegirVoz = vm::onElegirVoz,
        onProbarVoz = vm::onProbarVoz,
        onPosponerVoz = vm::onPosponerVoz,
        onEscuchar = vm::onEscuchar,
        onAnticipacion = vm::onAnticipacion,
        onNotasSimplificadas = vm::onNotasSimplificadas,
        onVolumenVoz = vm::onVolumenVoz,
        onEscucharEjemplo = vm::onEscucharEjemplo,
        onNotasGrandes = vm::onNotasGrandes,
        onAvisarCruces = vm::onAvisarCruces,
        onAvisarSalidasAutopista = vm::onAvisarSalidasAutopista,
        onAvisarRotondas = vm::onAvisarRotondas,
        onSalidaManual = vm::onSalidaManual,
    )

private fun accionesMapa(
    viewModel: MapaViewModel,
    permiso: PermisoUbicacionUi,
    onAbrirMapasOffline: () -> Unit,
    alertas: AlertasViewModel,
) = AccionesMapa(
    onCapa = viewModel::onCapa,
    onRelieve = viewModel::onRelieve,
    onOrientacion = viewModel::onOrientacion,
    onRecentrar = viewModel::onRecentrar,
    onPedirPermiso = permiso.pedir,
    onAbrirAjustes = permiso.abrirAjustes,
    onDescartarAviso = viewModel::onDescartarAviso,
    onAbrirMapasOffline = onAbrirMapasOffline,
    onCerrarIncidencia = { alertas.onSeleccionar(null) },
)

private fun camaraVistaMapa(
    viewModel: MapaViewModel,
    alertas: AlertasViewModel,
    navegacion: NavegacionViewModel,
    edicion: EdicionMapaViewModel,
) = CamaraVistaMapa(
    inicial = viewModel.camaraGuardada,
    onCambio = viewModel::onCamara,
    onSeguimientoPerdido = viewModel::onSeguimientoPerdido,
    onZoomManual = viewModel::onZoomManual,
    // Tocar el mapa fuera de una incidencia cierra también la tarjeta del punto elegido.
    onToqueIncidencia = { id ->
        alertas.onSeleccionar(id)
        if (id == null) navegacion.cerrarPunto()
    },
    onMantenerPulsado = navegacion::onMantenerPulsado,
    onToqueNota = edicion::onTocarNota,
)

private fun preferenciasVista(
    estado: MapaUiState,
    ruta: RutaEnMapa?,
    alertas: AlertasUiState,
    notas: List<MarcadorNota>,
    estela: List<PuntoGeo>,
) = PreferenciasVistaMapa(
    siguiendo = estado.siguiendo,
    relieve = estado.relieve,
    icono = estado.icono,
    orientacion = estado.orientacion,
    zoomObjetivo = estado.zoomObjetivo,
    ruta = ruta,
    incidencias = alertas.marcadores,
    radares = alertas.radares,
    bocadillo = alertas.bocadillo,
    notas = notas,
    estela = estela,
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
        onIrAqui = vm::irAqui,
        onAnadirParadaDelPunto = vm::anadirParadaDelPunto,
        onAbrirBusqueda = vm::abrirBusquedaGuiando,
        onCerrarBusqueda = vm::cerrarBusquedaGuiando,
        onCerrarPunto = vm::cerrarPunto,
        onAnadirParada = vm::anadirParada,
        onQuitarParada = vm::quitarParada,
        onMoverParada = vm::moverParada,
    )

/** La ruta del guiado o de la vista previa lista para pintar; conserva la instancia mientras no cambien los puntos. */
@Composable
private fun rutaParaMapa(navegacion: NavegacionUiState): RutaEnMapa? {
    val ruta = navegacion.rutaEnMapa
    val destino = navegacion.destinoEnMapa
    val encuadrar = navegacion.encuadrar
    val paradas = navegacion.marcadoresParada
    val elegido = navegacion.puntoElegido?.punto
    val vacia = listOf(ruta, destino, elegido).all { it == null } && paradas.isEmpty()
    val recorrido = recorridoDeLaRuta(navegacion.control)
    return remember(ruta, destino, encuadrar, paradas, elegido, recorrido) {
        if (vacia) null else RutaEnMapa(ruta?.puntos.orEmpty(), encuadrar, destino, paradas, elegido, recorrido)
    }
}

/**
 * Metros recorridos de la ruta guiada, en escalones de [ESCALON_RECORRIDO_M] para no redibujar la línea con cada
 * fijación; 0 sin guiado o mientras se recalcula (el progreso aún es el de la ruta anterior).
 */
internal fun recorridoDeLaRuta(control: EstadoNavegacion): Double {
    val guiando = control as? EstadoNavegacion.Guiando
    val progreso = guiando?.progreso?.progresoM
    return if (guiando == null || progreso == null || guiando.recalculando) {
        0.0
    } else {
        floor(progreso / ESCALON_RECORRIDO_M) * ESCALON_RECORRIDO_M
    }
}

private const val ESCALON_RECORRIDO_M = 20.0

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
