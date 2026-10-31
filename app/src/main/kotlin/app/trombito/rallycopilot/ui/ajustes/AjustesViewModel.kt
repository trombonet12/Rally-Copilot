package app.trombito.rallycopilot.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.EstiloManiobras
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Ajustes de conducción: zoom del mapa, icono de posición, orientación y estilo de las maniobras, persistidos en el
 * repositorio.
 */
@HiltViewModel
class AjustesViewModel
    @Inject
    constructor(
        private val repositorio: RepositorioAjustesConduccion,
    ) : ViewModel() {
        val uiState: StateFlow<AjustesConduccion> =
            repositorio.ajustes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), AjustesConduccion())

        /** El nivel se acota a -2..2 (más lejos a más cerca). */
        fun onNivelZoom(nivel: Int) {
            guardar { it.copy(nivelZoom = nivel.coerceIn(AjustesConduccion.NIVEL_MIN, AjustesConduccion.NIVEL_MAX)) }
        }

        fun onIcono(icono: IconoPosicion) {
            guardar { it.copy(icono = icono) }
        }

        fun onOrientacion(orientacion: Orientacion) {
            guardar { it.copy(orientacion = orientacion) }
        }

        fun onEstiloManiobras(estilo: EstiloManiobras) {
            guardar { it.copy(estiloManiobras = estilo) }
        }

        private fun guardar(cambio: (AjustesConduccion) -> AjustesConduccion) {
            viewModelScope.launch { repositorio.guardar(cambio(repositorio.ajustes.first())) }
        }

        private companion object {
            const val PARADA_MS = 5_000L
        }
    }
