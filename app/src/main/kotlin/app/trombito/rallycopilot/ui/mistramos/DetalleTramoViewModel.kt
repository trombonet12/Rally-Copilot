package app.trombito.rallycopilot.ui.mistramos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.trombito.rallycopilot.domain.datos.RepositorioDatosMapa
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.PasadaDetallada
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.RutaTramo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Detalle de un tramo: traza, estilo del mapa para dibujarla, pasadas contra la mejor, favorito y nombre. */
@HiltViewModel
class DetalleTramoViewModel
    @Inject
    constructor(
        guardado: SavedStateHandle,
        private val repositorio: RepositorioTramos,
        private val datosMapa: RepositorioDatosMapa,
    ) : ViewModel() {
        private val tramoId = guardado.toRoute<RutaTramo>().tramoId
        private val renombrando = MutableStateFlow(false)
        private val estiloMapa = MutableStateFlow<String?>(null)

        private class Carga(
            val traza: List<PuntoGeo>,
            val pasadas: List<FilaPasada>,
        )

        private val resumen: Flow<ResumenTramo?> =
            repositorio.observarResumenes(FiltroTramos.TODOS).map { lista -> lista.firstOrNull { it.id == tramoId } }

        // Las pasadas solo se releen si cambia su número o la mejor; renombrar o marcar favorito no las toca.
        @OptIn(ExperimentalCoroutinesApi::class)
        private val carga: Flow<Carga> =
            resumen
                .map { it?.pasadas to it?.mejorTotalMs }
                .distinctUntilChanged()
                .mapLatest {
                    Carga(
                        traza = repositorio.tramo(tramoId)?.traza.orEmpty(),
                        pasadas = filasDe(repositorio.pasadasDetalladas(tramoId)),
                    )
                }

        val uiState: StateFlow<DetalleTramoUiState> =
            combine(resumen, carga, renombrando, estiloMapa) { tramo, datos, editando, estilo ->
                DetalleTramoUiState(
                    cargando = false,
                    tramo = tramo,
                    traza = datos.traza,
                    estiloMapaJson = estilo,
                    pasadas = datos.pasadas,
                    renombrando = editando && tramo != null,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), DetalleTramoUiState())

        init {
            viewModelScope.launch { estiloMapa.value = datosMapa.estilos()?.vectorial }
        }

        fun onFavorito() {
            val tramo = uiState.value.tramo ?: return
            viewModelScope.launch { repositorio.marcarFavorito(tramo.id, !tramo.favorito) }
        }

        fun onPedirRenombrar() {
            renombrando.value = true
        }

        fun onCancelarRenombrar() {
            renombrando.value = false
        }

        /** [nombre] en blanco devuelve el nombre automático. */
        fun onRenombrar(nombre: String) {
            renombrando.value = false
            viewModelScope.launch { repositorio.renombrar(tramoId, nombre.trim().ifEmpty { null }) }
        }

        private fun filasDe(pasadas: List<PasadaDetallada>): List<FilaPasada> {
            val mejor = pasadas.minOfOrNull { it.totalMs } ?: return emptyList()
            return pasadas.map {
                FilaPasada(it.id, it.inicioMs, it.totalMs, it.totalMs - mejor, it.esMejor)
            }
        }

        private companion object {
            const val PARADA_MS = 5_000L
        }
    }
