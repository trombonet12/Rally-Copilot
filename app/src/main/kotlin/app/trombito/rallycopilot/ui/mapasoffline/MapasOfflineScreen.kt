package app.trombito.rallycopilot.ui.mapasoffline

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.datos.MotivoError
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.comun.CargandoWrc
import app.trombito.rallycopilot.ui.comun.FondoWrc
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas

const val ETIQUETA_FASE = "mapas-fase"
const val ETIQUETA_INSTALADOS = "mapas-instalados"
const val ETIQUETA_USO = "mapas-uso"
const val ETIQUETA_PROGRESO = "mapas-progreso"
const val ETIQUETA_ESPERA = "mapas-espera"
const val ETIQUETA_ERROR = "mapas-error"
const val ETIQUETA_DESCARGAR = "mapas-descargar"
const val ETIQUETA_DESCARGAR_SATELITE = "mapas-descargar-satelite"
const val ETIQUETA_CANCELAR = "mapas-cancelar"
const val ETIQUETA_BORRAR = "mapas-borrar"
const val ETIQUETA_BORRAR_CONFIRMAR = "mapas-borrar-confirmar"
const val ETIQUETA_BORRAR_CANCELAR = "mapas-borrar-cancelar"
const val ETIQUETA_LIMITE_PREFIJO = "mapas-limite-"

/** Callbacks de Mapas offline. */
data class AccionesMapasOffline(
    val onVolver: () -> Unit = {},
    val onDescargar: () -> Unit = {},
    val onCancelar: () -> Unit = {},
    val onBorrar: () -> Unit = {},
    val onSoloWifi: (Boolean) -> Unit = {},
    val onLimiteGb: (Int) -> Unit = {},
    val onDescargarZona: () -> Unit = {},
    val onBorrarDetalle: () -> Unit = {},
    val onCancelarDetalle: () -> Unit = {},
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

/** Qué borrado espera confirmación: el satélite entero o solo el detalle z18-19. */
private enum class Borrado { SATELITE, DETALLE }

/**
 * Mapas offline en tarjetas, una acción principal por tarjeta: lo instalado con la barra de uso («1,6 GB de 8 GB»),
 * la descarga (progreso, error o el botón de descargar), el detalle z18-19 y los ajustes de descarga. Los borrados
 * llevan icono de papelera y piden confirmación.
 */
@Composable
fun MapasOfflineScreen(
    estado: MapasOfflineUiState,
    acciones: AccionesMapasOffline,
    modifier: Modifier = Modifier,
) {
    var confirmar by rememberSaveable { mutableStateOf<Borrado?>(null) }
    FondoWrc(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .testTag(ETIQUETA_FASE + "-" + estado.fase.name.lowercase()),
        ) {
            BarraSuperior(stringResource(R.string.mapas_titulo), acciones.onVolver)
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Espacio.l),
                verticalArrangement = Arrangement.spacedBy(Espacio.m),
            ) {
                when (estado.fase) {
                    FaseMapas.CARGANDO -> {
                        CargandoWrc(stringResource(R.string.mapas_cargando))
                    }

                    FaseMapas.PREPARANDO -> {
                        Preparando()
                    }

                    else -> {
                        ContenidoMapas(estado, acciones, onBorrarSatelite = { confirmar = Borrado.SATELITE }) {
                            confirmar = Borrado.DETALLE
                        }
                    }
                }
                Spacer(Modifier.height(Espacio.l))
            }
        }
    }
    confirmar?.let { borrado ->
        DialogoBorrar(
            borrado = borrado,
            onConfirmar = {
                confirmar = null
                if (borrado == Borrado.SATELITE) acciones.onBorrar() else acciones.onBorrarDetalle()
            },
            onCancelar = { confirmar = null },
        )
    }
}

@Composable
private fun ContenidoMapas(
    estado: MapasOfflineUiState,
    acciones: AccionesMapasOffline,
    onBorrarSatelite: () -> Unit,
    onBorrarDetalle: () -> Unit,
) {
    TarjetaUso(estado)
    TarjetaDescarga(estado, acciones)
    if (estado.datos.instalados != null && estado.fase != FaseMapas.DESCARGANDO) {
        SeccionDetalle(estado.detalle, acciones, onBorrarDetalle)
    }
    TarjetaAjustesDescarga(estado, acciones)
    if (estado.fase == FaseMapas.INSTALADO && estado.datos.instalados?.conSatelite == true) {
        BotonBorrar(R.string.mapas_borrar, ETIQUETA_BORRAR, onBorrarSatelite)
    }
}

@Composable
private fun DialogoBorrar(
    borrado: Borrado,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    val satelite = borrado == Borrado.SATELITE
    AlertDialog(
        onDismissRequest = onCancelar,
        title = {
            Text(stringResource(if (satelite) R.string.mapas_borrar_titulo else R.string.mapas_detalle_borrar_titulo))
        },
        text = {
            Text(stringResource(if (satelite) R.string.mapas_borrar_texto else R.string.mapas_detalle_borrar_texto))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmar,
                modifier =
                    Modifier
                        .heightIn(min = Medidas.zonaTactil)
                        .testTag(if (satelite) ETIQUETA_BORRAR_CONFIRMAR else ETIQUETA_DETALLE_BORRAR_CONFIRMAR),
            ) { Text(stringResource(R.string.mapas_borrar_confirmar), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(
                onClick = onCancelar,
                modifier = Modifier.heightIn(min = Medidas.zonaTactil).testTag(ETIQUETA_BORRAR_CANCELAR),
            ) {
                Text(stringResource(R.string.mapas_borrar_cancelar))
            }
        },
    )
}
