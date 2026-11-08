package app.trombito.rallycopilot.ui.miscarreteras

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.carreteras.CalculoCarreteras
import app.trombito.rallycopilot.domain.carreteras.MisCarreteras
import app.trombito.rallycopilot.ui.comun.PARADA_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException
import javax.inject.Inject

/**
 * «Mis carreteras»: empieza cargando y, cuando [CalculoCarreteras] termina (en segundo plano), enseña el resumen. Si
 * la base de datos o los puntos fallan, pasa a estado de error en vez de cerrar la app.
 */
@HiltViewModel
class MisCarreterasViewModel
    @Inject
    constructor(
        calculador: CalculoCarreteras,
    ) : ViewModel() {
        val uiState: StateFlow<MisCarreterasUiState> =
            calculador
                .observar()
                .map { it.aEstado() }
                .catch { fallo ->
                    if (fallo is SQLiteException || fallo is IOException || fallo is IllegalStateException) {
                        emit(MisCarreterasUiState(cargando = false, error = true))
                    } else {
                        throw fallo
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), MisCarreterasUiState())
    }

private fun MisCarreteras.aEstado() =
    MisCarreterasUiState(
        cargando = false,
        recorridos = resumen.recorridos,
        metrosTotales = resumen.metrosTotales,
        curvas = resumen.curvas,
        trazos = resumen.vias.map { TrazoCarretera(it.via.puntos, it.pasadas) },
        carreteras = resumen.carreteras,
        hayVias = hayVias,
    )
