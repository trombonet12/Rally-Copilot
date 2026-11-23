package app.trombito.rallycopilot.ui.mapa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.notas.ProveedorNotas
import app.trombito.rallycopilot.ui.comun.PARADA_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Las curvas y avisos de la hoja vigente cercanos a la posición, para pintarlos en el mapa (libre y guiado). Solo
 * emite cuando cambia el conjunto visible (se pasa una nota, entra otra en la ventana), no con cada fijación.
 */
@HiltViewModel
class NotasMapaViewModel
    @Inject
    constructor(
        proveedor: ProveedorNotas,
    ) : ViewModel() {
        val notasMapa: StateFlow<NotasEnMapa> =
            proveedor.notas
                .map { notasEnMapa(it) }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), NotasEnMapa())
    }
