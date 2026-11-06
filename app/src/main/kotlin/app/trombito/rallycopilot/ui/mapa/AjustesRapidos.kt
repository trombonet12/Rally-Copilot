package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.ajustes.ControlVolumen
import app.trombito.rallycopilot.ui.ajustes.ETIQUETA_NOTAS_SIMPLIFICADAS
import app.trombito.rallycopilot.ui.ajustes.FilaInterruptor
import app.trombito.rallycopilot.ui.ajustes.OpcionesAnticipacion
import app.trombito.rallycopilot.ui.ajustes.VistaPreviaNotasReducida
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.EstadoBotonIcono
import app.trombito.rallycopilot.ui.copiloto.AccionesCopiloto
import app.trombito.rallycopilot.ui.copiloto.CopilotoUiState
import app.trombito.rallycopilot.ui.copiloto.SelectorModoCopiloto
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_RAPIDOS_PANEL = "mapa-rapidos-panel"
const val ETIQUETA_AVISAR_CRUCES = "rapidos-avisar-cruces"
const val ETIQUETA_AVISAR_SALIDAS_AUTOPISTA = "rapidos-avisar-salidas-autopista"
const val ETIQUETA_AVISAR_ROTONDAS = "rapidos-avisar-rotondas"

private const val OPACIDAD_PANEL = 0.98f
private val RADIO_PANEL = 28.dp
private val ANCHO_MAXIMO = 340.dp
private val ANCHO_MAXIMO_APAISADO = 580.dp
private val ALTO_MAXIMO = 440.dp
private val LADO_ICONO = 28.dp

/**
 * Hoja del copiloto, pegada a su botón del panel: arriba los cuatro modos (con el nombre y lo que hace el elegido) y
 * debajo los ajustes rápidos: la vista previa, la anticipación (5 niveles), las simplificadas,
 * tres interruptores de indicaciones (cruces, salidas de autopista, rotondas) y el volumen de la voz. Cada cambio se
 * guarda y vale al momento, sin cerrar la hoja ni parar la conducción.
 */
@Composable
internal fun PanelAjustesRapidos(
    estado: CopilotoUiState,
    acciones: AccionesCopiloto,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
) {
    val descripcion = stringResource(R.string.rapidos_panel)
    Surface(
        shape = RoundedCornerShape(RADIO_PANEL),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(OPACIDAD_PANEL),
        modifier =
            modifier
                .widthIn(max = if (horizontal) ANCHO_MAXIMO_APAISADO else ANCHO_MAXIMO)
                .testTag(ETIQUETA_RAPIDOS_PANEL)
                .semantics { contentDescription = descripcion },
    ) {
        val modos = @Composable { ModosYVistaPrevia(estado, acciones) }
        val ajustes = @Composable { AjustesDeLaVoz(estado, acciones) }
        Column(
            modifier =
                Modifier
                    .heightIn(max = ALTO_MAXIMO)
                    .verticalScroll(rememberScrollState())
                    .padding(Espacio.m),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            if (horizontal) {
                // Apaisado hay poca altura: los modos y la vista previa a la izquierda, los ajustes a la derecha.
                Row(horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.s)) { modos() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.s)) { ajustes() }
                }
            } else {
                modos()
                ajustes()
            }
        }
    }
}

@Composable
private fun ModosYVistaPrevia(
    estado: CopilotoUiState,
    acciones: AccionesCopiloto,
) {
    SelectorModoCopiloto(estado.modo, acciones.onModo)
    VistaPreviaNotasReducida(
        anticipacion = estado.anticipacion,
        simplificadas = estado.notasSimplificadas,
        onEscuchar = acciones.onEscucharEjemplo,
    )
}

@Composable
private fun AjustesDeLaVoz(
    estado: CopilotoUiState,
    acciones: AccionesCopiloto,
) {
    OpcionesAnticipacion(estado.anticipacion, acciones.onAnticipacion)
    FilaInterruptor(
        titulo = stringResource(R.string.ajustes_notas_simplificadas),
        marcado = estado.notasSimplificadas,
        onCambio = acciones.onNotasSimplificadas,
        etiqueta = ETIQUETA_NOTAS_SIMPLIFICADAS,
    )
    InterruptoresIndicaciones(estado, acciones)
    ControlVolumen(estado.volumenVoz, acciones.onVolumenVoz)
}

/** Tres interruptores con icono (sin texto): cruces, salidas y entradas de autopista, rotondas. */
@Composable
private fun InterruptoresIndicaciones(
    estado: CopilotoUiState,
    acciones: AccionesCopiloto,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        InterruptorIndicacion(
            icono = R.drawable.ic_nota_cruce,
            descripcion = stringResource(R.string.indicaciones_cruces),
            activado = estado.avisarCruces,
            onCambio = acciones.onAvisarCruces,
            etiqueta = ETIQUETA_AVISAR_CRUCES,
        )
        InterruptorIndicacion(
            icono = R.drawable.ic_nota_salida_autopista,
            descripcion = stringResource(R.string.indicaciones_salidas_autopista),
            activado = estado.avisarSalidasAutopista,
            onCambio = acciones.onAvisarSalidasAutopista,
            etiqueta = ETIQUETA_AVISAR_SALIDAS_AUTOPISTA,
        )
        InterruptorIndicacion(
            icono = R.drawable.ic_nota_rotonda,
            descripcion = stringResource(R.string.indicaciones_rotondas),
            activado = estado.avisarRotondas,
            onCambio = acciones.onAvisarRotondas,
            etiqueta = ETIQUETA_AVISAR_ROTONDAS,
        )
    }
}

@Composable
private fun InterruptorIndicacion(
    icono: Int,
    descripcion: String,
    activado: Boolean,
    onCambio: (Boolean) -> Unit,
    etiqueta: String,
) {
    BotonIcono(
        descripcion = descripcion,
        onClick = { onCambio(!activado) },
        modifier = Modifier.testTag(etiqueta),
        estado =
            EstadoBotonIcono(
                seleccionado = activado,
                descripcionEstado =
                    stringResource(
                        if (activado) R.string.indicaciones_estado_activo else R.string.indicaciones_estado_inactivo,
                    ),
                rol = Role.Switch,
            ),
    ) { Icon(painterResource(icono), contentDescription = null, modifier = Modifier.size(LADO_ICONO)) }
}
