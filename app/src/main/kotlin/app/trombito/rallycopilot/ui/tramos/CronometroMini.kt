package app.trombito.rallycopilot.ui.tramos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.ui.comun.Iconos

const val ETIQUETA_CRONO_MINI = "crono-mini"

/**
 * Cronómetro pequeño de la conducción, para tramos nuevos o sin tramo: icono y «mm:ss» (sin décimas, para no competir
 * con las notas). Va en el panel inferior, junto a la velocidad; usa los colores del tema del panel. Sin región viva:
 * TalkBack lo lee solo si se enfoca.
 */
@Composable
fun CronometroMini(
    crono: CronoEnCurso,
    modifier: Modifier = Modifier,
    reloj: () -> Long = System::currentTimeMillis,
) {
    val transcurrido = rememberTranscurridoMs(crono, reloj, PERIODO_MINI_MS)
    val recursos = LocalContext.current.resources
    val descripcion = recursos.getString(R.string.crono_mini_descripcion, descripcionTiempo(recursos, transcurrido))
    Row(
        modifier =
            modifier
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
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = formatearTiempo(transcurrido, conDecimas = false),
            style =
                TextStyle(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(ETIQUETA_CRONO_TIEMPO),
        )
    }
}
