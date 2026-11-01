package app.trombito.rallycopilot.ui.mapa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.ui.alertas.AlertasUiState
import app.trombito.rallycopilot.ui.copiloto.BotonModoCopiloto
import app.trombito.rallycopilot.ui.copiloto.CopilotoEnMapa
import app.trombito.rallycopilot.ui.copiloto.DialogoVozCopiloto
import app.trombito.rallycopilot.ui.copiloto.PanelModoCopiloto
import app.trombito.rallycopilot.ui.navegacion.BotonBuscarGuiando
import app.trombito.rallycopilot.ui.navegacion.BotonTerminar
import app.trombito.rallycopilot.ui.navegacion.NavegacionEnMapa
import app.trombito.rallycopilot.ui.tramos.CronoMiniEnPanel

/** El diálogo para elegir la voz del copiloto en el primer uso. */
@Composable
internal fun DialogoVozSiToca(copiloto: CopilotoEnMapa) {
    if (!copiloto.estado.pedirVoz) return
    DialogoVozCopiloto(
        onElegir = copiloto.acciones.onElegirVoz,
        onProbar = copiloto.acciones.onProbarVoz,
        onPosponer = copiloto.acciones.onPosponerVoz,
    )
}

/** Lo que lleva el panel de velocidad: datos de conducción, botones y alertas; [enColumna] es el panel lateral. */
internal class PanelEnMapa(
    val estado: MapaUiState,
    val botones: BotonesPanel,
    val alertas: AlertasUiState,
    val enColumna: Boolean,
    /** Parado planificando: el panel se encoge para dejar más mapa. */
    val compacto: Boolean = false,
    /** Cronómetro de la conducción: en el panel solo va el pequeño (sin tramo conocido). */
    val crono: CronoEnCurso? = null,
)

@Composable
internal fun PanelDeVelocidad(
    panel: PanelEnMapa,
    modifier: Modifier,
) {
    PanelVelocidad(
        velocidadKmh = panel.estado.velocidadKmh,
        limiteKmh = panel.estado.limiteKmh,
        via = panel.estado.via,
        enColumna = panel.enColumna,
        botonesIzquierda = panel.botones.izquierda,
        botonesDerecha = panel.botones.derecha,
        alertas = panel.alertas.panel,
        compacto = panel.compacto,
        pie = panel.crono?.let { crono -> { CronoMiniEnPanel(crono) } },
        modifier = modifier,
    )
}

/** Por debajo de esta velocidad (km/h), planificando, el panel inferior se encoge. */
internal const val VELOCIDAD_COMPACTAR_KMH = 5

/** Por encima de esta velocidad (km/h) el panel compacto vuelve a su tamaño. */
internal const val VELOCIDAD_AMPLIAR_KMH = 10

/**
 * Si el panel inferior debe ser compacto, con histéresis: planificando se encoge con menos de
 * [VELOCIDAD_COMPACTAR_KMH] y no se amplía hasta pasar de [VELOCIDAD_AMPLIAR_KMH]; entre una y otra se queda como
 * estaba. Sin planificar (al empezar la ruta, guiando) siempre es el panel completo. Sin velocidad cuenta como parado.
 */
internal fun panelCompacto(
    previo: Boolean,
    velocidadKmh: Int?,
    planificando: Boolean,
): Boolean {
    if (!planificando) return false
    val v = velocidadKmh ?: 0
    return if (previo) v <= VELOCIDAD_AMPLIAR_KMH else v < VELOCIDAD_COMPACTAR_KMH
}

/** [panelCompacto] como estado de la composición: guarda el valor anterior para la histéresis. */
@Composable
internal fun rememberPanelCompacto(
    velocidadKmh: Int?,
    planificando: Boolean,
): Boolean {
    var compacto by remember { mutableStateOf(false) }
    val nuevo = panelCompacto(compacto, velocidadKmh, planificando)
    LaunchedEffect(nuevo) { compacto = nuevo }
    return compacto
}

private val ANCHO_PANEL_LATERAL = 250.dp

// Con cinco botones (la lupa del guiado) la fila de abajo necesita 5 x 48 dp + huecos + márgenes.
private val ANCHO_PANEL_LATERAL_AMPLIO = 280.dp
private const val BOTONES_EN_FILA_ESTRECHA = 4

/** Los botones de icono del panel inferior: a la izquierda de la velocidad y a la derecha. */
internal class BotonesPanel(
    val izquierda: List<@Composable () -> Unit> = emptyList(),
    val derecha: List<@Composable () -> Unit> = emptyList(),
)

internal fun anchoPanelLateral(botones: BotonesPanel) =
    if (botones.izquierda.size + botones.derecha.size > BOTONES_EN_FILA_ESTRECHA) {
        ANCHO_PANEL_LATERAL_AMPLIO
    } else {
        ANCHO_PANEL_LATERAL
    }

/** Menú desplegado sobre el mapa desde el panel inferior: solo uno a la vez. */
internal enum class MenuMapa { NINGUNO, CAPAS, MODO }

internal class MenuAbierto(
    val actual: MenuMapa,
    val cambiar: (MenuMapa) -> Unit,
) {
    /** Abre [menu], o lo cierra si ya estaba abierto. */
    fun alternar(menu: MenuMapa) = cambiar(if (actual == menu) MenuMapa.NINGUNO else menu)
}

/**
 * Izquierda: capas, modo del copiloto y, mientras se guía, la lupa que abre la búsqueda. Derecha: rumbo/norte y,
 * mientras se guía, la bandera de terminar. Con cuatro botones la velocidad no se tapa: dos a cada lado en vertical y
 * una sola fila en horizontal; con la lupa, tres a la izquierda y el panel lateral se ensancha para la fila de cinco.
 */
internal fun botonesDelPanel(
    estado: MapaUiState,
    acciones: AccionesMapa,
    navegacion: NavegacionEnMapa,
    copiloto: CopilotoEnMapa,
    menu: MenuAbierto,
): BotonesPanel {
    val terminar = navegacion.acciones.onTerminar.takeIf { navegacion.estado.conduciendo }
    val buscar = navegacion.acciones.onAbrirBusqueda.takeIf { navegacion.estado.conduciendo }
    return BotonesPanel(
        izquierda =
            listOfNotNull(
                { BotonCapas(menu.actual == MenuMapa.CAPAS, { menu.alternar(MenuMapa.CAPAS) }) },
                {
                    val abierto = menu.actual == MenuMapa.MODO
                    BotonModoCopiloto(copiloto.estado.modo, abierto, { menu.alternar(MenuMapa.MODO) })
                },
                buscar?.let { alBuscar -> { BotonBuscarGuiando(alBuscar) } },
            ),
        derecha =
            listOfNotNull(
                { BotonOrientacion(estado.orientacion, acciones.onOrientacion) },
                terminar?.let { alTerminar -> { BotonTerminar(alTerminar) } },
            ),
    )
}

/** El menú abierto desde el panel inferior (capas o modo del copiloto), pegado a su botón. */
@Composable
internal fun MenuSobreMapa(
    estado: MapaUiState,
    acciones: AccionesMapa,
    copiloto: CopilotoEnMapa,
    menu: MenuAbierto,
    modifier: Modifier,
) {
    when (menu.actual) {
        MenuMapa.CAPAS -> {
            PanelCapas(estado, acciones, modifier)
        }

        MenuMapa.MODO -> {
            PanelModoCopiloto(
                modo = copiloto.estado.modo,
                onModo = {
                    copiloto.acciones.onModo(it)
                    menu.cambiar(MenuMapa.NINGUNO)
                },
                modifier = modifier,
            )
        }

        MenuMapa.NINGUNO -> {}
    }
}
