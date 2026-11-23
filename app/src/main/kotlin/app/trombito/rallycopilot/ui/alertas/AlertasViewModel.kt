package app.trombito.rallycopilot.ui.alertas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.alertas.ProveedorAlertas
import app.trombito.rallycopilot.domain.alertas.ProveedorRadaresMapa
import app.trombito.rallycopilot.domain.alertas.RepositorioIncidencias
import app.trombito.rallycopilot.domain.diagnostico.RegistroDiagnostico
import app.trombito.rallycopilot.ui.comun.PARADA_MS
import app.trombito.rallycopilot.ui.mapa.NotasEnMapa
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** El reloj de la antigüedad de los datos avanza cada minuto aunque no llegue ninguna alerta nueva. */
private const val PULSO_MS = 60_000L

/** El bocadillo de lo tocado en el mapa se cierra solo a los 8 s. */
internal const val CIERRE_SELECCION_MS = 8_000L

/**
 * Presentación de las alertas de la conducción: combina las [ProveedorAlertas.alertas] en vivo (radar próximo, media
 * de tramo, exceso, incidencias relevantes) con las incidencias vigentes del [RepositorioIncidencias] (para el mapa
 * fuera de conducción) y lo tocado en el mapa (incidencia, radar, tramo de radar, bandera de tramo o nota de la hoja).
 * Los toques se anotan en el registro de diagnóstico, sin coordenadas ni ids.
 */
@HiltViewModel
class AlertasViewModel
    @Inject
    constructor(
        proveedor: ProveedorAlertas,
        incidencias: RepositorioIncidencias,
        radares: ProveedorRadaresMapa,
        private val registro: RegistroDiagnostico,
    ) : ViewModel() {
        /** Reloj de pared (ms); sustituible en los tests. */
        internal var reloj: () -> Long = System::currentTimeMillis

        private val seleccion = MutableStateFlow<SeleccionMapa?>(null)
        private var cierreAutomatico: Job? = null

        private val pulso =
            flow {
                while (true) {
                    emit(Unit)
                    delay(PULSO_MS)
                }
            }

        /** Todos los radares y tramos, leídos una vez en segundo plano; vacíos hasta entonces (o sin archivo). */
        private val radaresUi = MutableStateFlow(RadaresUi())

        /** Curvas, avisos y banderas que pinta el mapa: los publican otros ViewModels ([actualizarTocables]). */
        private val externos = MutableStateFlow(ElementosTocables())

        init {
            viewModelScope.launch { radaresUi.value = radaresUiDe(radares.radaresEnMapa()) }
        }

        val uiState: StateFlow<AlertasUiState> =
            combine(
                proveedor.alertas,
                incidencias.incidencias,
                seleccion,
                pulso,
                combine(radaresUi, externos) { r, e -> r to e },
            ) { alertas, vigentes, elegida, _, (r, e) ->
                presentarAlertas(alertas, vigentes, reloj(), elegida, r, e)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), AlertasUiState())

        /**
         * Se toca un elemento del mapa y sale su bocadillo; `null` (tocar fuera) lo cierra. Tocar el mismo otra vez lo
         * cierra y, si no, se cierra solo a los 8 s.
         */
        fun onSeleccionar(elegida: SeleccionMapa?) {
            cierreAutomatico?.cancel()
            cierreAutomatico = null
            val nueva = if (elegida != null && elegida.clave == seleccion.value?.clave) null else elegida
            seleccion.value = nueva
            if (nueva != null) {
                cierreAutomatico =
                    viewModelScope.launch {
                        delay(CIERRE_SELECCION_MS)
                        if (seleccion.value == nueva) seleccion.value = null
                    }
            }
        }

        /**
         * Las curvas y avisos de la hoja y las banderas de los tramos que hay ahora en el mapa (los publican otros
         * ViewModels): sin ellos no se puede montar el bocadillo de una nota o de una bandera tocadas.
         */
        fun actualizarTocables(
            notas: NotasEnMapa,
            banderas: List<BanderaMapa>,
        ) {
            externos.value = ElementosTocables(banderas = banderas, notas = notas)
        }

        /** Anota un toque en el mapa («toque capa=radar resultado=bocadillo»): sin coordenadas ni ids. */
        fun registrarToque(linea: String) = registro.info(ETIQUETA_DIAGNOSTICO, linea)

        private companion object {
            const val ETIQUETA_DIAGNOSTICO = "Mapa"
        }
    }
