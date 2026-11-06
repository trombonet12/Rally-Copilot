package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.copia.MotivoCopiaInvalida
import app.trombito.rallycopilot.ui.comun.Iconos
import java.time.LocalDate

const val ETIQUETA_AJUSTES_EXPORTAR = "ajustes-copia-exportar"
const val ETIQUETA_AJUSTES_IMPORTAR = "ajustes-copia-importar"
const val ETIQUETA_COPIA_PROGRESO = "ajustes-copia-progreso"
const val ETIQUETA_COPIA_CONFIRMAR = "ajustes-copia-confirmar"
const val ETIQUETA_COPIA_CANCELAR = "ajustes-copia-cancelar"

/** Nombre propuesto al guardar la copia: `rally-copilot-AAAA-MM-DD.zip`. */
fun nombreArchivoCopia(fecha: LocalDate = LocalDate.now()): String = "rally-copilot-$fecha.zip"

/** Sección «Datos»: mapas offline, exportar e importar la copia de seguridad (con progreso mientras trabaja). */
@Composable
internal fun SeccionDatos(
    acciones: AccionesAjustes,
    copiaEnCurso: Boolean,
) {
    TarjetaSeccion(
        clave = "datos",
        icono = Iconos.Descarga,
        titulo = R.string.ajustes_seccion_datos,
        detalle = R.string.ajustes_seccion_datos_detalle,
    ) {
        FilaAjuste(
            titulo = stringResource(R.string.ajustes_mapas_offline),
            detalle = stringResource(R.string.ajustes_mapas_offline_detalle),
            etiqueta = ETIQUETA_AJUSTES_MAPAS,
            onClick = acciones.onMapasOffline,
        )
        FilaAjuste(
            titulo = stringResource(R.string.copia_exportar),
            detalle = stringResource(R.string.copia_exportar_detalle),
            etiqueta = ETIQUETA_AJUSTES_EXPORTAR,
            onClick = acciones.onExportarCopia,
            icono = Iconos.Descarga,
            habilitada = !copiaEnCurso,
        )
        FilaAjuste(
            titulo = stringResource(R.string.copia_importar),
            detalle = stringResource(R.string.copia_importar_detalle),
            etiqueta = ETIQUETA_AJUSTES_IMPORTAR,
            onClick = acciones.onImportarCopia,
            icono = Iconos.Importar,
            habilitada = !copiaEnCurso,
        )
        if (copiaEnCurso) {
            LinearProgressIndicator(Modifier.fillMaxWidth().testTag(ETIQUETA_COPIA_PROGRESO))
            Text(stringResource(R.string.copia_progreso))
        }
    }
}

/** Confirmación antes de elegir la copia: importar sustituye lo que hay. */
@Composable
internal fun DialogoImportarCopia(
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.copia_confirmar_titulo)) },
        text = { Text(stringResource(R.string.copia_confirmar_texto)) },
        confirmButton = {
            TextButton(onClick = onConfirmar, modifier = Modifier.testTag(ETIQUETA_COPIA_CONFIRMAR)) {
                Text(stringResource(R.string.copia_confirmar_aceptar))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, modifier = Modifier.testTag(ETIQUETA_COPIA_CANCELAR)) {
                Text(stringResource(R.string.copia_cancelar))
            }
        },
    )
}

/** Avisa (snackbar) de cómo acabó la copia y da el aviso por visto. */
@Composable
internal fun AvisoCopia(
    estado: EstadoCopia,
    avisos: SnackbarHostState,
    onVisto: () -> Unit,
) {
    val mensaje =
        when (estado) {
            EstadoCopia.Exportada -> stringResource(R.string.copia_exportada)
            EstadoCopia.Importada -> stringResource(R.string.copia_importada)
            is EstadoCopia.Fallo -> stringResource(textoFallo(estado.motivo))
            else -> null
        }
    LaunchedEffect(estado) {
        if (mensaje != null) {
            avisos.showSnackbar(mensaje, duration = SnackbarDuration.Long)
            onVisto()
        }
    }
}

private fun textoFallo(motivo: MotivoCopiaInvalida): Int =
    when (motivo) {
        MotivoCopiaInvalida.NO_SE_PUEDE_ABRIR -> R.string.copia_fallo_abrir
        MotivoCopiaInvalida.ARCHIVO_INVALIDO -> R.string.copia_fallo_invalido
        MotivoCopiaInvalida.DEMASIADO_GRANDE -> R.string.copia_fallo_grande
        MotivoCopiaInvalida.ESQUEMA_FUTURO -> R.string.copia_fallo_futuro
        MotivoCopiaInvalida.BASE_DANADA -> R.string.copia_fallo_danada
        MotivoCopiaInvalida.INCOMPATIBLE -> R.string.copia_fallo_incompatible
    }
