package app.trombito.rallycopilot.ui.mapasoffline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.datos.EstadoDetalle
import app.trombito.rallycopilot.domain.datos.ProgresoDetalle
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas

const val ETIQUETA_DETALLE = "mapas-detalle"
const val ETIQUETA_DETALLE_ZONA = "mapas-detalle-zona"
const val ETIQUETA_DETALLE_BORRAR = "mapas-detalle-borrar"
const val ETIQUETA_DETALLE_BORRAR_CONFIRMAR = "mapas-detalle-borrar-confirmar"
const val ETIQUETA_DETALLE_PROGRESO = "mapas-detalle-progreso"

/**
 * Detalle z18-19 de Mapas offline: lo bajado, el progreso y «Descargar zona» como acción principal; «Borrar detalle»
 * (papelera, pide confirmar mediante [onPedirBorrado]) solo si hay algo descargado.
 */
@Composable
internal fun SeccionDetalle(
    detalle: EstadoDetalle,
    acciones: AccionesMapasOffline,
    onPedirBorrado: () -> Unit,
) {
    TarjetaWrc(Modifier.fillMaxWidth().testTag(ETIQUETA_DETALLE)) {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            TituloTarjeta(stringResource(R.string.mapas_detalle_titulo))
            Text(stringResource(R.string.mapas_detalle_texto), style = Ayuda)
            Text(
                text =
                    if (detalle.teselas > 0) {
                        stringResource(R.string.mapas_detalle_tamano, FormatoEs.tamano(detalle.bytes), detalle.teselas)
                    } else {
                        stringResource(R.string.mapas_detalle_vacio)
                    },
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            detalle.descarga?.let { ProgresoDetalleFila(it, acciones.onCancelarDetalle) }
            Button(
                onClick = acciones.onDescargarZona,
                modifier = Modifier.fillMaxWidth().heightIn(min = Medidas.zonaTactil).testTag(ETIQUETA_DETALLE_ZONA),
            ) { Text(stringResource(R.string.mapas_detalle_zona)) }
            if (detalle.bytes > 0) {
                BotonBorrar(R.string.mapas_detalle_borrar, ETIQUETA_DETALLE_BORRAR, onPedirBorrado)
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
            style = Ayuda,
        )
        TextButton(onClick = onCancelar, modifier = Modifier.heightIn(min = Medidas.zonaTactil)) {
            Text(stringResource(R.string.mapas_cancelar))
        }
    }
}
