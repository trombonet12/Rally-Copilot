package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.importar.MotivoFalloImportar
import app.trombito.rallycopilot.ui.comun.Iconos

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
