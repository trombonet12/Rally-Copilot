package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion

const val ETIQUETA_AJUSTES_CRONOMETRAR = "ajustes-cronometrar"
const val ETIQUETA_AJUSTES_SALIDA_MANUAL = "ajustes-salida-manual"

/**
 * Tramos: «Cronometrar trayectos» y «Salida manual». Cronometrar activado: cada trayecto se guarda como tramo con sus
 * pasadas; desactivado, las notas y el guiado siguen. La vista previa de arriba enseña el reloj según lo elegido.
 */
@Composable
internal fun ControlesTramos(
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
) {
    FilaInterruptor(
        titulo = stringResource(R.string.ajustes_cronometrar),
        marcado = ajustes.cronometrar,
        onCambio = acciones.onCronometrar,
        etiqueta = ETIQUETA_AJUSTES_CRONOMETRAR,
        detalle = stringResource(R.string.ajustes_cronometrar_corto),
        info = stringResource(R.string.ajustes_cronometrar_detalle),
    )
    FilaInterruptor(
        titulo = stringResource(R.string.ajustes_salida_manual),
        marcado = ajustes.salidaManual,
        onCambio = acciones.onSalidaManual,
        etiqueta = ETIQUETA_AJUSTES_SALIDA_MANUAL,
        detalle = stringResource(R.string.ajustes_salida_manual_corto),
        info = stringResource(R.string.ajustes_salida_manual_detalle),
    )
}
