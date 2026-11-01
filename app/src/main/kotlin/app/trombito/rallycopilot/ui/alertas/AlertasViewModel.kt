package app.trombito.rallycopilot.ui.alertas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.alertas.ProveedorAlertas
import app.trombito.rallycopilot.domain.alertas.RepositorioIncidencias
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val PARADA_MS = 5_000L

/** El reloj de la antigüedad de los datos avanza cada minuto aunque no llegue ninguna alerta nueva. */
private const val PULSO_MS = 60_000L

/**
 * Presentación de las alertas de la conducción: combina las [ProveedorAlertas.alertas] en vivo (radar próximo, media
 * de tramo, exceso, incidencias relevantes) con las incidencias vigentes del [RepositorioIncidencias] (para el mapa
 * fuera de conducción) y la incidencia tocada en el mapa.
 */
@HiltViewModel
class AlertasViewModel
    @Inject
    constructor(
        proveedor: ProveedorAlertas,
        incidencias: RepositorioIncidencias,
    ) : ViewModel() {
        /** Reloj de pared (ms); sustituible en los tests. */
        internal var reloj: () -> Long = System::currentTimeMillis

        private val seleccion = MutableStateFlow<String?>(null)

        private val pulso =
            flow {
                while (true) {
                    emit(Unit)
                    delay(PULSO_MS)
                }
            }

        val uiState: StateFlow<AlertasUiState> =
            combine(proveedor.alertas, incidencias.incidencias, seleccion, pulso) { alertas, vigentes, elegida, _ ->
                presentarAlertas(alertas, vigentes, reloj(), elegida)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), AlertasUiState())

        /** Se toca un marcador del mapa ([id]); `null` cierra la tarjeta. Tocar el mismo otra vez la cierra. */
        fun onSeleccionar(id: String?) {
            seleccion.value = if (id == seleccion.value) null else id
        }
    }
