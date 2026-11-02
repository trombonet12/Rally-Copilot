package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.mapa.CamaraVistaMapa
import app.trombito.rallycopilot.ui.mapa.MapaViewModel
import app.trombito.rallycopilot.ui.mapa.PreferenciasVistaMapa
import app.trombito.rallycopilot.ui.mapa.RutaEnMapa
import app.trombito.rallycopilot.ui.mapa.VistaMapa
import app.trombito.rallycopilot.ui.navegacion.MarcadorParada
import app.trombito.rallycopilot.ui.theme.Espacio

/**
 * Conecta el modo dibujo con su ViewModel y el mapa real (el estilo sale de [MapaViewModel], que sin permiso de
 * ubicación no lee el GPS). Al guardar navega al detalle del tramo creado con [onGuardado].
 */
@Composable
fun DibujarRoute(
    onVolver: () -> Unit,
    onGuardado: (tramoId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DibujarViewModel = hiltViewModel(),
    mapaViewModel: MapaViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val estadoMapa by mapaViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(estado.guardado) { estado.guardado?.let(onGuardado) }
    val camara = remember(viewModel) { CamaraVistaMapa(onToque = viewModel::onToque) }
    DibujarScreen(
        estado = estado,
        acciones =
            AccionesDibujar(
                onVolver = onVolver,
                onDeshacer = viewModel::onDeshacer,
                onBorrarTodo = viewModel::onBorrarTodo,
                onGuardar = viewModel::onGuardar,
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
                    preferencias = PreferenciasVistaMapa(siguiendo = false, ruta = rutaDibujada(estado)),
                    modifier = modificador.fillMaxSize(),
                )
            }
        },
        modifier = modifier,
    )
}

/** Los puntos tocados (salida, paradas numeradas y meta) sobre la ruta calculada o, mientras, rectas entre ellos. */
internal fun rutaDibujada(estado: DibujarUiState): RutaEnMapa? {
    val puntos = estado.puntos
    return if (puntos.isEmpty()) {
        null
    } else {
        RutaEnMapa(
            puntos =
                estado.ruta
                    .ifEmpty { puntos }
                    .takeIf { it.size >= 2 }
                    .orEmpty(),
            destino = puntos.takeIf { it.size >= 2 }?.last(),
            paradas = puntos.drop(1).dropLast(1).mapIndexed { i, p -> MarcadorParada(p, i + 1) },
            elegido = puntos.first(),
        )
    }
}
