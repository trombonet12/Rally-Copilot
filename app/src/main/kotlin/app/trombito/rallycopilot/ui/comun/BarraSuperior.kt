package app.trombito.rallycopilot.ui.comun

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.theme.Espacio

/** Etiqueta del botón de volver, para las pruebas. */
const val ETIQUETA_VOLVER = "barra-volver"

/** Barra superior de las pantallas secundarias: botón de volver (48 dp) y título. */
@Composable
fun BarraSuperior(
    titulo: String,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Espacio.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotonVolver(onVolver)
        Spacer(Modifier.width(Espacio.xs))
        Text(
            text = titulo.uppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
fun BotonVolver(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onVolver, modifier = modifier.size(48.dp).testTag(ETIQUETA_VOLVER)) {
        Icon(
            imageVector = Iconos.Atras,
            contentDescription = stringResource(R.string.comun_volver),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
