package app.trombito.rallycopilot.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.tramos.EstadisticasPerfil
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.motor.analisis.PasadaRegistrada
import app.trombito.rallycopilot.motor.analisis.PerfilPiloto
import app.trombito.rallycopilot.motor.analisis.RecorridoRegistrado
import app.trombito.rallycopilot.motor.analisis.TramoRegistrado
import app.trombito.rallycopilot.motor.tramos.Tramo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Perfil del piloto: agrega con `PerfilPiloto` del motor los recorridos y las pasadas de todos los tramos. La
 * evolución que se ve es la del tramo elegido (por defecto, el de más pasadas).
 */
@HiltViewModel
class PerfilViewModel
    @Inject
    constructor(
        private val repositorio: RepositorioTramos,
    ) : ViewModel() {
        private val seleccion = MutableStateFlow<String?>(null)

        @OptIn(ExperimentalCoroutinesApi::class)
        private val perfil: Flow<PerfilPiloto> =
            combine(
                repositorio.observarPerfil(),
                repositorio.observarResumenes(FiltroTramos.TODOS),
            ) { totales, tramos ->
                totales to tramos
            }.mapLatest { (totales, tramos) -> construir(totales, tramos) }

        val uiState: StateFlow<PerfilUiState> =
            combine(perfil, seleccion) { perfilActual, elegido ->
                PerfilUiState(cargando = false, perfil = perfilActual, seleccionadoId = elegido)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), PerfilUiState())

        /** Muestra la evolución de otro tramo. */
        fun onSeleccionar(tramoId: String) {
            seleccion.value = tramoId
        }

        private suspend fun construir(
            totales: EstadisticasPerfil,
            tramos: List<ResumenTramo>,
        ): PerfilPiloto {
            val registrados =
                tramos.map { resumen ->
                    TramoRegistrado(
                        tramoId = resumen.id,
                        nombre = resumen.nombre,
                        // La distancia cronometrada llega a la meta, no al final de la traza.
                        distanciaM = Tramo.metaDe(resumen.longitudM),
                        pasadas =
                            repositorio.pasadasDetalladas(resumen.id).map {
                                PasadaRegistrada(it.id.toString(), it.pasada, it.inicioMs)
                            },
                    )
                }
            val recorridos =
                if (totales.recorridos > 0) {
                    listOf(RecorridoRegistrado(totales.ultimoRecorridoMs ?: 0L, totales.distanciaTotalM))
                } else {
                    emptyList()
                }
            return PerfilPiloto.de(registrados, recorridos)
        }

        private companion object {
            const val PARADA_MS = 5_000L
        }
    }
