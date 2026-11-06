package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.ui.copiloto.CopilotoUiState
import app.trombito.rallycopilot.ui.copiloto.CopilotoViewModel
import app.trombito.rallycopilot.ui.edicion.AccionesEdicionMapa
import app.trombito.rallycopilot.ui.edicion.EdicionEnMapa
import app.trombito.rallycopilot.ui.edicion.EdicionMapaUiState
import app.trombito.rallycopilot.ui.edicion.EdicionMapaViewModel
import app.trombito.rallycopilot.ui.navegacion.NavegacionViewModel
import app.trombito.rallycopilot.ui.theme.CarbonOpaco
import app.trombito.rallycopilot.ui.theme.Radios
import app.trombito.rallycopilot.ui.theme.TextoSobreCarbon
import app.trombito.rallycopilot.ui.tramos.CuentaAtrasPorVoz

/** Separación de los avisos (snackbar) del borde de abajo: por encima de la hoja de conducción. */
private val MARGEN_AVISOS = 120.dp

/** Lo que la superposición del mapa lee del estado: copiloto, cronómetro, editor de notas y estado de la conducción. */
internal class UiSuperposicion(
    val copiloto: CopilotoUiState,
    val crono: CronoEnCurso?,
    val edicion: EdicionMapaUiState,
    val control: EstadoNavegacion,
)

/**
 * Lo que va encima de todo en el mapa: las luces de la cuenta atrás de la orden «salida», el aviso (snackbar) de «Casa
 * guardada», el editor de notas (parado) y el micrófono grande del reconocimiento.
 */
@Composable
internal fun BoxScope.SuperposicionDelMapa(
    ui: UiSuperposicion,
    navegacionViewModel: NavegacionViewModel,
    edicionViewModel: EdicionMapaViewModel,
    copilotoViewModel: CopilotoViewModel,
) {
    val avisos = remember { SnackbarHostState() }
    val casaGuardada = stringResource(R.string.navegacion_ui_casa_guardada)
    LaunchedEffect(navegacionViewModel) {
        navegacionViewModel.casaGuardada.collect { avisos.showSnackbar(casaGuardada) }
    }
    CuentaAtrasPorVoz(ui.copiloto.dialogo, ui.crono)
    SnackbarHost(
        avisos,
        Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = MARGEN_AVISOS),
    ) {
        // Carbón con texto claro y filo ámbar, como el resto de tarjetas sobre el mapa.
        Snackbar(
            it,
            shape = Radios.formaTarjeta,
            containerColor = CarbonOpaco,
            contentColor = TextoSobreCarbon,
        )
    }
    EdicionEnMapa(
        estado = ui.edicion,
        acciones =
            AccionesEdicionMapa(
                onAplicar = edicionViewModel::onAplicar,
                onQuitar = edicionViewModel::onQuitar,
                onCerrar = edicionViewModel::onCerrar,
            ),
        reconocimiento = ui.control is EstadoNavegacion.Libre && ui.control.reconocimientoTramoId != null,
        escuchando = ui.copiloto.escuchando,
        onEscuchar = copilotoViewModel::onEscuchar,
    )
}
