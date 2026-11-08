package app.trombito.rallycopilot.ui.navegacion.notas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.escucha.AnotadorNotaMal
import app.trombito.rallycopilot.domain.escucha.ResultadoNotaMal
import app.trombito.rallycopilot.domain.notas.ProveedorNotas
import app.trombito.rallycopilot.motor.notas.SelectorNotas
import app.trombito.rallycopilot.motor.voz.HojaFiltrada
import app.trombito.rallycopilot.ui.comun.PARADA_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Cuántos elementos enseña la franja: el grande y dos pequeños. */
internal const val NOTAS_VISIBLES = 3

/**
 * Presentación de la franja de notas: combina las [ProveedorNotas.notas] en vivo (hoja, posición y velocidad) con la
 * anticipación y las notas simplificadas de los ajustes, y deja a [SelectorNotas] elegir los próximos elementos de la
 * hoja sin los avisos (cruce, rotonda) que el piloto ha desactivado: lo que se ve es lo que se dice.
 */
@HiltViewModel
class NotasViewModel
    @Inject
    constructor(
        proveedor: ProveedorNotas,
        ajustes: RepositorioAjustesConduccion,
        private val anotador: AnotadorNotaMal,
    ) : ViewModel() {
        private val selector = SelectorNotas()
        private val hojaFiltrada = HojaFiltrada()

        val uiState: StateFlow<NotasUiState> =
            combine(proveedor.notas, ajustes.ajustes) { notas, preferencias ->
                if (notas == null) {
                    NotasUiState(simplificadas = preferencias.notasSimplificadas)
                } else {
                    NotasUiState(
                        visibles =
                            selector.siguientes(
                                hoja = hojaFiltrada.de(notas.hoja, preferencias.filtroIndicaciones),
                                posicionM = notas.posicionM,
                                vMs = notas.vMs ?: 0.0,
                                nivel = preferencias.anticipacion,
                                n = NOTAS_VISIBLES,
                            ),
                        simplificadas = preferencias.notasSimplificadas,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), NotasUiState())

        /**
         * «Esta nota estaba mal» (pulsación larga del banner): marca la última nota dicha. Se hace en el ámbito del
         * ViewModel, así que se guarda aunque la tarjeta que lo pidió salga de la pantalla.
         */
        suspend fun anotarNotaMal(): ResultadoNotaMal = viewModelScope.async { anotador.anotar() }.await()
    }
