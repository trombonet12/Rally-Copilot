package app.trombito.rallycopilot.ui.mapasoffline

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.datos.EstadoDetalle
import app.trombito.rallycopilot.domain.datos.ProgresoDetalle
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_DETALLE = "mapas-detalle"
const val ETIQUETA_DETALLE_ZONA = "mapas-detalle-zona"
const val ETIQUETA_DETALLE_BORRAR = "mapas-detalle-borrar"
const val ETIQUETA_DETALLE_PROGRESO = "mapas-detalle-progreso"

private val ALTO_MINIMO_TACTIL = 48.dp

/** Detalle z18-19 de Mapas offline: lo bajado, el progreso, Descargar zona y Borrar detalle. */
@Composable
internal fun SeccionDetalle(
    detalle: EstadoDetalle,
    acciones: AccionesMapasOffline,
) {
    val contexto = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_DETALLE),
    ) {
        Column(Modifier.padding(Espacio.l), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            Text(
                text = stringResource(R.string.mapas_detalle_titulo),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.mapas_detalle_texto), style = MaterialTheme.typography.bodyMedium)
            Text(
                text =
                    if (detalle.teselas > 0) {
                        stringResource(
                            R.string.mapas_detalle_tamano,
                            Formatter.formatShortFileSize(contexto, detalle.bytes),
                            detalle.teselas,
                        )
                    } else {
                        stringResource(R.string.mapas_detalle_vacio)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            detalle.descarga?.let { ProgresoDetalleFila(it, acciones.onCancelarDetalle) }
            Button(
                onClick = acciones.onDescargarZona,
                modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_MINIMO_TACTIL).testTag(ETIQUETA_DETALLE_ZONA),
            ) { Text(stringResource(R.string.mapas_detalle_zona)) }
            if (detalle.bytes > 0) {
                OutlinedButton(
                    onClick = acciones.onBorrarDetalle,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier =
                        Modifier.fillMaxWidth().heightIn(min = ALTO_MINIMO_TACTIL).testTag(ETIQUETA_DETALLE_BORRAR),
                ) { Text(stringResource(R.string.mapas_detalle_borrar)) }
            }
        }
    }
}

@Composable
private fun ProgresoDetalleFila(
    progreso: ProgresoDetalle,
    onCancelar: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().testTag(ETIQUETA_DETALLE_PROGRESO),
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        if (progreso.totales > 0) {
            LinearProgressIndicator(
                progress = { progreso.hechas.toFloat() / progreso.totales },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Text(
            text =
                stringResource(
                    if (progreso.esperando) R.string.mapas_detalle_esperando else R.string.mapas_detalle_progreso,
                    progreso.hechas,
                    progreso.totales,
                ),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onCancelar, modifier = Modifier.heightIn(min = ALTO_MINIMO_TACTIL)) {
            Text(stringResource(R.string.mapas_cancelar))
        }
    }
}
