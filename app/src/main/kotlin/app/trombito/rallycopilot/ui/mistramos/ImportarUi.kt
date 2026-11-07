package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.importar.MotivoFalloImportar
import app.trombito.rallycopilot.ui.comun.Iconos

const val ETIQUETA_IMPORTAR_DIALOGO = "importar-confirmar-dialogo"
const val ETIQUETA_IMPORTAR_ACEPTAR = "importar-confirmar-aceptar"
const val ETIQUETA_IMPORTAR_CANCELAR = "importar-confirmar-cancelar"

/**
 * Pregunta «¿Importar «nombre» (N km)?» antes de guardar un GPX/KML que ha llegado de otra app: viene de fuera y no es
 * algo que el usuario haya elegido en el selector de la propia app. Sin nombre dice «este tramo».
 */
@Composable
fun DialogoConfirmarImportacion(
    pendiente: EstadoImportacion.Pendiente,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    val km = formatearDistanciaTramo(pendiente.longitudM)
    val titulo =
        pendiente.nombre?.let { stringResource(R.string.mistramos_confirmar_titulo, it, km) }
            ?: stringResource(R.string.mistramos_confirmar_titulo_sin_nombre, km)
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo, modifier = Modifier.testTag(ETIQUETA_IMPORTAR_DIALOGO)) },
        text = { Text(stringResource(R.string.mistramos_confirmar_texto)) },
        confirmButton = {
            TextButton(onClick = onConfirmar, modifier = Modifier.testTag(ETIQUETA_IMPORTAR_ACEPTAR)) {
                Text(stringResource(R.string.mistramos_confirmar_aceptar))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, modifier = Modifier.testTag(ETIQUETA_IMPORTAR_CANCELAR)) {
                Text(stringResource(R.string.mistramos_confirmar_cancelar))
            }
        },
    )
}

/** Importar un GPX/KML y dibujar una ruta, como iconos en la barra (solo si la pantalla los ofrece). */
@Composable
internal fun RowScope.BotonesCrear(acciones: AccionesMisTramos) {
    acciones.onImportar?.let { alImportar ->
        IconButton(onClick = alImportar, modifier = Modifier.size(48.dp).testTag(ETIQUETA_TRAMOS_IMPORTAR)) {
            Icon(
                Iconos.Importar,
                stringResource(R.string.mistramos_importar),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    acciones.onDibujar?.let { alDibujar ->
        IconButton(onClick = alDibujar, modifier = Modifier.size(48.dp).testTag(ETIQUETA_TRAMOS_DIBUJAR)) {
            Icon(Iconos.Dibujar, stringResource(R.string.mistramos_dibujar), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Avisa de cómo acabó la importación (importado o el motivo del fallo) y la da por vista. */
@Composable
internal fun AvisoImportacion(
    importacion: EstadoImportacion,
    avisos: SnackbarHostState,
    onVista: () -> Unit,
) {
    val mensaje =
        when (importacion) {
            is EstadoImportacion.Hecho -> {
                importacion.nombre?.let { stringResource(R.string.mistramos_importado, it) }
                    ?: stringResource(R.string.mistramos_importado_sin_nombre)
            }

            is EstadoImportacion.Fallo -> {
                stringResource(textoFalloImportar(importacion.motivo))
            }

            else -> {
                null
            }
        }
    LaunchedEffect(importacion) {
        if (mensaje != null) {
            avisos.showSnackbar(mensaje, duration = SnackbarDuration.Long)
            onVista()
        }
    }
}

private fun textoFalloImportar(motivo: MotivoFalloImportar): Int =
    when (motivo) {
        MotivoFalloImportar.NO_SE_PUEDE_ABRIR -> R.string.mistramos_importar_no_se_puede_abrir
        MotivoFalloImportar.DEMASIADO_GRANDE -> R.string.mistramos_importar_demasiado_grande
        MotivoFalloImportar.FORMATO_DESCONOCIDO -> R.string.mistramos_importar_formato
        MotivoFalloImportar.ARCHIVO_DANADO -> R.string.mistramos_importar_danado
        MotivoFalloImportar.SIN_TRAZA -> R.string.mistramos_importar_sin_traza
        MotivoFalloImportar.CORTO -> R.string.mistramos_importar_corto
        MotivoFalloImportar.NO_GUARDADO -> R.string.mistramos_importar_no_guardado
    }
