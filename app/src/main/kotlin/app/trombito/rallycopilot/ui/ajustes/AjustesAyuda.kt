package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.Iconos

const val ETIQUETA_AYUDA_TUTORIAL = "ajustes-ayuda-tutorial"
const val ETIQUETA_AYUDA_NOTAS = "ajustes-ayuda-notas"
const val ETIQUETA_AYUDA_VOZ = "ajustes-ayuda-voz"

/** Adónde lleva cada fila de Ajustes > Ayuda; los resuelve la navegación. */
data class AccionesAyuda(
    val onVerTutorial: () -> Unit = {},
    val onNotas: () -> Unit = {},
    val onVoz: () -> Unit = {},
)

/** Ajustes > Ayuda: repasar el tutorial, qué significan las notas y qué se le puede decir al copiloto. */
@Composable
internal fun ControlesAyuda(acciones: AccionesAjustes) {
    FilaAccion(
        titulo = stringResource(R.string.ayuda_ver_tutorial),
        detalle = stringResource(R.string.ayuda_ver_tutorial_detalle),
        etiqueta = ETIQUETA_AYUDA_TUTORIAL,
        onClick = acciones.onVerTutorial,
        icono = Iconos.Empezar,
        navega = true,
    )
    FilaAccion(
        titulo = stringResource(R.string.ayuda_notas_titulo),
        detalle = stringResource(R.string.ayuda_notas_detalle),
        etiqueta = ETIQUETA_AYUDA_NOTAS,
        onClick = acciones.onAyudaNotas,
        icono = Iconos.Notas,
        navega = true,
    )
    FilaAccion(
        titulo = stringResource(R.string.ayuda_voz_titulo),
        detalle = stringResource(R.string.ayuda_voz_detalle),
        etiqueta = ETIQUETA_AYUDA_VOZ,
        onClick = acciones.onAyudaVoz,
        icono = Iconos.Microfono,
        navega = true,
    )
}
