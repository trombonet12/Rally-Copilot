package app.trombito.rallycopilot.ui.mapa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.datos.RepositorioDatosMapa
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.domain.vias.ProveedorViaActual
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.motor.velocidad.EstimadorVelocidad
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.velocidad.kmh
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Presentación del mapa: combina el estado de los datos, los estilos, la capa elegida y las fijaciones GPS (que solo
 * se recogen mientras la pantalla está visible: la UI recoge `uiState` con el ciclo de vida) con la velocidad
 * estimada y la vía actual con su límite.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MapaViewModel
    @Inject
    constructor(
        private val repositorio: RepositorioDatosMapa,
        ubicacion: FuenteUbicacion,
        private val vias: ProveedorViaActual,
    ) : ViewModel() {
        private val capa = MutableStateFlow(CapaMapa.VECTORIAL)
        private val permiso = MutableStateFlow(ubicacion.tienePermiso())

        private class Lectura(
            val posicion: Fijacion?,
            val velocidadKmh: Int?,
            val via: ViaActual?,
        )

        private val estilos =
            repositorio.estado
                .map { it.instalados }
                .distinctUntilChanged()
                .map { repositorio.estilos() }

        private val lecturas: Flow<Lectura> =
            permiso.flatMapLatest { concedido ->
                if (!concedido) {
                    flowOf(LECTURA_VACIA)
                } else {
                    val estimador = EstimadorVelocidad()
                    var viaPrevia: ViaActual? = null
                    ubicacion
                        .fijaciones()
                        .transform { fijacion ->
                            val velocidad = estimador.actualizar(fijacion)?.let(::kmh)
                            // Primero la velocidad: la vía puede tardar (carga perezosa del archivo de vías).
                            emit(Lectura(fijacion, velocidad, viaPrevia))
                            viaPrevia = vias.viaEn(fijacion)
                            emit(Lectura(fijacion, velocidad, viaPrevia))
                        }.onStart { emit(LECTURA_VACIA) }
                }
            }

        val uiState: StateFlow<MapaUiState> =
            combine(repositorio.estado, estilos, capa, lecturas) { datos, estilosMapa, capaElegida, lectura ->
                MapaUiState(
                    cargando = false,
                    datos = datos,
                    estilos = estilosMapa,
                    capaElegida = capaElegida,
                    posicion = lectura.posicion,
                    velocidadKmh = lectura.velocidadKmh,
                    via = lectura.via,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), MapaUiState())

        fun onCapa(nueva: CapaMapa) {
            capa.value = nueva
        }

        /** La UI avisa de si tiene la ubicación precisa (el GPS la exige); al concederse empieza a recoger el GPS. */
        fun onPermisoUbicacion(precisa: Boolean) {
            permiso.value = precisa
        }

        fun onDescartarAviso() {
            viewModelScope.launch { repositorio.descartarAviso() }
        }

        private companion object {
            const val PARADA_MS = 5_000L
            val LECTURA_VACIA = Lectura(null, null, null)
        }
    }
