package app.trombito.rallycopilot.ui.mapasoffline

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.datos.MotivoError
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_FASE = "mapas-fase"
const val ETIQUETA_INSTALADOS = "mapas-instalados"
const val ETIQUETA_PROGRESO = "mapas-progreso"
const val ETIQUETA_ESPERA = "mapas-espera"
const val ETIQUETA_ERROR = "mapas-error"
const val ETIQUETA_DESCARGAR = "mapas-descargar"
const val ETIQUETA_CANCELAR = "mapas-cancelar"
const val ETIQUETA_BORRAR = "mapas-borrar"
const val ETIQUETA_BORRAR_CONFIRMAR = "mapas-borrar-confirmar"
const val ETIQUETA_SOLO_WIFI = "mapas-solo-wifi"
const val ETIQUETA_LIMITE_PREFIJO = "mapas-limite-"

private val ALTO_MINIMO_TACTIL = 48.dp

/** Callbacks de Mapas offline. */
data class AccionesMapasOffline(
    val onVolver: () -> Unit = {},
    val onDescargar: () -> Unit = {},
    val onCancelar: () -> Unit = {},
    val onBorrar: () -> Unit = {},
    val onSoloWifi: (Boolean) -> Unit = {},
    val onLimiteGb: (Int) -> Unit = {},
)

@StringRes
internal fun textoError(motivo: MotivoError): Int =
    when (motivo) {
        MotivoError.SIN_RED -> R.string.mapas_error_sin_red
        MotivoError.SUPERA_LIMITE -> R.string.mapas_error_supera_limite
        MotivoError.SIN_ESPACIO -> R.string.mapas_error_sin_espacio
        MotivoError.HASH_INCORRECTO -> R.string.mapas_error_hash
        MotivoError.MANIFEST_INVALIDO -> R.string.mapas_error_manifest
        MotivoError.DESCONOCIDO -> R.string.mapas_error_desconocido
    }

@Composable
fun MapasOfflineScreen(
    estado: MapasOfflineUiState,
    acciones: AccionesMapasOffline,
    modifier: Modifier = Modifier,
) {
    var confirmarBorrado by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .testTag(ETIQUETA_FASE + "-" + estado.fase.name.lowercase()),
    ) {
        BarraSuperior(stringResource(R.string.mapas_titulo), acciones.onVolver)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Espacio.l),
            verticalArrangement = Arrangement.spacedBy(Espacio.l),
        ) {
            if (estado.fase == FaseMapas.CARGANDO) {
                CircularProgressIndicator()
            } else {
                ContenidoMapas(estado, acciones) { confirmarBorrado = true }
            }
            Spacer(Modifier.height(Espacio.xl))
        }
    }
    if (confirmarBorrado) {
        DialogoBorrar(
            onConfirmar = {
                confirmarBorrado = false
                acciones.onBorrar()
            },
            onCancelar = { confirmarBorrado = false },
        )
    }
}

@Composable
private fun ContenidoMapas(
    estado: MapasOfflineUiState,
    acciones: AccionesMapasOffline,
    onPedirBorrado: () -> Unit,
) {
    TarjetaEstado(estado)
    estado.datos.descarga?.let { Progreso(estado, it.esperandoRed) }
    estado.datos.error?.let { Error(it) }
    when (estado.fase) {
        FaseMapas.DESCARGANDO -> {
            OutlinedButton(
                onClick = acciones.onCancelar,
                modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_MINIMO_TACTIL).testTag(ETIQUETA_CANCELAR),
            ) { Text(stringResource(R.string.mapas_cancelar)) }
        }

        FaseMapas.SIN_DATOS -> {
            BotonDescargar(estado.datos.error != null, acciones.onDescargar)
        }

        else -> {
            if (estado.datos.error != null) BotonDescargar(true, acciones.onDescargar)
        }
    }
    AjustesDescarga(estado, acciones)
    if (estado.fase == FaseMapas.INSTALADO) {
        OutlinedButton(
            onClick = onPedirBorrado,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_MINIMO_TACTIL).testTag(ETIQUETA_BORRAR),
        ) { Text(stringResource(R.string.mapas_borrar)) }
    }
}

@Composable
private fun BotonDescargar(
    reintento: Boolean,
    onDescargar: () -> Unit,
) {
    Button(
        onClick = onDescargar,
        modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_MINIMO_TACTIL).testTag(ETIQUETA_DESCARGAR),
    ) { Text(stringResource(if (reintento) R.string.mapas_reintentar else R.string.mapas_descargar)) }
}

@Composable
private fun TarjetaEstado(estado: MapasOfflineUiState) {
    val contexto = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_INSTALADOS),
    ) {
        Column(Modifier.padding(Espacio.l), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            val instalados = estado.datos.instalados
            Text(
                text = stringResource(R.string.mapas_instalados_titulo),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            if (instalados == null) {
                Text(stringResource(R.string.mapas_no_instalados), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    stringResource(R.string.mapas_version, instalados.version),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(R.string.mapas_tamano, Formatter.formatShortFileSize(contexto, instalados.bytes)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(
                        if (instalados.conSatelite) R.string.mapas_con_satelite else R.string.mapas_sin_satelite,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Progreso(
    estado: MapasOfflineUiState,
    esperandoRed: Boolean,
) {
    val contexto = LocalContext.current
    val porcentaje = estado.porcentaje
    Column(Modifier.fillMaxWidth().testTag(ETIQUETA_PROGRESO), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        if (estado.datos.instalados != null) {
            Text(stringResource(R.string.mapas_actualizando), style = MaterialTheme.typography.bodyMedium)
        }
        if (esperandoRed) {
            Text(
                text =
                    stringResource(
                        if (estado.ajustes.soloWifi) R.string.mapas_esperando_wifi else R.string.mapas_esperando_red,
                    ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.testTag(ETIQUETA_ESPERA),
            )
        }
        val progreso = estado.progreso
        if (porcentaje == null || progreso == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            // Esperando red sin total conocido: ya lo dice «Esperando…»; «Descargando…» sería contradictorio.
            if (!esperandoRed) {
                Text(stringResource(R.string.mapas_descargando), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            val descripcion = stringResource(R.string.mapas_progreso_descripcion, porcentaje)
            LinearProgressIndicator(
                progress = { porcentaje / PORCIENTO },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = descripcion },
            )
            Text(
                text =
                    stringResource(
                        R.string.mapas_progreso,
                        Formatter.formatShortFileSize(contexto, progreso.bytesHechos),
                        Formatter.formatShortFileSize(contexto, progreso.bytesTotales),
                        porcentaje,
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private const val PORCIENTO = 100f

@Composable
private fun Error(motivo: MotivoError) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_ERROR),
    ) {
        Text(
            text = stringResource(textoError(motivo)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(Espacio.l),
        )
    }
}

@Composable
private fun AjustesDescarga(
    estado: MapasOfflineUiState,
    acciones: AccionesMapasOffline,
) {
    val soloWifi = estado.ajustes.soloWifi
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag(ETIQUETA_SOLO_WIFI)
                .toggleable(value = soloWifi, role = Role.Switch, onValueChange = acciones.onSoloWifi),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.mapas_solo_wifi), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.mapas_solo_wifi_detalle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = soloWifi, onCheckedChange = null)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Text(
            text = stringResource(R.string.mapas_limite_titulo),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        val actualGb = estado.ajustes.limiteBytes / BYTES_POR_GB
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            LIMITES_GB.forEachIndexed { indice, gb ->
                val descripcion = stringResource(R.string.mapas_limite_descripcion, gb)
                SegmentedButton(
                    selected = actualGb == gb.toLong(),
                    onClick = { acciones.onLimiteGb(gb) },
                    shape = SegmentedButtonDefaults.itemShape(index = indice, count = LIMITES_GB.size),
                    modifier =
                        Modifier
                            .heightIn(min = ALTO_MINIMO_TACTIL)
                            .testTag(ETIQUETA_LIMITE_PREFIJO + gb)
                            .semantics { contentDescription = descripcion },
                ) { Text(stringResource(R.string.mapas_limite_opcion, gb)) }
            }
        }
    }
}

@Composable
private fun DialogoBorrar(
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.mapas_borrar_titulo)) },
        text = { Text(stringResource(R.string.mapas_borrar_texto)) },
        confirmButton = {
            TextButton(
                onClick = onConfirmar,
                modifier = Modifier.heightIn(min = ALTO_MINIMO_TACTIL).testTag(ETIQUETA_BORRAR_CONFIRMAR),
            ) { Text(stringResource(R.string.mapas_borrar_confirmar), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, modifier = Modifier.heightIn(min = ALTO_MINIMO_TACTIL)) {
                Text(stringResource(R.string.mapas_borrar_cancelar))
            }
        },
    )
}
