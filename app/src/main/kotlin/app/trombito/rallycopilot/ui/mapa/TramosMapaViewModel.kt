package app.trombito.rallycopilot.ui.mapa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.tramos.ProveedorTramosPorDelante
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.motor.tramos.TramoEnCamino
import app.trombito.rallycopilot.ui.alertas.BanderaMapa
import app.trombito.rallycopilot.ui.comun.PARADA_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.math.round

/** Paso (m) en que se publica la distancia a la meta: el HUD la redondea a 50 y no hace falta recomponer por menos. */
internal const val PASO_FIN_TRAMO_M = 10.0

/**
 * Los tramos conocidos por delante en el camino (guiado o libre) para pintarlos en el mapa y la distancia a la meta
 * más próxima para el HUD. [tramos] solo emite al recalcularse (cada ~200 m), no con cada fijación. [banderas] son las
 * de esos tramos con su nombre y su mejor tiempo, para el bocadillo al tocarlas.
 */
@HiltViewModel
class TramosMapaViewModel
    @Inject
    constructor(
        proveedor: ProveedorTramosPorDelante,
        repositorio: RepositorioTramos,
    ) : ViewModel() {
        // Un solo cálculo para las dos salidas: el flujo del proveedor es frío y buscar cuesta.
        private val estado =
            proveedor.porDelante.shareIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), 1)

        val tramos: StateFlow<List<TramoEnCamino>> =
            estado
                .map { it.tramos }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), emptyList())

        /** Metros hasta la meta más próxima por delante (a pasos de [PASO_FIN_TRAMO_M]); null si no hay. */
        val finDeTramoM: StateFlow<Double?> =
            estado
                .map { it.finDeTramoM?.let { m -> round(m / PASO_FIN_TRAMO_M) * PASO_FIN_TRAMO_M } }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), null)

        /** Las banderas de salida y meta de los [tramos], con el nombre y el mejor tiempo de su tramo (si se saben). */
        @OptIn(ExperimentalCoroutinesApi::class)
        val banderas: StateFlow<List<BanderaMapa>> =
            tramos
                .flatMapLatest { lista ->
                    if (lista.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        repositorio.observarResumenes().map { resumenes ->
                            val porId = resumenes.associateBy { it.id }
                            banderasDe(lista).map { b ->
                                val resumen = porId[b.seleccion.tramoId]
                                BanderaMapa(
                                    tramoId = b.seleccion.tramoId,
                                    esMeta = b.seleccion.esMeta,
                                    lat = b.punto.lat,
                                    lon = b.punto.lon,
                                    nombre = resumen?.nombre,
                                    mejorMs = resumen?.mejorTotalMs,
                                )
                            }
                        }
                    }
                }.distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), emptyList())
    }
