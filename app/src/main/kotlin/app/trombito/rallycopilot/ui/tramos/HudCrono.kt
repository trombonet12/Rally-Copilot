package app.trombito.rallycopilot.ui.tramos

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ParcialCrono
import app.trombito.rallycopilot.domain.tramos.TipoCorte
import app.trombito.rallycopilot.ui.maniobras.ParalelogramoShape
import app.trombito.rallycopilot.ui.navegacion.guiado.ColoresGuiado

const val ETIQUETA_CRONO_HUD = "crono-hud"
const val ETIQUETA_CRONO_TIEMPO = "crono-tiempo"
const val ETIQUETA_CRONO_DELTA = "crono-delta"
const val ETIQUETA_CRONO_BARRA = "crono-barra"

/** Parcial ya cerrado: `crono-parcial-S1`, `crono-parcial-M2`. */
const val ETIQUETA_CRONO_PARCIAL_PREFIJO = "crono-parcial-"

/** Envuelve al parcial recién cortado mientras está destacado. */
const val ETIQUETA_CRONO_DESTACADO = "crono-destacado"

private const val OPACIDAD_FONDO = 0.94f
private const val OPACIDAD_BORDE = 0.7f
private const val OPACIDAD_VIA = 0.18f
private const val OPACIDAD_MICRO = 0.55f
private const val OPACIDAD_ETIQUETA = 0.85f
private const val ESCALA_DESTACADO = 1.12f
private const val PORCENTAJE = 100
private val FONDO_PARCIAL = Color(0xFF22252A)

/** Medidas del HUD en vertical y en horizontal compacto. */
private class MedidasHud(
    val tiempo: TextUnit,
    val delta: TextUnit,
    val parcial: TextUnit,
    val barra: Dp,
    val sesgo: Dp,
    val compacto: Boolean,
) {
    val anchoDelta: Dp get() = if (compacto) 76.dp else 104.dp
}

private val NORMAL =
    MedidasHud(
        tiempo = 40.sp,
        delta = 28.sp,
        parcial = 16.sp,
        barra = 10.dp,
        sesgo = 10.dp,
        compacto = false,
    )
private val COMPACTO =
    MedidasHud(
        tiempo = 30.sp,
        delta = 22.sp,
        parcial = 14.sp,
        barra = 8.dp,
        sesgo = 6.dp,
        compacto = true,
    )

private fun estiloCifras(tamano: TextUnit) =
    TextStyle(
        fontSize = tamano,
        fontWeight = FontWeight.Black,
        fontStyle = FontStyle.Italic,
        fontFeatureSettings = "tnum",
    )

/**
 * HUD de retransmisión de un tramo conocido (con mejor pasada): cronómetro grande, delta en vivo (verde si va más
 * rápido que la mejor pasada, rojo si más lento), barra de progreso con las marcas de sectores y microsectores y los
 * parciales de sector ya pasados. El parcial recién cortado destaca [DURACION_PARCIAL_DESTACADO_MS]. En apaisado
 * ([compacto]) cronómetro, delta y parciales van en una fila sobre la barra. No pinta nada sin tramo. [reloj] es el
 * reloj de pared (ms epoch) con el que el cronómetro sigue contando entre fijaciones.
 */
@Composable
fun HudCrono(
    crono: CronoEnCurso,
    modifier: Modifier = Modifier,
    compacto: Boolean = false,
    reloj: () -> Long = System::currentTimeMillis,
) {
    val tramo = crono.tramo ?: return
    val medidas = if (compacto) COMPACTO else NORMAL
    val transcurrido = rememberTranscurridoMs(crono, reloj, PERIODO_HUD_MS)
    val destacado = rememberParcialDestacado(crono.ultimaMarca)
    val forma = ParalelogramoShape(medidas.sesgo)
    val parciales: @Composable () -> Unit = {
        Parciales(
            parciales = crono.parciales.filter { it.tipo == TipoCorte.SECTOR },
            destacado = destacado,
            medidas = medidas,
        )
    }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(ETIQUETA_CRONO_HUD)
                .background(ColoresGuiado.Carbon.copy(OPACIDAD_FONDO), forma)
                .border(2.dp, ColoresGuiado.Laton.copy(OPACIDAD_BORDE), forma)
                .padding(horizontal = medidas.sesgo + 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TiempoYDelta(
                transcurrido,
                crono.deltaMs,
                medidas,
                if (compacto) Modifier else Modifier.weight(1f),
                listo = !crono.enMarcha,
            )
            if (compacto) Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { parciales() }
        }
        BarraTramo(crono, medidas)
        if (!compacto) parciales()
    }
}

/** Cronómetro grande y delta en vivo, con una sola descripción para TalkBack (sin región viva: cambia sin parar). */
@Composable
private fun TiempoYDelta(
    transcurridoMs: Long,
    deltaMs: Long?,
    medidas: MedidasHud,
    modifier: Modifier,
    listo: Boolean = false,
) {
    val recursos = LocalContext.current.resources
    val tiempo = descripcionTiempo(recursos, transcurridoMs)
    val alfa = if (listo) rememberAlfaListo() else 1f
    val descripcion =
        if (listo) {
            recursos.getString(R.string.crono_listo_descripcion)
        } else if (deltaMs == null) {
            recursos.getString(R.string.crono_hud_descripcion_sin_delta, tiempo)
        } else {
            recursos.getString(R.string.crono_hud_descripcion, tiempo, descripcionDelta(recursos, deltaMs))
        }
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (medidas.compacto) Arrangement.spacedBy(10.dp) else Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (listo) stringResource(R.string.crono_listo) else formatearTiempo(transcurridoMs),
            style = estiloCifras(medidas.tiempo),
            color = ColoresGuiado.Texto,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.alpha(alfa).testTag(ETIQUETA_CRONO_TIEMPO),
        )
        PildoraDelta(deltaMs, medidas.delta, medidas.anchoDelta, medidas.sesgo / 2)
    }
}

/**
 * El delta como texto con signo y flecha (▼ a favor, ▲ en contra), en verde o rojo sin relleno; solo un borde fino del
 * mismo color. Sin delta, un guion neutro.
 */
@Composable
internal fun PildoraDelta(
    deltaMs: Long?,
    tamano: TextUnit,
    anchoMinimo: Dp,
    sesgo: Dp,
    etiqueta: String = ETIQUETA_CRONO_DELTA,
) {
    val color = if (deltaMs == null) ColoresGuiado.Texto.copy(alpha = OPACIDAD_ETIQUETA) else colorDelta(deltaMs)
    val forma = ParalelogramoShape(sesgo)
    val texto =
        if (deltaMs == null) {
            stringResource(R.string.crono_sin_delta)
        } else {
            "${flechaDelta(deltaMs)} ${formatearDelta(deltaMs)}".trim()
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .defaultMinSize(minWidth = anchoMinimo)
                .border(1.dp, color.copy(OPACIDAD_BORDE), forma)
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .testTag(etiqueta),
    ) {
        Text(
            text = texto,
            style = estiloCifras(tamano),
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** El parcial recién cortado, durante [DURACION_PARCIAL_DESTACADO_MS]; el que ya había al montar no se destaca. */
@Composable
private fun rememberParcialDestacado(ultima: ParcialCrono?): ParcialCrono? {
    val inicial = remember { ultima }
    var destacado by remember { mutableStateOf<ParcialCrono?>(null) }
    LaunchedEffect(ultima) {
        if (ultima != null && ultima != inicial) {
            destacado = ultima
            esperarEnPrincipal(DURACION_PARCIAL_DESTACADO_MS)
            destacado = null
        }
    }
    return destacado
}

/** Parciales de sector ya pasados (S1 −0,3 · S2 +1,2…); un microsector recién cortado va al final unos segundos. */
@Composable
private fun Parciales(
    parciales: List<ParcialCrono>,
    destacado: ParcialCrono?,
    medidas: MedidasHud,
) {
    val extra = destacado?.takeIf { it.tipo == TipoCorte.MICROSECTOR }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        (parciales + listOfNotNull(extra)).forEach { parcial ->
            ChipParcial(parcial, parcial == destacado, medidas)
        }
    }
}

@Composable
private fun ChipParcial(
    parcial: ParcialCrono,
    destacado: Boolean,
    medidas: MedidasHud,
) {
    val recursos = LocalContext.current.resources
    val sector = parcial.tipo == TipoCorte.SECTOR
    val etiqueta =
        stringResource(if (sector) R.string.crono_sector_etiqueta else R.string.crono_micro_etiqueta, parcial.numero)
    val color = colorParcial(parcial.diferenciaMs)
    val valor = parcial.diferenciaMs?.let(::formatearDelta) ?: formatearTiempo(parcial.duracionMs, conDecimas = false)
    val cuerpo =
        parcial.diferenciaMs?.let { descripcionDelta(recursos, it) }
            ?: recursos.getString(
                R.string.crono_parcial_con_tiempo,
                etiqueta,
                descripcionTiempo(recursos, parcial.duracionMs),
            )
    val descripcion =
        recursos.getString(
            if (sector) R.string.crono_sector_descripcion else R.string.crono_micro_descripcion,
            parcial.numero,
            cuerpo,
        )
    val escala by animateFloatAsState(if (destacado) ESCALA_DESTACADO else 1f, label = "parcial")
    val forma = RoundedCornerShape(6.dp)
    val nodo =
        Modifier
            .graphicsLayer {
                scaleX = escala
                scaleY = escala
            }.background(if (destacado) color else FONDO_PARCIAL, forma)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag(ETIQUETA_CRONO_PARCIAL_PREFIJO + etiqueta)
            .clearAndSetSemantics {
                contentDescription = descripcion
                if (destacado) liveRegion = LiveRegionMode.Polite
            }
    val contenido: @Composable () -> Unit = {
        Row(
            modifier = nodo,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = etiqueta,
                style = estiloCifras(medidas.parcial).copy(fontWeight = FontWeight.Bold),
                color = if (destacado) ColoresGuiado.Carbon else ColoresGuiado.Texto.copy(alpha = OPACIDAD_ETIQUETA),
            )
            Text(
                text = valor,
                style = estiloCifras(medidas.parcial),
                color = if (destacado) ColoresGuiado.Carbon else color,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
    if (destacado) Box(Modifier.testTag(ETIQUETA_CRONO_DESTACADO)) { contenido() } else contenido()
}

/**
 * Barra de progreso del tramo: relleno de latón hasta lo recorrido, un corte alto por cada fin de sector y un punto por
 * cada fin de microsector. Los ya pasados toman el color de su diferencia con la mejor pasada.
 */
@Composable
private fun BarraTramo(
    crono: CronoEnCurso,
    medidas: MedidasHud,
) {
    val tramo = crono.tramo ?: return
    val fraccion = fraccionProgreso(crono.progresoM, tramo.metaM)
    val marcas = remember(tramo, crono.parciales) { marcasDeBarra(tramo, crono.parciales) }
    val descripcion = stringResource(R.string.crono_barra_descripcion, (fraccion * PORCENTAJE).toInt())
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(medidas.barra + 6.dp)
            .testTag(ETIQUETA_CRONO_BARRA)
            .clearAndSetSemantics {
                contentDescription = descripcion
                progressBarRangeInfo = ProgressBarRangeInfo(fraccion, 0f..1f)
            },
    ) {
        val alto = medidas.barra.toPx()
        val arriba = (size.height - alto) / 2
        val radio = CornerRadius(alto / 2)
        drawRoundRect(Color.White.copy(OPACIDAD_VIA), Offset(0f, arriba), Size(size.width, alto), radio)
        drawRoundRect(ColoresGuiado.Laton, Offset(0f, arriba), Size(size.width * fraccion, alto), radio)
        marcas.forEach { marca ->
            val x = size.width * marca.fraccion
            val color = marca.signo?.let { colorSigno(it) }
            if (marca.tipo == TipoCorte.SECTOR) {
                drawRect(color ?: Color.White, Offset(x - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), size.height))
            } else {
                drawCircle(color ?: Color.White.copy(OPACIDAD_MICRO), 2.5.dp.toPx(), Offset(x, size.height / 2))
            }
        }
    }
}
