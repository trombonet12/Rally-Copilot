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
    onSalir: () -> Unit = {},
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
    // Sin `by`: solo el mapa y la superposición leen la edición y la estela, así que sus cambios no recomponen la raíz.
    val edicion = edicionViewModel.uiState.collectAsStateWithLifecycle()
    val estela = viewModel.estela.collectAsStateWithLifecycle()
    val permiso = rememberPermisoUbicacion()
    EfectosDelMapa(permiso.estado, estado, navegacion, viewModel, navegacionViewModel)
    // Sin `by`: solo el mapa lee la reserva, así que su cambio no recompone la raíz.
    val reservaPuntoPx = remember { mutableIntStateOf(0) }
    val ruta = rutaParaMapa(navegacion)
    val panel = panelGuiado ?: remember { panelGuiadoMapa() }

    val salida = remember(cronoViewModel) { AccionesSalidaCrono(cronoViewModel::decir, cronoViewModel::darSalida) }
    // Las acciones y los bloques derivados se recuerdan: con instancias nuevas en cada fijación nada se podría saltar.
    val accionesDelMapa =
        rememberAccionesDelMapa(viewModel, alertasViewModel, permiso, reservaPuntoPx, onAbrirMapasOffline, onSalir)
    val accionesNav = remember(navegacionViewModel) { accionesNavegacion(navegacionViewModel) }
    val accionesCop = remember(copilotoViewModel) { accionesCopiloto(copilotoViewModel) }
    val navegacionEnMapa =
        remember(navegacion, accionesNav, panel, notas) { NavegacionEnMapa(navegacion, accionesNav, panel, notas) }
    val copilotoEnMapa =
        remember(copiloto, accionesCop, estado.datos) {
            CopilotoEnMapa(copiloto, accionesCop, EstadoVozDescarga.de(estado.datos))
        }
    CompositionLocalProvider(LocalAbrirAnalisis provides onVerAnalisis, LocalAccionesSalidaCrono provides salida) {
        MapaScreen(
            estado = estado,
            vista = EstadoVistaMapa(permiso = permiso.estado, siguiendo = estado.siguiendo),
            acciones = accionesDelMapa,
            mapa = { estiloJson, modificador ->
                VistaMapa(
                    estiloJson = estiloJson,
                    posicion = estado.posicionMostrada,
                    camara = camaraVistaMapa(viewModel, alertasViewModel, navegacionViewModel, edicionViewModel),
                    preferencias =
                        preferenciasVista(estado, ruta, alertas, edicion.value.marcadores, estela.value)
                            .copy(reservaPuntoPx = reservaPuntoPx.intValue),
                    modifier = modificador,
                )
            },
            modifier = modifier,
            navegacion = navegacionEnMapa,
            copiloto = copilotoEnMapa,
            alertas = alertas,
            crono = crono,
            superposicion = {
                SuperposicionDelMapa(
                    UiSuperposicion(copiloto, crono, edicion.value, navegacion.control),
                    navegacionViewModel,
                    edicionViewModel,
                    copilotoViewModel,
                )
            },
        )
    }
}

@Composable
private fun camaraVistaMapa(
    viewModel: MapaViewModel,
    alertas: AlertasViewModel,
    navegacion: NavegacionViewModel,
    edicion: EdicionMapaViewModel,
) = remember(viewModel, alertas, navegacion, edicion) {
    CamaraVistaMapa(
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
}.conArranque()

private fun preferenciasVista(
    estado: MapaUiState,
    ruta: RutaEnMapa?,
    alertas: AlertasUiState,
    notas: List<MarcadorNota>,
    estela: List<List<PuntoGeo>>,
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
