package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.ajustes.ControlIntercom
import app.trombito.rallycopilot.ui.ajustes.ControlVolumen
import app.trombito.rallycopilot.ui.ajustes.ETIQUETA_NOTAS_SIMPLIFICADAS
import app.trombito.rallycopilot.ui.ajustes.FilaInterruptor
import app.trombito.rallycopilot.ui.ajustes.OpcionesAnticipacion
import app.trombito.rallycopilot.ui.ajustes.VistaPreviaNotasReducida
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.HojaInferiorWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.OpcionSegmento
import app.trombito.rallycopilot.ui.comun.SelectorSegmentadoWrc
import app.trombito.rallycopilot.ui.copiloto.AccionesAvisos
import app.trombito.rallycopilot.ui.copiloto.AccionesCopiloto
import app.trombito.rallycopilot.ui.copiloto.CopilotoUiState
import app.trombito.rallycopilot.ui.copiloto.InterruptoresAvisos
import app.trombito.rallycopilot.ui.copiloto.SelectorModoCopiloto
import app.trombito.rallycopilot.ui.copiloto.SelectorVelocidadHabla
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono
import app.trombito.rallycopilot.ui.theme.Radios

const val ETIQUETA_RAPIDOS_PANEL = "mapa-rapidos-panel"
const val ETIQUETA_AVISAR_CRUCES = "rapidos-avisar-cruces"
const val ETIQUETA_AVISAR_PUENTES = "rapidos-avisar-puentes"
const val ETIQUETA_AVISAR_SALIDAS_AUTOPISTA = "rapidos-avisar-salidas-autopista"
const val ETIQUETA_AVISAR_ROTONDAS = "rapidos-avisar-rotondas"
const val ETIQUETA_SALIDA_MANUAL = "rapidos-salida-manual"
const val ETIQUETA_RAPIDOS_AYUDA_NOTAS = "rapidos-ayuda-notas"

private const val FRACCION_ALTO_MAXIMO = 0.6f
private val LADO_ICONO = 28.dp

/** Las dos pestañas de la hoja: lo que el copiloto canta y cómo suena su voz. */
internal enum class PestanaCopiloto { QUE_CANTA, COMO_SUENA }

/** Una columna de secciones de la hoja. */
private typealias ColumnaHoja = @Composable ColumnScope.() -> Unit

/**
 * Hoja del copiloto: una hoja modal a todo el ancho (radio 16 dp arriba) de como mucho el 60 % del alto, en dos
 * pestañas de icono fijas arriba, con el «?» de [onAyudaNotas] a su lado («Qué significan las notas»).
 * «Qué canta»: el modo con la vista previa de la nota, la anticipación, las notas simplificadas, los cuatro avisos
 * de la carretera y los tres avisos por voz. «Cómo suena»: el volumen, la velocidad de habla, el intercom del casco y
 * la salida del cronómetro. Cada cambio se guarda y vale al momento, sin cerrar la hoja ni parar la conducción; se
 * cierra al tocar fuera, al arrastrarla o con atrás. En apaisado las secciones de cada pestaña van en dos columnas
 * para que quepan en poca altura; si aun así no caben, la pestaña se desplaza.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList") // estado, acciones, cierre, ayuda y orientación de la hoja
internal fun PanelAjustesRapidos(
    estado: CopilotoUiState,
    acciones: AccionesCopiloto,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
    onAyudaNotas: () -> Unit = {},
    pestanaInicial: PestanaCopiloto = PestanaCopiloto.QUE_CANTA,
) {
    val descripcion = stringResource(R.string.rapidos_panel)
    val altoMaximo = (LocalConfiguration.current.screenHeightDp * FRACCION_ALTO_MAXIMO).dp
    var pestana by rememberSaveable { mutableStateOf(pestanaInicial) }
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
            PestanasCopiloto(pestana, { pestana = it }, onAyudaNotas)
            Spacer(Modifier.height(Espacio.m))
            val columnas =
                when (pestana) {
                    PestanaCopiloto.QUE_CANTA -> columnasQueCanta(estado, acciones)
                    PestanaCopiloto.COMO_SUENA -> columnasComoSuena(estado, acciones)
                }
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Espacio.m),
            ) {
                Columnas(columnas, horizontal)
            }
        }
    }
}

/** Las columnas de una pestaña: una tras otra en vertical y una al lado de otra en apaisado. */
@Composable
private fun Columnas(
    columnas: List<ColumnaHoja>,
    horizontal: Boolean,
) {
    if (horizontal) {
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.l)) {
            columnas.forEach {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.m), content = it)
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.m)) { columnas.forEach { it() } }
    }
}

/** Las pestañas de icono («Qué canta» y «Cómo suena») y, a su derecha, el «?» que abre «Qué significan las notas». */
@Composable
private fun PestanasCopiloto(
    pestana: PestanaCopiloto,
    onPestana: (PestanaCopiloto) -> Unit,
    onAyudaNotas: () -> Unit,
) {
    val opciones =
        listOf(
            OpcionSegmento(PestanaCopiloto.QUE_CANTA, stringResource(R.string.rapidos_pestana_canta), Iconos.Notas),
            OpcionSegmento(PestanaCopiloto.COMO_SUENA, stringResource(R.string.rapidos_pestana_suena), Iconos.Volumen),
        )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        SelectorSegmentadoWrc(opciones, pestana, onPestana, Modifier.weight(1f))
        BotonIcono(
            descripcion = stringResource(R.string.ayuda_notas_titulo),
            onClick = onAyudaNotas,
            modifier = Modifier.testTag(ETIQUETA_RAPIDOS_AYUDA_NOTAS),
        ) { Icon(Iconos.Ayuda, contentDescription = null, modifier = Modifier.size(LADO_ICONO)) }
    }
}

/** Sección con su rótulo de 12 sp, en el color secundario de la hoja. */
@Composable
private fun Seccion(
    titulo: String,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Text(text = titulo, style = EtiquetaIcono, color = MaterialTheme.colorScheme.onSurfaceVariant)
        contenido()
    }
}

private fun columnasQueCanta(
    estado: CopilotoUiState,
    acciones: AccionesCopiloto,
): List<ColumnaHoja> =
    listOf(
        { SeccionModo(estado, acciones) },
        {
            SeccionAnticipacion(estado, acciones)
            SeccionIndicaciones(estado, acciones)
            SeccionAvisosVoz(estado, acciones)
        },
    )

private fun columnasComoSuena(
    estado: CopilotoUiState,
    acciones: AccionesCopiloto,
): List<ColumnaHoja> =
    listOf(
        {
            Seccion(stringResource(R.string.rapidos_seccion_volumen)) {
                ControlVolumen(estado.volumenVoz, acciones.onVolumenVoz)
            }
            Seccion(stringResource(R.string.habla_titulo)) {
                SelectorVelocidadHabla(estado.velocidadHabla, acciones.onVelocidadHabla)
            }
        },
        {
            ControlIntercom()
            Seccion(stringResource(R.string.rapidos_seccion_salida)) {
                InterruptorSalida(estado.salidaManual, acciones.onSalidaManual)
            }
        },
    )

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
private fun SeccionAnticipacion(
    estado: CopilotoUiState,
    acciones: AccionesCopiloto,
) {
    val decimales = if (estado.anticipacion.segundos % 1.0 == 0.0) 0 else 1
    val segundos = FormatoEs.decimal(estado.anticipacion.segundos, decimales)
    Seccion(stringResource(R.string.rapidos_seccion_anticipacion, segundos)) {
        OpcionesAnticipacion(estado.anticipacion, acciones.onAnticipacion, conEtiquetas = true)
    }
}

@Composable
private fun SeccionIndicaciones(
    estado: CopilotoUiState,
    acciones: AccionesCopiloto,
) {
    Seccion(stringResource(R.string.rapidos_seccion_indicaciones)) {
        FilaInterruptor(
            titulo = stringResource(R.string.ajustes_notas_simplificadas),
            marcado = estado.notasSimplificadas,
            onCambio = acciones.onNotasSimplificadas,
            etiqueta = ETIQUETA_NOTAS_SIMPLIFICADAS,
        )
        InterruptoresIndicaciones(estado, acciones)
    }
}

/** Los tres avisos por voz (radares, incidencias y velocidad): lo que el copiloto canta además de las notas. */
@Composable
private fun SeccionAvisosVoz(
    estado: CopilotoUiState,
    acciones: AccionesCopiloto,
) {
    Seccion(stringResource(R.string.avisos_titulo)) {
        InterruptoresAvisos(
            radares = estado.avisarRadares,
            incidencias = estado.avisarIncidencias,
            velocidad = estado.avisarVelocidad,
            acciones =
                AccionesAvisos(acciones.onAvisarRadares, acciones.onAvisarIncidencias, acciones.onAvisarVelocidad),
        )
    }
}
