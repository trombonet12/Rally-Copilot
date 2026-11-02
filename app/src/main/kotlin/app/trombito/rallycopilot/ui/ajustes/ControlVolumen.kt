package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio
import kotlin.math.roundToInt

const val ETIQUETA_AJUSTES_VOLUMEN = "ajustes-volumen"
const val ETIQUETA_AJUSTES_VOLUMEN_VALOR = "ajustes-volumen-valor"

private const val PASO_VOLUMEN = 10
private val ANCHO_VALOR = 56.dp

/**
 * Deslizador del volumen del copiloto: de 50 a 150 % de 10 en 10 (100 % es el volumen tal cual). Cada paso se guarda
 * y se aplica al instante; la cifra se ve al lado.
 */
@Composable
internal fun ControlVolumen(
    volumen: Int,
    onVolumen: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val descripcion = stringResource(R.string.ajustes_volumen_descripcion)
    val estado = stringResource(R.string.ajustes_volumen_estado, volumen)
    val pasos = (AjustesConduccion.VOLUMEN_VOZ_MAX - AjustesConduccion.VOLUMEN_VOZ_MIN) / PASO_VOLUMEN - 1
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        Icon(Iconos.Volumen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = volumen.toFloat(),
            onValueChange = { onVolumen((it / PASO_VOLUMEN).roundToInt() * PASO_VOLUMEN) },
            valueRange = AjustesConduccion.VOLUMEN_VOZ_MIN.toFloat()..AjustesConduccion.VOLUMEN_VOZ_MAX.toFloat(),
            steps = pasos,
            modifier =
                Modifier
                    .weight(1f)
                    .testTag(ETIQUETA_AJUSTES_VOLUMEN)
                    .semantics {
                        contentDescription = descripcion
                        stateDescription = estado
                    },
        )
        Text(
            text = stringResource(R.string.ajustes_volumen_valor, volumen),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = ANCHO_VALOR).testTag(ETIQUETA_AJUSTES_VOLUMEN_VALOR),
        )
    }
}
