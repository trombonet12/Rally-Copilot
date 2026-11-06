package app.trombito.rallycopilot.ui.mapasoffline

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.datos.MotivoError
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.Radios

private const val PORCIENTO = 100f
private val ICONO_BOTON = 20.dp

/** Título de una tarjeta de Mapas offline: 16 sp SemiBold en ámbar. */
@Composable
internal fun TituloTarjeta(
    texto: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = texto,
        style = Cuerpo,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() },
    )
}

/** Lo instalado: versión, barra de uso («1,6 GB de 8 GB» del límite elegido) y si incluye satélite. */
@Composable
internal fun TarjetaUso(estado: MapasOfflineUiState) {
    TarjetaWrc(Modifier.fillMaxWidth().testTag(ETIQUETA_INSTALADOS)) {
        TituloTarjeta(stringResource(R.string.mapas_instalados_titulo))
        val instalados = estado.datos.instalados
        if (instalados == null) {
            Text(
                text = stringResource(R.string.mapas_no_instalados),
                style = Ayuda,
                modifier = Modifier.padding(top = Espacio.xs),
            )
        } else {
            Column(Modifier.padding(top = Espacio.xs), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                Text(stringResource(R.string.mapas_version, instalados.version), style = Cuerpo)
                BarraUso(usados = instalados.bytes + estado.detalle.bytes, limite = estado.ajustes.limiteBytes)
                Text(
                    text =
                        stringResource(
                            if (instalados.conSatelite) R.string.mapas_con_satelite else R.string.mapas_sin_satelite,
                        ),
                    style = Ayuda,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Barra de uso del espacio: lo que ocupan los mapas frente al límite elegido. */
@Composable
private fun BarraUso(
    usados: Long,
    limite: Long,
) {
    val gb = stringResource(R.string.mapas_limite_opcion, (limite / BYTES_POR_GB).toInt())
    val texto = stringResource(R.string.mapas_uso, FormatoEs.tamano(usados), gb)
    val fraccion = if (limite > 0) (usados.toFloat() / limite).coerceIn(0f, 1f) else 0f
    Column(
        modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_USO),
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        LinearProgressIndicator(
            progress = { fraccion },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = texto },
        )
        Text(text = texto, style = Ayuda, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * La descarga: progreso con «Cancelar» mientras baja; si no, el error y/o el único botón que toca (descargar todo,
 * reintentar o descargar el satélite). Sin nada que hacer no sale la tarjeta.
 */
@Composable
internal fun TarjetaDescarga(
    estado: MapasOfflineUiState,
    acciones: AccionesMapasOffline,
) {
    val descarga = estado.datos.descarga
    val error = estado.datos.error
    val accion =
        when {
            descarga != null -> Accion.CANCELAR
            estado.fase == FaseMapas.SIN_DATOS || error != null -> Accion.DESCARGAR
            estado.ofreceSatelite -> Accion.SATELITE
            else -> null
        }
    if (accion == null) return
    TarjetaWrc(Modifier.fillMaxWidth(), acentoRojo = error != null) {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            descarga?.let { Progreso(estado, it.esperandoRed) }
            error?.let { Error(it) }
            when (accion) {
                Accion.CANCELAR -> {
                    OutlinedButton(
                        onClick = acciones.onCancelar,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(
                                    min = Medidas.zonaTactil,
                                ).testTag(ETIQUETA_CANCELAR),
                    ) { Text(stringResource(R.string.mapas_cancelar)) }
                }

                Accion.DESCARGAR -> {
                    BotonDescargar(error != null, acciones.onDescargar)
                }

                Accion.SATELITE -> {
                    BotonDescargarSatelite(estado.bytesSatelite, acciones.onDescargar)
                }
            }
        }
    }
}

private enum class Accion { CANCELAR, DESCARGAR, SATELITE }

@Composable
private fun BotonDescargar(
    reintento: Boolean,
    onDescargar: () -> Unit,
) {
    Button(
        onClick = onDescargar,
        modifier = Modifier.fillMaxWidth().heightIn(min = Medidas.zonaTactil).testTag(ETIQUETA_DESCARGAR),
    ) { Text(stringResource(if (reintento) R.string.mapas_reintentar else R.string.mapas_descargar)) }
}

@Composable
private fun BotonDescargarSatelite(
    bytes: Long?,
    onDescargar: () -> Unit,
) {
    Button(
        onClick = onDescargar,
        modifier = Modifier.fillMaxWidth().heightIn(min = Medidas.zonaTactil).testTag(ETIQUETA_DESCARGAR_SATELITE),
    ) {
        Text(
            if (bytes == null) {
                stringResource(R.string.mapas_descargar_satelite)
            } else {
                stringResource(R.string.mapas_descargar_satelite_tamano, FormatoEs.tamano(bytes))
            },
        )
    }
}

@Composable
private fun Progreso(
    estado: MapasOfflineUiState,
    esperandoRed: Boolean,
) {
    val porcentaje = estado.porcentaje
    Column(Modifier.fillMaxWidth().testTag(ETIQUETA_PROGRESO), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        if (estado.datos.instalados != null) {
            Text(stringResource(R.string.mapas_actualizando), style = Ayuda)
        }
        if (esperandoRed) {
            Text(
                text =
                    stringResource(
                        if (estado.ajustes.soloWifi) R.string.mapas_esperando_wifi else R.string.mapas_esperando_red,
                    ),
                style = Cuerpo,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.testTag(ETIQUETA_ESPERA),
            )
        }
        val progreso = estado.progreso
        if (porcentaje == null || progreso == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            // Esperando red sin total conocido: ya lo dice «Esperando…»; «Descargando…» sería contradictorio.
            if (!esperandoRed) Text(stringResource(R.string.mapas_descargando), style = Ayuda)
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
                        FormatoEs.tamano(progreso.bytesHechos),
                        FormatoEs.tamano(progreso.bytesTotales),
                        porcentaje,
                    ),
                style = Ayuda,
            )
        }
    }
}

@Composable
private fun Error(motivo: MotivoError) {
    Text(
        text = stringResource(textoError(motivo)),
        style = Ayuda,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_ERROR),
    )
}

/** Solo con WiFi y límite de espacio, en una tarjeta de ajustes. */
@Composable
internal fun TarjetaAjustesDescarga(
    estado: MapasOfflineUiState,
    acciones: AccionesMapasOffline,
) {
    TarjetaWrc(Modifier.fillMaxWidth()) {
        FilaSoloWifi(estado, acciones)
        Column(Modifier.padding(top = Espacio.s), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            TituloTarjeta(stringResource(R.string.mapas_limite_titulo))
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
                                .heightIn(min = Medidas.zonaTactil)
                                .testTag(ETIQUETA_LIMITE_PREFIJO + gb)
                                .semantics { contentDescription = descripcion },
                    ) { Text(stringResource(R.string.mapas_limite_opcion, gb)) }
                }
            }
        }
    }
}

/** Botón destructivo: contorno y texto en el color de error, con la papelera. El borrado pide confirmación aparte. */
@Composable
internal fun BotonBorrar(
    @StringRes texto: Int,
    etiqueta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        shape = Radios.formaTarjeta,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = modifier.fillMaxWidth().heightIn(min = Medidas.zonaTactil).testTag(etiqueta),
    ) {
        Icon(Iconos.Borrar, contentDescription = null, modifier = Modifier.size(ICONO_BOTON))
        Text(stringResource(texto), modifier = Modifier.padding(start = Espacio.s))
    }
}
