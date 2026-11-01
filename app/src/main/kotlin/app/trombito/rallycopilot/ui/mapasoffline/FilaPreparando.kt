package app.trombito.rallycopilot.ui.mapasoffline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_PREPARANDO = "mapas-preparando"

/** Mientras se siembran los datos del APK: indicador y «Preparando mapas…», sin botones. */
@Composable
internal fun Preparando() {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_PREPARANDO),
        horizontalArrangement = Arrangement.spacedBy(Espacio.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.mapas_preparando), style = MaterialTheme.typography.titleMedium)
    }
}
