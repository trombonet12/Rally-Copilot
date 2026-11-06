package app.trombito.rallycopilot.ui.tramos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.navegacion.guiado.ColoresGuiado
import app.trombito.rallycopilot.ui.theme.CifraMedia

const val ETIQUETA_CRONO_MINI = "crono-mini"

/** El botón «SALIDA» de la salida manual, junto al «LISTO». */
const val ETIQUETA_CRONO_SALIDA = "crono-salida"

/** Tamaño del cronómetro pequeño: el de siempre, el de 20 sp del guiado o el grande ámbar de la hoja libre. */
enum class EscalaCronoMini { NORMAL, COMPACTO, GRANDE }

private val ALTO_BOTON_SALIDA = 56.dp
private val AMBAR_SALIDA = Color(0xFFFFC94A)

/**
 * Cronómetro pequeño de la conducción, para tramos nuevos o sin tramo: icono y «mm:ss» (sin décimas, para no competir
 * con las notas). Va en el panel inferior, junto a la velocidad; usa los colores del tema del panel. Sin región viva:
 * TalkBack lo lee solo si se enfoca. Mientras la conducción está lista (sin salida aún, [CronoEnCurso.enMarcha] false)
 * muestra «LISTO» con un pulso suave de alfa en vez de 00:00. Si además la salida es manual
 * ([CronoEnCurso.esperaSalidaManual]) y hay [onSalida], a su lado va el botón grande ámbar «SALIDA» (56 dp de alto).
 */
@Composable
fun CronometroMini(
    crono: CronoEnCurso,
    modifier: Modifier = Modifier,
    reloj: () -> Long = System::currentTimeMillis,
    onSalida: (() -> Unit)? = null,
    escala: EscalaCronoMini = EscalaCronoMini.NORMAL,
) {
    val transcurrido = rememberTranscurridoMs(crono, reloj, PERIODO_MINI_MS)
    val recursos = LocalContext.current.resources
    val listo = !crono.enMarcha
    val descripcion =
        if (listo) {
            recursos.getString(R.string.crono_listo_descripcion)
        } else {
            recursos.getString(R.string.crono_mini_descripcion, descripcionTiempo(recursos, transcurrido))
        }
    val alfa = if (listo) rememberAlfaListo() else 1f
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cifras(listo, transcurrido, descripcion, alfa, escala)
        if (listo && crono.esperaSalidaManual && onSalida != null) BotonSalida(onSalida)
    }
}

/** Icono y «mm:ss» (o «LISTO»), con una sola descripción para TalkBack. */
@Composable
private fun Cifras(
    listo: Boolean,
    transcurrido: Long,
    descripcion: String,
    alfa: Float,
    escala: EscalaCronoMini,
) {
    val grande = escala == EscalaCronoMini.GRANDE
    Row(
        modifier =
            Modifier
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .testTag(ETIQUETA_CRONO_MINI)
                .clearAndSetSemantics { contentDescription = descripcion },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Iconos.Cronometro,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(if (grande) 28.dp else 20.dp),
        )
        Text(
            text =
                if (listo) {
                    stringResource(
                        R.string.crono_listo,
                    )
                } else {
                    formatearTiempo(transcurrido, conDecimas = false)
                },
            style =
                when (escala) {
                    EscalaCronoMini.GRANDE -> {
                        CifraMedia.copy(fontFeatureSettings = "tnum")
                    }

                    EscalaCronoMini.COMPACTO -> {
                        TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
                    }

                    EscalaCronoMini.NORMAL -> {
                        TextStyle(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                        )
                    }
                },
            color = if (grande) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.alpha(alfa).testTag(ETIQUETA_CRONO_TIEMPO),
        )
    }
}

/** Botón grande ámbar «SALIDA»: abre la cuenta atrás de la salida manual. */
@Composable
private fun BotonSalida(onSalida: () -> Unit) {
    val descripcion = stringResource(R.string.crono_salida_descripcion)
    Button(
        onClick = onSalida,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AMBAR_SALIDA, contentColor = ColoresGuiado.Carbon),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier =
            Modifier
                .heightIn(min = ALTO_BOTON_SALIDA)
                .testTag(ETIQUETA_CRONO_SALIDA)
                .semantics { contentDescription = descripcion },
    ) {
        Text(
            text = stringResource(R.string.crono_salida_boton),
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic),
            maxLines = 1,
            softWrap = false,
        )
    }
}
