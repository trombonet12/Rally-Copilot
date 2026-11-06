package app.trombito.rallycopilot.ui.navegacion.guiado

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.ruta.EstadoRuta
import app.trombito.rallycopilot.ui.navegacion.NumeroParada
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally
import java.time.ZoneId

const val ETIQUETA_PIE_GUIADO = "pie_guiado"
const val ETIQUETA_PIE_HORA = "pie_guiado_hora"
const val ETIQUETA_PIE_TIEMPO = "pie_guiado_tiempo"
const val ETIQUETA_PIE_DISTANCIA = "pie_guiado_distancia"
const val ETIQUETA_PIE_PARADA = "pie_guiado_parada"
const val ETIQUETA_PIE_FINAL = "pie_guiado_final"

/** Lo que falta hasta la próxima parada: su número, la hora de llegada, el tiempo y la distancia. */
data class ProximaParada(
    val numero: Int,
    val llegadaMs: Long,
    val tiempoS: Double,
    val distanciaM: Double,
)

/**
 * Centro de la hoja de conducción mientras se guía: el tiempo restante en 24 sp («17 min», en verde si [adelanta] al
 * mejor tiempo del tramo) y debajo, en 16 sp, los kilómetros restantes y la hora de llegada («9,6 km · 07:43»).
 * Con [proxima] esos datos son los de la próxima parada (con su número delante) y, en 12 sp, debajo van los del final.
 * TalkBack lee una sola frase en el contenedor.
 */
@Composable
fun PieGuiado(
    progreso: EstadoRuta,
    modifier: Modifier = Modifier,
    zona: ZoneId = ZoneId.systemDefault(),
    proxima: ProximaParada? = null,
    adelanta: Boolean = false,
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
    val datos = principal ?: Triple(hora, tiempo, distancia)
    val colores = LocalColoresRally.current
    val colorTiempo = if (adelanta) colores.deltaMejor else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier.testTag(ETIQUETA_PIE_GUIADO).semantics { contentDescription = descripcion },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s), verticalAlignment = Alignment.CenterVertically) {
            if (proxima != null) NumeroParada(proxima.numero, Modifier.testTag(ETIQUETA_PIE_PARADA), lado = 24.dp)
            Dato(datos.second, 24.sp, FontWeight.Black, colorTiempo, ETIQUETA_PIE_TIEMPO)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val secundario = MaterialTheme.colorScheme.onSurfaceVariant
            Dato(datos.third, 16.sp, FontWeight.Bold, secundario, ETIQUETA_PIE_DISTANCIA)
            Text(text = " · ", fontSize = 16.sp, color = secundario)
            Dato(datos.first, 16.sp, FontWeight.Bold, MaterialTheme.colorScheme.primary, ETIQUETA_PIE_HORA)
        }
        if (principal != null) {
            Text(
                text = "${stringResource(R.string.guiado_pie_final_etiqueta)}  $hora · $tiempo · $distancia",
                modifier = Modifier.testTag(ETIQUETA_PIE_FINAL),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Dato(
    texto: String,
    tamano: TextUnit,
    peso: FontWeight,
    color: Color,
    etiqueta: String,
) {
    Text(
        text = texto,
        modifier = Modifier.testTag(etiqueta),
        color = color,
        fontSize = tamano,
        fontWeight = peso,
        fontStyle = FontStyle.Italic,
        maxLines = 1,
        softWrap = false,
    )
}
