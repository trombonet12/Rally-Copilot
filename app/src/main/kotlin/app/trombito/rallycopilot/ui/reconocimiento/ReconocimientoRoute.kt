package app.trombito.rallycopilot.ui.reconocimiento

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.mistramos.MisTramosRoute
import app.trombito.rallycopilot.ui.theme.Espacio

/**
 * «Reconocimiento»: la lista de Mis tramos para elegir cuál se va a reconocer; al elegirlo empieza la pasada y se abre
 * el mapa, donde el copiloto lee las notas del tramo (fusionadas con las editadas) y lo que se dicta se anota.
 */
@Composable
fun ReconocimientoRoute(
    onVolver: () -> Unit,
    onEmpezado: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReconocimientoViewModel = hiltViewModel(),
) {
    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        Text(
            text = stringResource(R.string.reconocimiento_ayuda),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Espacio.l, vertical = Espacio.s),
        )
        MisTramosRoute(
            onVolver = onVolver,
            onAbrirTramo = {
                viewModel.empezar(it)
                onEmpezado()
            },
            titulo = R.string.inicio_reconocimiento,
        )
    }
}
