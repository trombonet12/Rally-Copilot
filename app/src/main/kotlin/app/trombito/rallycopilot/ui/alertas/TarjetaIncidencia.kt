package app.trombito.rallycopilot.ui.alertas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.navegacion.guiado.ColoresGuiado

const val ETIQUETA_INCIDENCIA_TARJETA = "incidencia-tarjeta"
const val ETIQUETA_INCIDENCIA_CERRAR = "incidencia-cerrar"
const val ETIQUETA_INCIDENCIA_DESCRIPCION = "incidencia-descripcion"

private const val OPACIDAD_FONDO = 0.94f
private const val LINEAS_DESCRIPCION = 3
private val FORMA = RoundedCornerShape(12.dp)
private val ANCHO_MAXIMO = 520.dp

/**
 * Tarjeta pequeña de una incidencia tocada en el mapa: icono por tipo, tipo, carretera, la descripción de la DGT y,
 * si los datos son viejos o hubo error, cuánto hace ([frescura]). Se cierra con la X (o tocando otra vez el mapa).
 */
@Composable
fun TarjetaIncidencia(
    marcador: MarcadorIncidencia,
    frescura: FrescuraIncidencias?,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recursos = LocalContext.current.resources
    val color = colorIncidencia(marcador.tipo)
    Row(
        modifier =
            modifier
                .widthIn(max = ANCHO_MAXIMO)
                .fillMaxWidth()
                .testTag(ETIQUETA_INCIDENCIA_TARJETA)
                .background(ColoresGuiado.Carbon.copy(OPACIDAD_FONDO), FORMA)
                .border(2.dp, color, FORMA)
                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconoIncidencia(marcador.tipo)),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(36.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = recursos.getString(tituloIncidencia(marcador.tipo)),
                color = color,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            marcador.carretera?.let {
                Text(it, color = ColoresGuiado.Texto, style = MaterialTheme.typography.labelLarge)
            }
            Text(
                text = marcador.descripcion,
                color = ColoresGuiado.Texto,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = LINEAS_DESCRIPCION,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(ETIQUETA_INCIDENCIA_DESCRIPCION),
            )
            frescura?.let { IndicadorFrescura(it) }
        }
        BotonIcono(
            descripcion = stringResource(R.string.alerta_tarjeta_cerrar),
            onClick = onCerrar,
            modifier = Modifier.testTag(ETIQUETA_INCIDENCIA_CERRAR),
        ) { Icon(Iconos.Cerrar, contentDescription = null, tint = ColoresGuiado.Texto) }
    }
}
