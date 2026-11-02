package app.trombito.rallycopilot.ui.tramos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.tramos.ResultadoCrono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.maniobras.ParalelogramoShape
import app.trombito.rallycopilot.ui.navegacion.guiado.ColoresGuiado

const val ETIQUETA_CRONO_META = "crono-meta"
const val ETIQUETA_CRONO_META_TOTAL = "crono-meta-total"
const val ETIQUETA_CRONO_META_DELTA = "crono-meta-delta"
const val ETIQUETA_CRONO_META_MEJOR_ANTERIOR = "crono-meta-mejor-anterior"
const val ETIQUETA_CRONO_NUEVO_MEJOR = "crono-nuevo-mejor"
const val ETIQUETA_CRONO_META_ANALISIS = "crono-meta-analisis"

private const val OPACIDAD_FONDO = 0.96f
private const val OPACIDAD_SECUNDARIO = 0.75f

/**
 * Tarjeta de resultado al cruzar la meta, tipo WRC: bandera y «META», tiempo total grande, delta contra el mejor tiempo
 * anterior (verde o rojo; sin delta si no había mejor) y «NUEVO MEJOR» sobre latón si lo mejora. Con [compacta]
 * (apaisado) va en dos filas bajas. Región viva: TalkBack la anuncia entera al aparecer. Con [onVerAnalisis] ofrece
 * debajo el botón «Ver análisis» (nunca se abre solo: solo al pulsarlo); va fuera de la región viva para que TalkBack
 * lo trate como botón.
 */
@Composable
fun TarjetaMeta(
    resultado: ResultadoCrono,
    modifier: Modifier = Modifier,
    compacta: Boolean = false,
    onVerAnalisis: (() -> Unit)? = null,
) {
    val recursos = LocalContext.current.resources
    val descripcion =
        buildString {
            append(recursos.getString(R.string.crono_meta_descripcion, descripcionTiempo(recursos, resultado.totalMs)))
            resultado.deltaMs?.let {
                append(' ')
                append(recursos.getString(R.string.crono_meta_descripcion_delta, descripcionDelta(recursos, it)))
            }
            if (resultado.nuevoMejor) {
                append(' ')
                append(recursos.getString(R.string.crono_meta_descripcion_nuevo_mejor))
            }
        }
    val sesgo = if (compacta) 6.dp else 10.dp
    val forma = ParalelogramoShape(sesgo)
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CuerpoTarjeta(resultado, descripcion, compacta, sesgo, forma)
        if (onVerAnalisis != null) BotonVerAnalisis(onVerAnalisis, sesgo)
    }
}

@Composable
private fun CuerpoTarjeta(
    resultado: ResultadoCrono,
    descripcion: String,
    compacta: Boolean,
    sesgo: Dp,
    forma: ParalelogramoShape,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ETIQUETA_CRONO_META)
                .background(ColoresGuiado.Carbon.copy(OPACIDAD_FONDO), forma)
                .border(3.dp, ColoresGuiado.Laton, forma)
                .padding(horizontal = sesgo + 12.dp, vertical = if (compacta) 6.dp else 10.dp)
                .clearAndSetSemantics {
                    contentDescription = descripcion
                    liveRegion = LiveRegionMode.Polite
                },
        verticalArrangement = Arrangement.spacedBy(if (compacta) 2.dp else 6.dp),
    ) {
        if (compacta) FilaPrincipal(resultado, compacta = true) else CuerpoVertical(resultado)
    }
}

/** «Ver análisis» sobre latón, con icono de gráfico; 48 dp de alto para pulsarlo sin fallar. */
@Composable
private fun BotonVerAnalisis(
    onVerAnalisis: () -> Unit,
    sesgo: Dp,
) {
    val etiqueta = stringResource(R.string.analisis_meta_boton_descripcion)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .defaultMinSize(minHeight = 48.dp)
                .clip(ParalelogramoShape(sesgo))
                .background(ColoresGuiado.Laton)
                .clickable(onClickLabel = etiqueta, role = Role.Button, onClick = onVerAnalisis)
                .padding(horizontal = sesgo + 14.dp)
                .testTag(ETIQUETA_CRONO_META_ANALISIS),
    ) {
        Icon(Iconos.Grafico, contentDescription = null, tint = ColoresGuiado.Carbon, modifier = Modifier.size(22.dp))
        Text(
            text = stringResource(R.string.analisis_meta_boton).uppercase(),
            style = estiloMeta(16.sp),
            color = ColoresGuiado.Carbon,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun CuerpoVertical(resultado: ResultadoCrono) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Titulo(Modifier.weight(1f))
        if (resultado.nuevoMejor) EtiquetaNuevoMejor()
    }
    FilaPrincipal(resultado, compacta = false)
    resultado.mejorAnteriorMs?.let { MejorAnterior(it) }
}

/** Tiempo total grande y delta; en apaisado lleva también el título y «NUEVO MEJOR» en la misma fila. */
@Composable
private fun FilaPrincipal(
    resultado: ResultadoCrono,
    compacta: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (compacta) Titulo(Modifier)
        Text(
            text = formatearTiempo(resultado.totalMs),
            style = estiloMeta(if (compacta) 30.sp else 44.sp),
            color = ColoresGuiado.Texto,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(ETIQUETA_CRONO_META_TOTAL),
        )
        if (!compacta) Spacer(Modifier.weight(1f))
        resultado.deltaMs?.let {
            val ancho = if (compacta) 64.dp else 90.dp
            PildoraDelta(
                it,
                if (compacta) 22.sp else 30.sp,
                ancho,
                if (compacta) 3.dp else 5.dp,
                ETIQUETA_CRONO_META_DELTA,
            )
        }
        if (compacta) {
            resultado.mejorAnteriorMs?.let { MejorAnterior(it, Modifier.weight(1f)) }
            if (resultado.nuevoMejor) EtiquetaNuevoMejor()
        }
    }
}

@Composable
private fun Titulo(modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            imageVector = Iconos.Bandera,
            contentDescription = null,
            tint = ColoresGuiado.Laton,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = stringResource(R.string.crono_meta_titulo),
            style = estiloMeta(20.sp),
            color = ColoresGuiado.Laton,
        )
    }
}

@Composable
private fun EtiquetaNuevoMejor() {
    Text(
        text = stringResource(R.string.crono_meta_nuevo_mejor),
        style = estiloMeta(18.sp),
        color = ColoresGuiado.Carbon,
        maxLines = 1,
        softWrap = false,
        modifier =
            Modifier
                .background(ColoresGuiado.Laton, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp)
                .testTag(ETIQUETA_CRONO_NUEVO_MEJOR),
    )
}

@Composable
private fun MejorAnterior(
    ms: Long,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.crono_meta_mejor_anterior, formatearTiempo(ms)),
        style = estiloMeta(14.sp).copy(fontWeight = FontWeight.Medium),
        color = ColoresGuiado.Texto.copy(alpha = OPACIDAD_SECUNDARIO),
        maxLines = 1,
        modifier = modifier.testTag(ETIQUETA_CRONO_META_MEJOR_ANTERIOR),
    )
}

private fun estiloMeta(tamano: TextUnit) =
    TextStyle(
        fontSize = tamano,
        fontWeight = FontWeight.Black,
        fontStyle = FontStyle.Italic,
        fontFeatureSettings = "tnum",
    )
