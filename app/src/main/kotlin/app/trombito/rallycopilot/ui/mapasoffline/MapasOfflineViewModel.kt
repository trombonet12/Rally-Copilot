package app.trombito.rallycopilot.ui.mapasoffline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.datos.AjustesMapas
import app.trombito.rallycopilot.domain.datos.PrecargaDetalle
import app.trombito.rallycopilot.domain.datos.ProgresoDescarga
import app.trombito.rallycopilot.domain.datos.RepositorioDatosMapa
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapasOfflineViewModel
    @Inject
    constructor(
        private val repositorio: RepositorioDatosMapa,
        private val precarga: PrecargaDetalle = PrecargaDetalle.Ninguna,
    ) : ViewModel() {
        // Solo lo toca el combine (secuencial). Se olvida al terminar la descarga.
        private var ultimoProgreso: ProgresoDescarga? = null

        private val bytesSatelite = MutableStateFlow<Long?>(null)

        init {
            // Lo que falta de satélite cambia al instalar o borrar (también el z16 ya puesto con el z17 por bajar).
            viewModelScope.launch {
                repositorio.estado
                    .map { it.instalados }
                    .distinctUntilChanged()
                    .collectLatest { bytesSatelite.value = repositorio.tamanoSatelite() }
            }
        }

        val uiState: StateFlow<MapasOfflineUiState> =
            combine(
                repositorio.estado,
                repositorio.ajustes,
                bytesSatelite,
                precarga.estado,
            ) { datos, ajustes, tamanoSatelite, detalle ->
                val descarga = datos.descarga
                ultimoProgreso =
                    when {
                        descarga == null -> null
                        descarga.bytesTotales > 0 -> descarga
                        else -> ultimoProgreso
                    }
                MapasOfflineUiState(
                    cargando = false,
                    datos = datos,
                    ajustes = ajustes,
                    ultimoProgreso = ultimoProgreso,
                    bytesSatelite = tamanoSatelite,
                    detalle = detalle,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), MapasOfflineUiState())

        fun onDescargar() = repositorio.descargar()

        fun onCancelar() = repositorio.cancelar()

        /** Borra el detalle z18-19 (el MBTiles y las rutas pendientes); el satelite z16-17 no se toca. */
        fun onBorrarDetalle() {
            viewModelScope.launch { precarga.borrar() }
        }

        fun onCancelarDetalle() = precarga.cancelar()

        /** Borra solo el satélite. */
        fun onBorrar() {
            viewModelScope.launch {
                repositorio.borrar()
                if (bytesSatelite.value == null) cargarTamanoSatelite()
            }
        }

        /** Pide lo que falta de satélite a los manifests; sin red se queda en null y la pantalla no da cifra. */
        private fun cargarTamanoSatelite() {
            viewModelScope.launch { bytesSatelite.value = repositorio.tamanoSatelite() }
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
