package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_AJUSTES_MAPAS = "ajustes-mapas-offline"
const val ETIQUETA_AJUSTES_ACERCA_DE = "ajustes-acerca-de"

/** Ajustes. Por ahora solo Mapas offline y Acerca de; el resto llega en H10. */
@Composable
fun AjustesScreen(
    onMapasOffline: () -> Unit,
    onAcercaDe: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        BarraSuperior(stringResource(R.string.ajustes_titulo), onVolver)
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Espacio.l),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            FilaAjuste(stringResource(R.string.ajustes_mapas_offline), ETIQUETA_AJUSTES_MAPAS, onMapasOffline)
            FilaAjuste(stringResource(R.string.ajustes_acerca_de), ETIQUETA_AJUSTES_ACERCA_DE, onAcercaDe)
        }
    }
}

@Composable
private fun FilaAjuste(
    titulo: String,
    etiqueta: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag(etiqueta),
    ) {
        Column(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = Espacio.l, vertical = Espacio.m),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
