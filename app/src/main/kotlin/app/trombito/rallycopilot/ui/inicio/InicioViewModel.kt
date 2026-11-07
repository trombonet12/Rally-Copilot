package app.trombito.rallycopilot.ui.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.ui.comun.PARADA_MS
import app.trombito.rallycopilot.ui.mistramos.aligerar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Inicio: solo lee la última salida (el tramo usado más recientemente que tenga pasadas) con su pasada más reciente,
 * su mejor tiempo y su silueta. Sin tramos cronometrados no hay nada que mostrar y la pantalla da la bienvenida.
 */
@HiltViewModel
class InicioViewModel
    @Inject
    constructor(
        private val repositorio: RepositorioTramos,
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<InicioUiState> =
            repositorio
                .observarResumenes(FiltroTramos.TODOS)
                .map { tramos -> tramos.firstOrNull { it.pasadas > 0 && it.mejorTotalMs != null } }
                .mapLatest { InicioUiState(ultimaSalida = it?.let { tramo -> ultimaDe(tramo) }) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), InicioUiState())

        private suspend fun ultimaDe(tramo: ResumenTramo): UltimaSalida? {
            val mejor = tramo.mejorTotalMs
            // Las pasadas llegan con la más reciente primero.
            val ultima = repositorio.pasadasDetalladas(tramo.id).firstOrNull()
            if (mejor == null || ultima == null) return null
            val traza = repositorio.tramo(tramo.id)?.traza.orEmpty()
            return UltimaSalida(tramo.id, tramo.nombre, ultima.totalMs, mejor, aligerar(traza, PUNTOS_MINIATURA))
        }

        private companion object {
            const val PUNTOS_MINIATURA = 80
        }
    }
