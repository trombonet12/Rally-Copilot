package app.trombito.rallycopilot.ui.mapasoffline

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.CargandoWrc

const val ETIQUETA_PREPARANDO = "mapas-preparando"

/** Mientras se siembran los datos del APK: indicador y «Preparando mapas…», sin botones. */
@Composable
internal fun Preparando() {
    CargandoWrc(stringResource(R.string.mapas_preparando), Modifier.fillMaxWidth().testTag(ETIQUETA_PREPARANDO))
}
