package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion

const val ETIQUETA_AJUSTES_CRUCES = "ajustes-avisar-cruces"
const val ETIQUETA_AJUSTES_SALIDAS = "ajustes-avisar-salidas"
const val ETIQUETA_AJUSTES_ROTONDAS = "ajustes-avisar-rotondas"

/** Indicaciones: qué dice y enseña el copiloto (cruces, salidas y entradas de autopista, rotondas). */
@Composable
internal fun ControlesIndicaciones(
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
) {
    FilaInterruptor(
        titulo = stringResource(R.string.indicaciones_cruces),
        marcado = ajustes.avisarCruces,
        onCambio = acciones.onAvisarCruces,
        etiqueta = ETIQUETA_AJUSTES_CRUCES,
    )
    FilaInterruptor(
        titulo = stringResource(R.string.indicaciones_salidas_autopista),
        marcado = ajustes.avisarSalidasAutopista,
        onCambio = acciones.onAvisarSalidasAutopista,
        etiqueta = ETIQUETA_AJUSTES_SALIDAS,
    )
    FilaInterruptor(
        titulo = stringResource(R.string.indicaciones_rotondas),
        marcado = ajustes.avisarRotondas,
        onCambio = acciones.onAvisarRotondas,
        etiqueta = ETIQUETA_AJUSTES_ROTONDAS,
    )
}
