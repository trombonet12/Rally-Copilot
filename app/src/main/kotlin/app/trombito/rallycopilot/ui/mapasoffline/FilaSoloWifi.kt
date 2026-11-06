package app.trombito.rallycopilot.ui.mapasoffline

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo

const val ETIQUETA_SOLO_WIFI = "mapas-solo-wifi"
const val ETIQUETA_DATOS_CONFIRMAR = "mapas-datos-confirmar"
const val ETIQUETA_DATOS_CANCELAR = "mapas-datos-cancelar"

private val ALTO_MINIMO_TACTIL = 48.dp

/** Interruptor «Solo con WiFi»: desactivarlo pide confirmar, porque la descarga podría gastar datos móviles. */
@Composable
internal fun FilaSoloWifi(
    estado: MapasOfflineUiState,
    acciones: AccionesMapasOffline,
) {
    val soloWifi = estado.ajustes.soloWifi
    var confirmarDatos by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag(ETIQUETA_SOLO_WIFI)
                .toggleable(
                    value = soloWifi,
                    role = Role.Switch,
                    // Desactivarlo pide confirmación: la descarga podría gastar datos móviles.
                    onValueChange = { nuevo -> if (nuevo) acciones.onSoloWifi(true) else confirmarDatos = true },
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.mapas_solo_wifi), style = Cuerpo, fontWeight = FontWeight.SemiBold)
            Text(
                text = stringResource(R.string.mapas_solo_wifi_detalle),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = soloWifi, onCheckedChange = null)
    }
    if (confirmarDatos) {
        DialogoDatosMoviles(
            bytes = estado.bytesPendientes,
            onConfirmar = {
                confirmarDatos = false
                acciones.onSoloWifi(false)
            },
            onCancelar = { confirmarDatos = false },
        )
    }
}

@Composable
private fun DialogoDatosMoviles(
    bytes: Long?,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    val contexto = LocalContext.current
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.mapas_datos_titulo)) },
        text = {
            Text(
                if (bytes == null) {
                    stringResource(R.string.mapas_datos_texto_sin_tamano)
                } else {
                    stringResource(R.string.mapas_datos_texto, Formatter.formatShortFileSize(contexto, bytes))
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmar,
                modifier = Modifier.heightIn(min = ALTO_MINIMO_TACTIL).testTag(ETIQUETA_DATOS_CONFIRMAR),
            ) { Text(stringResource(R.string.mapas_datos_confirmar), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(
                onClick = onCancelar,
                modifier = Modifier.heightIn(min = ALTO_MINIMO_TACTIL).testTag(ETIQUETA_DATOS_CANCELAR),
            ) { Text(stringResource(R.string.mapas_datos_cancelar)) }
        },
    )
}
