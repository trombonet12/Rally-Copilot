package app.trombito.rallycopilot.ui.mapasoffline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.mapa.CamaraVistaMapa
import app.trombito.rallycopilot.ui.mapa.MapaViewModel
import app.trombito.rallycopilot.ui.mapa.PreferenciasVistaMapa
import app.trombito.rallycopilot.ui.mapa.RutaEnMapa
import app.trombito.rallycopilot.ui.mapa.VistaMapa
import app.trombito.rallycopilot.ui.theme.Espacio

/**
 * Conecta la elección de zona con su ViewModel y el mapa real (el estilo sale de [MapaViewModel], igual que el modo
 * dibujo). Al confirmar encola la descarga y vuelve a Mapas offline.
 */
@Composable
fun ZonaDetalleRoute(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ZonaDetalleViewModel = hiltViewModel(),
    mapaViewModel: MapaViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val estadoMapa by mapaViewModel.uiState.collectAsStateWithLifecycle()
    val densidad = LocalDensity.current
    val camara =
        remember(viewModel) {
            CamaraVistaMapa(
                inicial = ZonaDetalleViewModel.CAMARA_INICIAL,
                onCambio = viewModel::onCamara,
                onToque = viewModel::onToque,
            )
        }
    ZonaDetalleScreen(
        estado = estado,
        acciones =
            AccionesZonaDetalle(
                onVolver = onVolver,
                onZoom = viewModel::onZoom,
                onLimpiar = viewModel::onLimpiar,
                onConfirmar = { if (viewModel.onConfirmar()) onVolver() },
            ),
        mapa = { modificador ->
            val json = estadoMapa.estiloJson
            if (json == null) {
                Box(modificador, contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.dibujar_sin_estilo), modifier = Modifier.padding(Espacio.xl))
                }
            } else {
                VistaMapa(
                    estiloJson = json,
                    posicion = null,
                    camara = camara,
                    preferencias =
                        PreferenciasVistaMapa(
                            siguiendo = false,
                            ruta = estado.contorno.takeIf { it.size >= 2 }?.let { RutaEnMapa(it) },
                        ),
                    modifier =
                        modificador.fillMaxSize().onSizeChanged {
                            viewModel.onTamano(
                                it.width / densidad.density.toDouble(),
                                it.height / densidad.density.toDouble(),
                            )
                        },
                )
            }
        },
        modifier = modifier,
    )
}
