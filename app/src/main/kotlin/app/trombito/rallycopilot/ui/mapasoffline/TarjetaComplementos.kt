package app.trombito.rallycopilot.ui.mapasoffline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.datos.EstadoComplemento
import app.trombito.rallycopilot.motor.datos.Complemento
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas

const val ETIQUETA_COMPLEMENTOS = "mapas-complementos"
const val ETIQUETA_COMPLEMENTO_DESCARGAR_PREFIJO = "mapas-complemento-descargar-"
const val ETIQUETA_COMPLEMENTO_BORRAR_PREFIJO = "mapas-complemento-borrar-"
const val ETIQUETA_COMPLEMENTO_BORRAR_CONFIRMAR = "mapas-complemento-borrar-confirmar"

private const val PORCIENTO = 100f

/** Los datos que no van en el APK y se bajan al elegirlos; el satélite tiene su propia tarjeta. */
private val COMPLEMENTOS_MOSTRADOS = listOf(Complemento.RELIEVE, Complemento.VOZ_SHARVARD)

private fun Complemento.nombre(): Int =
    if (this == Complemento.RELIEVE) R.string.complementos_relieve else R.string.complementos_voz_sharvard

/**
 * Relieve con curvas de nivel y voz Sharvard: cada uno dice si está instalado (y cuánto ocupa) o su tamaño, con el
 * botón de descargar, el progreso o la papelera. Borrar libera el espacio y se puede volver a descargar.
 */
@Composable
internal fun TarjetaComplementos(
    estado: MapasOfflineUiState,
    acciones: AccionesMapasOffline,
) {
    var borrando by rememberSaveable { mutableStateOf<String?>(null) }
    TarjetaWrc(Modifier.fillMaxWidth().testTag(ETIQUETA_COMPLEMENTOS)) {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.m)) {
            TituloTarjeta(stringResource(R.string.complementos_titulo))
            COMPLEMENTOS_MOSTRADOS.forEach { complemento ->
                FilaComplemento(
                    complemento = complemento,
                    estado = estado.datos.complemento(complemento),
                    onDescargar = { acciones.onDescargarComplemento(complemento) },
                    onBorrar = { borrando = complemento.name },
                )
            }
        }
    }
    val aBorrar = COMPLEMENTOS_MOSTRADOS.firstOrNull { it.name == borrando }
    if (aBorrar != null) {
        DialogoBorrarComplemento(
            complemento = aBorrar,
            bytes = estado.datos.complemento(aBorrar).bytes,
            onConfirmar = {
                borrando = null
                acciones.onBorrarComplemento(aBorrar)
            },
            onCancelar = { borrando = null },
        )
    }
}

@Composable
private fun FilaComplemento(
    complemento: Complemento,
    estado: EstadoComplemento,
    onDescargar: () -> Unit,
    onBorrar: () -> Unit,
) {
    val nombre = stringResource(complemento.nombre())
    val clave = complemento.name.lowercase()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            Text(nombre, style = Cuerpo)
            EstadoDeComplemento(complemento, estado)
        }
        if (estado.instalado) {
            BotonIcono(
                descripcion = stringResource(R.string.complementos_borrar_descripcion, nombre),
                onClick = onBorrar,
                modifier = Modifier.testTag(ETIQUETA_COMPLEMENTO_BORRAR_PREFIJO + clave),
            ) { Icon(Iconos.Borrar, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
        } else if (estado.descarga == null) {
            Button(
                onClick = onDescargar,
                modifier =
                    Modifier
                        .heightIn(min = Medidas.zonaTactil)
                        .testTag(ETIQUETA_COMPLEMENTO_DESCARGAR_PREFIJO + clave),
            ) { Text(stringResource(R.string.complementos_descargar)) }
        }
    }
}

/** La línea de estado: ocupa X, descargando con barra, esperando red o el tamaño a bajar. */
@Composable
private fun EstadoDeComplemento(
    complemento: Complemento,
    estado: EstadoComplemento,
) {
    val descarga = estado.descarga
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        estado.instalado -> {
            Text(
                stringResource(R.string.complementos_instalado, FormatoEs.tamano(estado.bytes)),
                Modifier,
                color,
                style = Ayuda,
            )
        }

        descarga != null && !descarga.esperandoRed -> {
            val total = descarga.bytesTotales
            val fraccion = if (total > 0) (descarga.bytesHechos.toFloat() / total).coerceIn(0f, 1f) else 0f
            val porcentaje = (fraccion * PORCIENTO).toInt()
            Text(stringResource(R.string.complementos_descargando, porcentaje), Modifier, color, style = Ayuda)
            LinearProgressIndicator(progress = { fraccion }, modifier = Modifier.fillMaxWidth())
        }

        descarga != null -> {
            Text(stringResource(R.string.complementos_esperando), Modifier, color, style = Ayuda)
        }

        else -> {
            val tamano = FormatoEs.tamano(complemento.bytesAproximados)
            Text(stringResource(R.string.complementos_no_instalado, tamano), Modifier, color, style = Ayuda)
        }
    }
}

@Composable
private fun DialogoBorrarComplemento(
    complemento: Complemento,
    bytes: Long,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    val nombre = stringResource(complemento.nombre())
    val texto = stringResource(R.string.complementos_borrar_texto, FormatoEs.tamano(bytes))
    val aviso =
        if (complemento ==
            Complemento.VOZ_SHARVARD
        ) {
            stringResource(R.string.complementos_borrar_voz_extra)
        } else {
            ""
        }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.complementos_borrar_titulo, nombre)) },
        text = { Text(if (aviso.isEmpty()) texto else "$texto $aviso") },
        confirmButton = {
            TextButton(
                onClick = onConfirmar,
                modifier = Modifier.heightIn(min = Medidas.zonaTactil).testTag(ETIQUETA_COMPLEMENTO_BORRAR_CONFIRMAR),
            ) { Text(stringResource(R.string.mapas_borrar_confirmar), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, modifier = Modifier.heightIn(min = Medidas.zonaTactil)) {
                Text(stringResource(R.string.mapas_borrar_cancelar))
            }
        },
    )
}
