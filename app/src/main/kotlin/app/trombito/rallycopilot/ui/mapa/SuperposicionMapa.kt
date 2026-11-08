package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import app.trombito.rallycopilot.ui.reconocimiento.AccionesResumenReconocimiento
import app.trombito.rallycopilot.ui.reconocimiento.ReconocimientoViewModel
import app.trombito.rallycopilot.ui.reconocimiento.ResumenReconocimiento
import app.trombito.rallycopilot.ui.theme.CarbonOpaco
import app.trombito.rallycopilot.ui.theme.Radios
import app.trombito.rallycopilot.ui.theme.TextoSobreCarbon
import app.trombito.rallycopilot.ui.tramos.CuentaAtrasPorVoz
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Los avisos (snackbar) van en el centro del mapa, entre la pila de tarjetas de arriba y la píldora de velocidad y la
 * hoja de abajo, y sin llegar a la columna de botones de la derecha: no tapan nada que se use conduciendo.
 */
private val MARGEN_AVISOS_INICIO = 16.dp
private val MARGEN_AVISOS_FIN = 84.dp

/** Lo que dura un aviso en pantalla. */
private const val DURACION_AVISO_MS = 3_000L

/** Lo que la superposición del mapa lee del estado: copiloto, cronómetro, editor de notas y estado de la conducción. */
internal class UiSuperposicion(
    val copiloto: CopilotoUiState,
    val crono: CronoEnCurso?,
    val edicion: EdicionMapaUiState,
    val control: EstadoNavegacion,
)

/**
 * Lo que va encima de todo en el mapa: las luces de la cuenta atrás de la orden «salida», el aviso (snackbar) de «Casa
 * guardada», el editor de notas (parado), el micrófono grande del reconocimiento y el resumen de cada pasada del
 * reconocimiento (al llegar parado a la meta o al terminarla).
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
        navegacionViewModel.casaGuardada.collect {
            launch {
                val cierre =
                    launch {
                        delay(DURACION_AVISO_MS)
                        avisos.currentSnackbarData?.dismiss()
                    }
                avisos.showSnackbar(casaGuardada, duration = SnackbarDuration.Indefinite)
                cierre.cancel()
            }
        }
    }
    CuentaAtrasPorVoz(ui.copiloto.dialogo, ui.crono)
    SnackbarHost(
        avisos,
        Modifier
            .align(Alignment.Center)
            .padding(start = MARGEN_AVISOS_INICIO, end = MARGEN_AVISOS_FIN),
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
    // La pasada vive en el control de navegación (singleton): este ViewModel ve la misma que el del tramo.
    val reconocimiento: ReconocimientoViewModel = hiltViewModel()
    val resumen by reconocimiento.resumen.collectAsStateWithLifecycle()
    ResumenReconocimiento(
        resumen,
        AccionesResumenReconocimiento(reconocimiento::onHacerSegundaPasada, reconocimiento::onTerminar),
    )
}
