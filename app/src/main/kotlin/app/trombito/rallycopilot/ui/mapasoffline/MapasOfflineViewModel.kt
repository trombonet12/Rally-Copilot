package app.trombito.rallycopilot.ui.mapasoffline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.datos.AjustesMapas
import app.trombito.rallycopilot.domain.datos.ProgresoDescarga
import app.trombito.rallycopilot.domain.datos.RepositorioDatosMapa
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapasOfflineViewModel
    @Inject
    constructor(
        private val repositorio: RepositorioDatosMapa,
    ) : ViewModel() {
        // Solo lo toca el combine (secuencial). Se olvida al terminar la descarga.
        private var ultimoProgreso: ProgresoDescarga? = null

        val uiState: StateFlow<MapasOfflineUiState> =
            combine(repositorio.estado, repositorio.ajustes) { datos, ajustes ->
                val descarga = datos.descarga
                ultimoProgreso =
                    when {
                        descarga == null -> null
                        descarga.bytesTotales > 0 -> descarga
                        else -> ultimoProgreso
                    }
                MapasOfflineUiState(cargando = false, datos = datos, ajustes = ajustes, ultimoProgreso = ultimoProgreso)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), MapasOfflineUiState())

        fun onDescargar() = repositorio.descargar()

        fun onCancelar() = repositorio.cancelar()

        fun onBorrar() {
            viewModelScope.launch { repositorio.borrar() }
        }

        fun onSoloWifi(soloWifi: Boolean) {
            guardar { it.copy(soloWifi = soloWifi) }
        }

        fun onLimiteGb(gigabytes: Int) {
            guardar { it.copy(limiteBytes = gigabytes * BYTES_POR_GB) }
        }

        private fun guardar(cambio: (AjustesMapas) -> AjustesMapas) {
            viewModelScope.launch { repositorio.guardarAjustes(cambio(repositorio.ajustes.first())) }
        }

        private companion object {
            const val PARADA_MS = 5_000L
        }
    }
