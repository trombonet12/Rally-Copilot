package app.trombito.rallycopilot.ui.resumen

import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.trombito.rallycopilot.domain.datos.RepositorioDatosMapa
import app.trombito.rallycopilot.domain.resumen.CalculoResumenViaje
import app.trombito.rallycopilot.ui.RutaResumenViaje
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/**
 * Presentación del resumen de un viaje: pide a [CalculoResumenViaje] el del recorrido de la ruta (el cálculo va en
 * segundo plano) y el estilo del mapa. Si la base de datos o los puntos fallan, pasa a estado de error en vez de
 * cerrar la app.
 */
@HiltViewModel
class ResumenViajeViewModel
    @Inject
    constructor(
        guardado: SavedStateHandle,
        private val calculo: CalculoResumenViaje,
        private val datosMapa: RepositorioDatosMapa,
    ) : ViewModel() {
        private val recorridoId = guardado.toRoute<RutaResumenViaje>().recorridoId
        private val estado = MutableStateFlow(ResumenViajeUiState())
        val uiState: StateFlow<ResumenViajeUiState> = estado.asStateFlow()

        init {
            viewModelScope.launch { cargar() }
        }

        private suspend fun cargar() {
            estado.value =
                try {
                    val viaje = calculo.resumenDe(recorridoId)
                    val estilo = viaje?.let { datosMapa.estilos()?.vectorial }
                    ResumenViajeUiState(cargando = false, viaje = viaje, estiloMapaJson = estilo)
                } catch (_: SQLiteException) {
                    ResumenViajeUiState(cargando = false, error = true)
                } catch (_: IOException) {
                    ResumenViajeUiState(cargando = false, error = true)
                } catch (_: IllegalStateException) {
                    ResumenViajeUiState(cargando = false, error = true)
                }
        }
    }
