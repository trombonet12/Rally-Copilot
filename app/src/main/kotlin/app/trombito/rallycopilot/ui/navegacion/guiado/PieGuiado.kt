package app.trombito.rallycopilot.ui.navegacion.guiado

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import java.time.ZoneId

const val ETIQUETA_PIE_GUIADO = "pie_guiado"
const val ETIQUETA_PIE_HORA = "pie_guiado_hora"
const val ETIQUETA_PIE_TIEMPO = "pie_guiado_tiempo"
const val ETIQUETA_PIE_DISTANCIA = "pie_guiado_distancia"

private val FORMA = RoundedCornerShape(10.dp)

/**
 * Pie de la guía: hora de llegada (latón), tiempo restante y kilómetros restantes, en cursiva negrita sobre carbón.
 * Cada dato lleva su icono (bandera, reloj, regla); TalkBack lee una sola frase.
 */
@Composable
fun PieGuiado(
    progreso: EstadoRuta,
    modifier: Modifier = Modifier,
    zona: ZoneId = ZoneId.systemDefault(),
) {
    val hora = formatearHoraLlegada(progreso.llegadaMs, zona)
    val tiempo = formatearTiempoRestante(progreso.tiempoRestanteS)
    val distancia = formatearDistanciaRestante(progreso.distanciaRestanteM)
    val descripcion = stringResource(R.string.guiado_pie_descripcion, hora, tiempo, distancia)
    Row(
        modifier =
            modifier
                .testTag(ETIQUETA_PIE_GUIADO)
                .background(ColoresGuiado.Carbon, FORMA)
                .border(2.dp, ColoresGuiado.Laton, FORMA)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .semantics { contentDescription = descripcion },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dato(IconosGuiado.Bandera, hora, ColoresGuiado.Laton, ETIQUETA_PIE_HORA)
        Dato(IconosGuiado.Reloj, tiempo, ColoresGuiado.Texto, ETIQUETA_PIE_TIEMPO)
        Dato(IconosGuiado.Regla, distancia, ColoresGuiado.Texto, ETIQUETA_PIE_DISTANCIA)
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
