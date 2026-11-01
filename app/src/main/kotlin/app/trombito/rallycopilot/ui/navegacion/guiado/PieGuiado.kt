package app.trombito.rallycopilot.ui.navegacion.guiado

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.ruta.EstadoRuta
import app.trombito.rallycopilot.ui.navegacion.NumeroParada
import java.time.ZoneId

const val ETIQUETA_PIE_GUIADO = "pie_guiado"
const val ETIQUETA_PIE_HORA = "pie_guiado_hora"
const val ETIQUETA_PIE_TIEMPO = "pie_guiado_tiempo"
const val ETIQUETA_PIE_DISTANCIA = "pie_guiado_distancia"
const val ETIQUETA_PIE_PARADA = "pie_guiado_parada"
const val ETIQUETA_PIE_FINAL = "pie_guiado_final"

private val FORMA = RoundedCornerShape(10.dp)

/** Lo que falta hasta la próxima parada: su número, la hora de llegada, el tiempo y la distancia. */
data class ProximaParada(
    val numero: Int,
    val llegadaMs: Long,
    val tiempoS: Double,
    val distanciaM: Double,
)

/**
 * Pie de la guía: hora de llegada (latón), tiempo restante y kilómetros restantes, en cursiva negrita sobre carbón.
 * Cada dato lleva su icono (bandera, reloj, regla); TalkBack lee una sola frase. Con [proxima] esos tres datos son los
 * de la próxima parada (con su número delante) y, más pequeño, debajo van los del final.
 */
@Composable
fun PieGuiado(
    progreso: EstadoRuta,
    modifier: Modifier = Modifier,
    zona: ZoneId = ZoneId.systemDefault(),
    proxima: ProximaParada? = null,
) {
    val hora = formatearHoraLlegada(progreso.llegadaMs, zona)
    val tiempo = formatearTiempoRestante(progreso.tiempoRestanteS)
    val distancia = formatearDistanciaRestante(progreso.distanciaRestanteM)
    val principal =
        proxima?.let {
            Triple(
                formatearHoraLlegada(it.llegadaMs, zona),
                formatearTiempoRestante(it.tiempoS),
                formatearDistanciaRestante(it.distanciaM),
            )
        }
    val descripcion =
        if (principal == null) {
            stringResource(R.string.guiado_pie_descripcion, hora, tiempo, distancia)
        } else {
            stringResource(
                R.string.guiado_pie_parada_descripcion,
                principal.first,
                principal.second,
                principal.third,
                hora,
                tiempo,
                distancia,
            )
        }
    Column(
        modifier =
            modifier
                .testTag(ETIQUETA_PIE_GUIADO)
                .background(ColoresGuiado.Carbon, FORMA)
                .border(2.dp, ColoresGuiado.Laton, FORMA)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .semantics { contentDescription = descripcion },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FilaPrincipal(proxima, principal, Triple(hora, tiempo, distancia))
        if (principal != null) {
            Text(
                text = "${stringResource(R.string.guiado_pie_final_etiqueta)}  $hora · $tiempo · $distancia",
                modifier = Modifier.testTag(ETIQUETA_PIE_FINAL),
                color = ColoresGuiado.Texto,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                    ),
                maxLines = 1,
            )
        }
    }
}

/** Los tres datos grandes: los de la próxima parada (con su número) si la hay y, si no, los del final. */
@Composable
private fun FilaPrincipal(
    proxima: ProximaParada?,
    deProxima: Triple<String, String, String>?,
    deFinal: Triple<String, String, String>,
) {
    val datos = deProxima ?: deFinal
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (proxima != null) NumeroParada(proxima.numero, Modifier.testTag(ETIQUETA_PIE_PARADA))
        Dato(IconosGuiado.Bandera, datos.first, ColoresGuiado.Laton, ETIQUETA_PIE_HORA)
        Dato(IconosGuiado.Reloj, datos.second, ColoresGuiado.Texto, ETIQUETA_PIE_TIEMPO)
        Dato(IconosGuiado.Regla, datos.third, ColoresGuiado.Texto, ETIQUETA_PIE_DISTANCIA)
    }
}

@Composable
private fun Dato(
    icono: ImageVector,
    texto: String,
    color: Color,
    etiqueta: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icono, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(
            text = texto,
            modifier = Modifier.testTag(etiqueta),
            color = color,
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                ),
            maxLines = 1,
        )
    }
}
