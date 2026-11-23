package app.trombito.rallycopilot.ui.mapa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.tramos.ProveedorTramosPorDelante
import app.trombito.rallycopilot.motor.tramos.TramoEnCamino
import app.trombito.rallycopilot.ui.comun.PARADA_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.math.round

/** Paso (m) en que se publica la distancia a la meta: el HUD la redondea a 50 y no hace falta recomponer por menos. */
internal const val PASO_FIN_TRAMO_M = 10.0

/**
 * Los tramos conocidos por delante en el camino (guiado o libre) para pintarlos en el mapa y la distancia a la meta
 * más próxima para el HUD. [tramos] solo emite al recalcularse (cada ~200 m), no con cada fijación.
 */
@HiltViewModel
class TramosMapaViewModel
    @Inject
    constructor(
        proveedor: ProveedorTramosPorDelante,
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
    }
