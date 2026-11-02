package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import app.trombito.rallycopilot.ui.theme.Espacio

/** Máximo de botones de 48 dp en una fila del panel lateral (cabe en el ancho amplio de 280 dp). */
internal const val BOTONES_POR_FILA = 5

/** Los botones en filas de [BOTONES_POR_FILA]: con más, los sobrantes bajan a otra fila en vez de salirse. */
@Composable
internal fun FilasDeBotones(botones: List<@Composable () -> Unit>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        botones.chunked(BOTONES_POR_FILA).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) { fila.forEach { it() } }
        }
    }
}
