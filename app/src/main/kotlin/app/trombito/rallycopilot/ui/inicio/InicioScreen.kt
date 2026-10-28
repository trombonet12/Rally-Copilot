package app.trombito.rallycopilot.ui.inicio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally

/** Pantalla de inicio tipo menú de juego. Las entradas de hitos pendientes salen desactivadas. */
@Composable
fun InicioScreen(
    modifier: Modifier = Modifier,
    onEntrada: (EntradaInicio) -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Espacio.xl, vertical = Espacio.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.mascota_rally),
            contentDescription = stringResource(R.string.inicio_mascota),
            modifier = Modifier.size(160.dp),
        )
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Box(
            modifier =
                Modifier
                    .padding(vertical = Espacio.s)
                    .width(96.dp)
                    .height(4.dp)
                    .background(LocalColoresRally.current.franja),
        )
        Text(
            text = stringResource(R.string.inicio_lema),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.padding(top = Espacio.xxl).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            EntradaInicio.entries.forEach { FilaMenu(it) { onEntrada(it) } }
        }
    }
}

@Composable
private fun FilaMenu(
    entrada: EntradaInicio,
    onClick: () -> Unit,
) {
    val modificador = Modifier.fillMaxWidth().testTag("inicio-${entrada.name.lowercase()}")
    if (entrada.activa) {
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small,
            modifier = modificador,
        ) { ContenidoFila(entrada, proximamente = false) }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small,
            modifier = modificador.semantics { disabled() },
        ) { ContenidoFila(entrada, proximamente = true) }
    }
}

@Composable
private fun ContenidoFila(
    entrada: EntradaInicio,
    proximamente: Boolean,
) {
    Row(
        modifier = Modifier.padding(horizontal = Espacio.l, vertical = Espacio.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(entrada.titulo).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (proximamente) {
            Text(
                text = stringResource(R.string.inicio_proximamente),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
