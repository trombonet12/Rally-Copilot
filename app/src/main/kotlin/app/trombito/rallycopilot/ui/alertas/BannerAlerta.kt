package app.trombito.rallycopilot.ui.alertas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.alertas.TipoIncidencia
import app.trombito.rallycopilot.ui.maniobras.ParalelogramoShape
import app.trombito.rallycopilot.ui.navegacion.guiado.ColoresGuiado
import app.trombito.rallycopilot.ui.theme.RallyOscuro
import java.util.Locale

const val ETIQUETA_ALERTA_BANNER = "alerta-banner"

/** Icono del aviso: `alerta-icono-FIJO`, `alerta-icono-OBRAS`... */
const val ETIQUETA_ALERTA_ICONO_PREFIJO = "alerta-icono-"
const val ETIQUETA_ALERTA_DISTANCIA = "alerta-distancia"
const val ETIQUETA_ALERTA_LIMITE = "alerta-limite"

private const val OPACIDAD_FONDO = 0.94f
private const val OPACIDAD_SECUNDARIO = 0.75f
private val ES = Locale.forLanguageTag("es-ES")
private val SESGO = 10.dp
private val SESGO_COMPACTO = 6.dp

/** Medidas del banner en vertical y en horizontal compacto. */
private class MedidasBanner(
    val icono: Dp,
    val titulo: TextUnit,
    val distancia: TextUnit,
    val senal: Dp,
    val sesgo: Dp,
    val subtitulo: Boolean,
)

private val NORMAL = MedidasBanner(40.dp, 22.sp, 24.sp, 48.dp, SESGO, subtitulo = true)
private val COMPACTO = MedidasBanner(32.dp, 18.sp, 20.sp, 36.dp, SESGO_COMPACTO, subtitulo = false)

/** Guarda el último aviso para poder dibujarlo mientras sale deslizando. */
private class UltimoAviso {
    var valor: AvisoAlerta? = null
}

/**
 * Banner de alerta tipo retransmisión WRC: entra deslizando desde arriba y sale al pasar el radar o la incidencia.
 * Va bajo la franja de notas o el banner de maniobra. Radar: icono, nombre, señal con el límite y distancia.
 * Incidencia de la ruta: icono por tipo, nombre, carretera, distancia y, si los datos son viejos o hubo error, cuánto
 * hace ([frescura]). Con [compacto] (apaisado) todo va en una fila baja. Región viva: TalkBack lo anuncia al aparecer.
 */
@Composable
fun BannerAlerta(
    aviso: AvisoAlerta?,
    frescura: FrescuraIncidencias?,
    modifier: Modifier = Modifier,
    compacto: Boolean = false,
) {
    val ultimo = remember { UltimoAviso() }
    if (aviso != null) ultimo.valor = aviso
    AnimatedVisibility(
        visible = aviso != null,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        ultimo.valor?.let { ContenidoBanner(it, frescura, if (compacto) COMPACTO else NORMAL) }
    }
}

@Composable
private fun ContenidoBanner(
    aviso: AvisoAlerta,
    frescura: FrescuraIncidencias?,
    medidas: MedidasBanner,
) {
    val recursos = LocalContext.current.resources
    val color = colorAviso(aviso)
    val forma = ParalelogramoShape(medidas.sesgo)
    val descripcion = descripcionAviso(recursos, aviso, frescura)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ETIQUETA_ALERTA_BANNER)
                .background(ColoresGuiado.Carbon.copy(OPACIDAD_FONDO), forma)
                .border(2.dp, color, forma)
                .padding(horizontal = medidas.sesgo + 8.dp, vertical = 6.dp)
                .clearAndSetSemantics {
                    contentDescription = descripcion
                    liveRegion = LiveRegionMode.Polite
                },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconoDeAviso(aviso)),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(medidas.icono).testTag(ETIQUETA_ALERTA_ICONO_PREFIJO + claveDeAviso(aviso)),
        )
        TituloYSubtitulo(aviso, frescura, medidas, Modifier.weight(1f))
        if (aviso is AvisoAlerta.Radar && aviso.limiteKmh != null) {
            SenalLimiteMini(aviso.limiteKmh, medidas.senal, Modifier.testTag(ETIQUETA_ALERTA_LIMITE))
        }
        distanciaAviso(aviso)?.let { Distancia(it, color, medidas) }
        if (!medidas.subtitulo && aviso is AvisoAlerta.Incidencia) frescura?.let { IndicadorFrescura(it) }
    }
}

@Composable
private fun TituloYSubtitulo(
    aviso: AvisoAlerta,
    frescura: FrescuraIncidencias?,
    medidas: MedidasBanner,
    modifier: Modifier,
) {
    val recursos = LocalContext.current.resources
    Column(modifier) {
        Text(
            text = tituloAviso(recursos, aviso).uppercase(ES),
            color = ColoresGuiado.Texto,
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontSize = medidas.titulo,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (medidas.subtitulo) Subtitulo(aviso, frescura)
    }
}

/** Línea pequeña bajo el nombre: «zona probable» del radar móvil o la carretera y la antigüedad de la incidencia. */
@Composable
private fun Subtitulo(
    aviso: AvisoAlerta,
    frescura: FrescuraIncidencias?,
) {
    when {
        aviso is AvisoAlerta.Radar && aviso.clase == ClaseRadar.MOVIL -> {
            TextoSecundario(stringResource(R.string.alerta_radar_movil_probable))
        }

        aviso is AvisoAlerta.Incidencia -> {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                aviso.carretera?.let { TextoSecundario(it, Modifier.weight(1f, fill = false)) }
                frescura?.let { IndicadorFrescura(it) }
            }
        }
    }
}

@Composable
private fun TextoSecundario(
    texto: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = texto,
        color = ColoresGuiado.Texto.copy(alpha = OPACIDAD_SECUNDARIO),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun Distancia(
    texto: String,
    color: Color,
    medidas: MedidasBanner,
) {
    Text(
        text = texto,
        color = color,
        style =
            MaterialTheme.typography.titleLarge.copy(
                fontSize = medidas.distancia,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
            ),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.testTag(ETIQUETA_ALERTA_DISTANCIA),
    )
}
