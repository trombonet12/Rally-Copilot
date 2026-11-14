package app.trombito.rallycopilot.ui.tramos

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.tramos.SubtramoResultado
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.maniobras.ParalelogramoShape
import app.trombito.rallycopilot.ui.navegacion.guiado.ColoresGuiado

const val ETIQUETA_SUBTRAMO_TARJETA = "subtramo-tarjeta"
const val ETIQUETA_SUBTRAMO_TARJETA_TIEMPO = "subtramo-tarjeta-tiempo"
const val ETIQUETA_SUBTRAMO_TARJETA_DELTA = "subtramo-tarjeta-delta"
const val ETIQUETA_SUBTRAMO_PRIMERA_VEZ = "subtramo-primera-vez"

/** Cuánto se queda la tarjeta de un subtramo recién cerrado. */
internal const val DURACION_TARJETA_SUBTRAMO_MS = 8_000L

private const val OPACIDAD_FONDO = 0.94f
private const val OPACIDAD_SECUNDARIO = 0.85f

/** Medidas de la línea de subtramo en vertical y en horizontal compacto. */
private class MedidasSubtramo(
    val titulo: TextUnit,
    val tiempo: TextUnit,
    val delta: TextUnit,
    val anchoDelta: Dp,
    val sesgo: Dp,
    val vertical: Dp,
)

private val NORMAL = MedidasSubtramo(14.sp, 26.sp, 18.sp, 80.dp, 8.dp, 5.dp)
private val COMPACTO = MedidasSubtramo(12.sp, 20.sp, 15.sp, 64.dp, 5.dp, 3.dp)

private fun estiloSubtramo(tamano: TextUnit) =
    TextStyle(
        fontSize = tamano,
        fontWeight = FontWeight.Black,
        fontStyle = FontStyle.Italic,
        fontFeatureSettings = "tnum",
    )

/**
 * La tarjeta de un subtramo recién cerrado mientras sigue vigente ([DURACION_TARJETA_SUBTRAMO_MS] desde su
 * `momentoMs` según [reloj]); null si no hay o ya caducó. Espera con [esperarEnPrincipal], que gobierna el reloj de
 * los tests.
 */
@Composable
internal fun rememberSubtramoReciente(
    ultimo: SubtramoResultado?,
    reloj: () -> Long,
): SubtramoResultado? {
    var vigente by remember(ultimo) {
        mutableStateOf(ultimo?.takeIf { transcurridoDesde(it, reloj) <= DURACION_TARJETA_SUBTRAMO_MS })
    }
    LaunchedEffect(ultimo) {
        if (ultimo != null && vigente != null) {
            esperarEnPrincipal((DURACION_TARJETA_SUBTRAMO_MS - transcurridoDesde(ultimo, reloj)).coerceAtLeast(0L))
            vigente = null
        }
    }
    return vigente
}

private fun transcurridoDesde(
    subtramo: SubtramoResultado,
    reloj: () -> Long,
): Long = (reloj() - subtramo.momentoMs).coerceAtLeast(0L)

/**
 * Tarjeta breve de un subtramo cerrado: «Subtramo 2,4 km», el tiempo y el delta contra el mejor previo en ese trozo
 * (sin mejor, «Primera vez»). Región viva: TalkBack la anuncia entera al aparecer.
 */
@Composable
fun TarjetaSubtramo(
    resultado: SubtramoResultado,
    modifier: Modifier = Modifier,
    compacta: Boolean = false,
) {
    val medidas = if (compacta) COMPACTO else NORMAL
    val distancia = FormatoEs.distancia(resultado.longitudM)
    val delta = resultado.mejorMs?.let { resultado.tiempoMs - it }
    val descripcion = descripcionTarjeta(LocalContext.current.resources, resultado, distancia, delta)
    val forma = ParalelogramoShape(medidas.sesgo)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(ETIQUETA_SUBTRAMO_TARJETA)
                .background(ColoresGuiado.Carbon.copy(OPACIDAD_FONDO), forma)
                .border(2.dp, ColoresGuiado.Laton, forma)
                .padding(horizontal = medidas.sesgo + 10.dp, vertical = medidas.vertical)
                .clearAndSetSemantics {
                    contentDescription = descripcion
                    liveRegion = LiveRegionMode.Polite
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.subtramo_tarjeta_titulo, distancia),
            style = estiloSubtramo(medidas.titulo),
            color = ColoresGuiado.Laton,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatearTiempo(resultado.tiempoMs),
            style = estiloSubtramo(medidas.tiempo),
            color = ColoresGuiado.Texto,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(ETIQUETA_SUBTRAMO_TARJETA_TIEMPO),
        )
        DeltaOPrimeraVez(delta, medidas)
    }
}

/** El delta contra el mejor previo o, sin él, «Primera vez». */
@Composable
private fun DeltaOPrimeraVez(
    delta: Long?,
    medidas: MedidasSubtramo,
) {
    if (delta != null) {
        PildoraDelta(delta, medidas.delta, medidas.anchoDelta, medidas.sesgo / 2, ETIQUETA_SUBTRAMO_TARJETA_DELTA)
    } else {
        Text(
            text = stringResource(R.string.subtramo_primera_vez),
            style = estiloSubtramo(medidas.titulo),
            color = ColoresGuiado.Texto.copy(alpha = OPACIDAD_SECUNDARIO),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(ETIQUETA_SUBTRAMO_PRIMERA_VEZ),
        )
    }
}

private fun descripcionTarjeta(
    recursos: Resources,
    resultado: SubtramoResultado,
    distancia: String,
    delta: Long?,
): String {
    val cierre =
        if (delta == null) {
            recursos.getString(R.string.subtramo_primera_vez_descripcion)
        } else {
            recursos.getString(R.string.crono_meta_descripcion_delta, descripcionDelta(recursos, delta))
        }
    val tiempo = descripcionTiempo(recursos, resultado.tiempoMs)
    return recursos.getString(R.string.subtramo_tarjeta_descripcion, distancia, tiempo) + " " + cierre
}
