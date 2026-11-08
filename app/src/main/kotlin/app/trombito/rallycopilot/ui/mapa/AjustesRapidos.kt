package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.ajustes.ControlVolumen
import app.trombito.rallycopilot.ui.ajustes.ETIQUETA_NOTAS_SIMPLIFICADAS
import app.trombito.rallycopilot.ui.ajustes.FilaInterruptor
import app.trombito.rallycopilot.ui.ajustes.OpcionesAnticipacion
import app.trombito.rallycopilot.ui.ajustes.VistaPreviaNotasReducida
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.EstadoBotonIcono
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.HojaInferiorWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.copiloto.AccionesCopiloto
import app.trombito.rallycopilot.ui.copiloto.ControlesAvisosVoz
import app.trombito.rallycopilot.ui.copiloto.CopilotoUiState
import app.trombito.rallycopilot.ui.copiloto.SelectorModoCopiloto
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono
import app.trombito.rallycopilot.ui.theme.Radios

const val ETIQUETA_RAPIDOS_PANEL = "mapa-rapidos-panel"
const val ETIQUETA_AVISAR_CRUCES = "rapidos-avisar-cruces"
const val ETIQUETA_AVISAR_PUENTES = "rapidos-avisar-puentes"
const val ETIQUETA_AVISAR_SALIDAS_AUTOPISTA = "rapidos-avisar-salidas-autopista"
const val ETIQUETA_AVISAR_ROTONDAS = "rapidos-avisar-rotondas"
const val ETIQUETA_SALIDA_MANUAL = "rapidos-salida-manual"

private const val FRACCION_ALTO_MAXIMO = 0.6f
private val LADO_ICONO = 28.dp

/**
 * Hoja del copiloto: una hoja modal a todo el ancho (radio 16 dp arriba) de como mucho el 60 % del alto, con scroll.
 * Secciones con rótulo de 12 sp: «Modo» (los cuatro modos con su nombre bajo el icono y la vista previa de la nota),
 * «Anticipación: 5 s», «Indicaciones» (notas simplificadas y los cuatro avisos), «Salida» del cronómetro y «Volumen».
 * Cada cambio se guarda y vale al momento, sin cerrar la hoja ni parar la conducción; se cierra al tocar fuera, al
 * arrastrarla o con atrás. En apaisado las secciones van en dos columnas para que quepan en poca altura.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PanelAjustesRapidos(
    estado: CopilotoUiState,
    acciones: AccionesCopiloto,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
) {
    val descripcion = stringResource(R.string.rapidos_panel)
    val altoMaximo = (LocalConfiguration.current.screenHeightDp * FRACCION_ALTO_MAXIMO).dp
    ModalBottomSheet(
        onDismissRequest = onCerrar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier.testTag(ETIQUETA_RAPIDOS_PANEL).semantics { contentDescription = descripcion },
        sheetMaxWidth = Dp.Unspecified,
        shape = Radios.formaHoja,
        containerColor = Color.Transparent,
        dragHandle = null,
    ) {
        HojaInferiorWrc(Modifier.heightIn(max = altoMaximo)) {
            val modos = @Composable { SeccionModo(estado, acciones) }
            val ajustes = @Composable { SeccionesAjustes(estado, acciones) }
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Espacio.m),
            ) {
                if (horizontal) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.l)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.m)) { modos() }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.m)) { ajustes() }
                    }
                } else {
                    modos()
                    ajustes()
                }
            }
        }
    }
}

/** Rótulo de sección: 12 sp, en el color secundario de la hoja. */
@Composable
private fun Rotulo(texto: String) {
    Text(
        text = texto,
        style = EtiquetaIcono,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SeccionModo(
    estado: CopilotoUiState,
    acciones: AccionesCopiloto,
) {
    Seccion(stringResource(R.string.rapidos_seccion_modo)) {
        SelectorModoCopiloto(estado.modo, acciones.onModo, conEtiquetas = true)
        VistaPreviaNotasReducida(
            anticipacion = estado.anticipacion,
            simplificadas = estado.notasSimplificadas,
            onEscuchar = acciones.onEscucharEjemplo,
        )
    }
}

@Composable
private fun Seccion(
    titulo: String,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Rotulo(titulo)
        contenido()
    }
}

@Composable
private fun SeccionesAjustes(
    estado: CopilotoUiState,
    acciones: AccionesCopiloto,
) {
    val segundos =
        FormatoEs.decimal(
            estado.anticipacion.segundos,
            if (estado.anticipacion.segundos % 1.0 ==
                0.0
            ) {
                0
            } else {
                1
            },
        )
    Seccion(stringResource(R.string.rapidos_seccion_anticipacion, segundos)) {
        OpcionesAnticipacion(estado.anticipacion, acciones.onAnticipacion, conEtiquetas = true)
    }
    Seccion(stringResource(R.string.rapidos_seccion_indicaciones)) {
        FilaInterruptor(
            titulo = stringResource(R.string.ajustes_notas_simplificadas),
            marcado = estado.notasSimplificadas,
            onCambio = acciones.onNotasSimplificadas,
            etiqueta = ETIQUETA_NOTAS_SIMPLIFICADAS,
        )
        InterruptoresIndicaciones(estado, acciones)
    }
    ControlesAvisosVoz(estado, acciones)
    Seccion(stringResource(R.string.rapidos_seccion_salida)) {
        InterruptorSalida(estado.salidaManual, acciones.onSalidaManual)
    }
    Seccion(stringResource(R.string.rapidos_seccion_volumen)) {
        ControlVolumen(estado.volumenVoz, acciones.onVolumenVoz)
    }
}

/** Interruptores con icono y su nombre debajo: cruces, puentes, salidas y entradas de autopista y rotondas. */
@Composable
private fun InterruptoresIndicaciones(
    estado: CopilotoUiState,
    acciones: AccionesCopiloto,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Espacio.l)) {
        InterruptorIndicacion(
            icono = R.drawable.ic_nota_cruce,
            descripcion = stringResource(R.string.indicaciones_cruces),
            etiqueta = stringResource(R.string.rapidos_indicacion_cruces),
            activado = estado.avisarCruces,
            onCambio = acciones.onAvisarCruces,
            prueba = ETIQUETA_AVISAR_CRUCES,
        )
        InterruptorIndicacion(
            icono = R.drawable.ic_nota_puente,
            descripcion = stringResource(R.string.indicaciones_puentes),
            etiqueta = stringResource(R.string.rapidos_indicacion_puentes),
            activado = estado.avisarPuentes,
            onCambio = acciones.onAvisarPuentes,
            prueba = ETIQUETA_AVISAR_PUENTES,
        )
        InterruptorIndicacion(
            icono = R.drawable.ic_nota_salida_autopista,
            descripcion = stringResource(R.string.indicaciones_salidas_autopista),
            etiqueta = stringResource(R.string.rapidos_indicacion_autopista),
            activado = estado.avisarSalidasAutopista,
            onCambio = acciones.onAvisarSalidasAutopista,
            prueba = ETIQUETA_AVISAR_SALIDAS_AUTOPISTA,
        )
        InterruptorIndicacion(
            icono = R.drawable.ic_nota_rotonda,
            descripcion = stringResource(R.string.indicaciones_rotondas),
            etiqueta = stringResource(R.string.rapidos_indicacion_rotondas),
            activado = estado.avisarRotondas,
            onCambio = acciones.onAvisarRotondas,
            prueba = ETIQUETA_AVISAR_ROTONDAS,
        )
    }
}

/** Salida del cronómetro: automática (al moverse) o manual (botón SALIDA y cuenta atrás); manual = seleccionado. */
@Composable
private fun InterruptorSalida(
    manual: Boolean,
    onCambio: (Boolean) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BotonIcono(
            descripcion = stringResource(R.string.salida_modo_titulo),
            onClick = { onCambio(!manual) },
            modifier = Modifier.testTag(ETIQUETA_SALIDA_MANUAL),
            estado =
                EstadoBotonIcono(
                    seleccionado = manual,
                    descripcionEstado =
                        stringResource(if (manual) R.string.salida_modo_manual else R.string.salida_modo_automatica),
                    rol = Role.Switch,
                ),
        ) { Icon(Iconos.Semaforo, contentDescription = null, modifier = Modifier.size(LADO_ICONO)) }
        Text(
            text = stringResource(if (manual) R.string.salida_modo_manual else R.string.salida_modo_automatica),
            style = EtiquetaIcono,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
@Suppress("LongParameterList") // icono, textos, estado y etiqueta de prueba del interruptor
private fun InterruptorIndicacion(
    icono: Int,
    descripcion: String,
    etiqueta: String,
    activado: Boolean,
    onCambio: (Boolean) -> Unit,
    prueba: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BotonIcono(
            descripcion = descripcion,
            onClick = { onCambio(!activado) },
            modifier = Modifier.testTag(prueba),
            estado =
                EstadoBotonIcono(
                    seleccionado = activado,
                    descripcionEstado = stringResource(estadoIndicacion(activado)),
                    rol = Role.Switch,
                ),
        ) { Icon(painterResource(icono), contentDescription = null, modifier = Modifier.size(LADO_ICONO)) }
        Text(
            text = etiqueta,
            style = EtiquetaIcono,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

private fun estadoIndicacion(activado: Boolean): Int =
    if (activado) R.string.indicaciones_estado_activo else R.string.indicaciones_estado_inactivo
