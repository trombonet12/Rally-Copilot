package app.trombito.rallycopilot.ui.navegacion.notas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.notas.ProveedorNotas
import app.trombito.rallycopilot.motor.notas.SelectorNotas
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val PARADA_MS = 5_000L

/** Cuántos elementos enseña la franja: el grande y dos pequeños. */
internal const val NOTAS_VISIBLES = 3

/**
 * Presentación de la franja de notas: combina las [ProveedorNotas.notas] en vivo (hoja, posición y velocidad) con la
 * anticipación y las notas simplificadas de los ajustes, y deja a [SelectorNotas] elegir los próximos elementos.
 */
@HiltViewModel
class NotasViewModel
    @Inject
    constructor(
        proveedor: ProveedorNotas,
        ajustes: RepositorioAjustesConduccion,
    ) : ViewModel() {
        private val selector = SelectorNotas()

        val uiState: StateFlow<NotasUiState> =
            combine(proveedor.notas, ajustes.ajustes) { notas, preferencias ->
                if (notas == null) {
                    NotasUiState(simplificadas = preferencias.notasSimplificadas)
                } else {
                    NotasUiState(
                        visibles =
                            selector.siguientes(
                                hoja = notas.hoja,
                                posicionM = notas.posicionM,
                                vMs = notas.vMs ?: 0.0,
                                nivel = preferencias.anticipacion,
                                n = NOTAS_VISIBLES,
                            ),
                        simplificadas = preferencias.notasSimplificadas,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), NotasUiState())
    }
