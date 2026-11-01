package app.trombito.rallycopilot.ui.alertas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.navegacion.guiado.ColoresGuiado

const val ETIQUETA_ALERTA_FRESCURA = "alerta-frescura"

private const val OPACIDAD_SECUNDARIO = 0.75f

/**
 * Icono (reloj si los datos son viejos, nube tachada si el último refresco falló) y «hace 2 h». Pequeño y discreto:
 * no es una alarma, solo dice de cuándo son las incidencias.
 */
@Composable
internal fun IndicadorFrescura(
    frescura: FrescuraIncidencias,
    modifier: Modifier = Modifier,
    color: Color = ColoresGuiado.Texto.copy(alpha = OPACIDAD_SECUNDARIO),
) {
    val recursos = LocalContext.current.resources
    val icono = if (frescura.conError) R.drawable.ic_alerta_sin_red else R.drawable.ic_alerta_reloj
    Row(
        modifier = modifier.testTag(ETIQUETA_ALERTA_FRESCURA),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icono), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            text = textoAntiguedad(recursos, frescura.antiguedadMs),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
}
