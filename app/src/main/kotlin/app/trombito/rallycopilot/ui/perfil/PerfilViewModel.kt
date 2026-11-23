package app.trombito.rallycopilot.ui.perfil

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.perfil.CalculoTablero
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
 * Perfil del piloto: empieza cargando y, cuando [CalculoTablero] termina (en segundo plano), enseña el tablero. Si la
 * base de datos o los puntos fallan, pasa a estado de error en vez de cerrar la app.
 */
@HiltViewModel
class PerfilViewModel
    @Inject
    constructor(
        calculo: CalculoTablero,
    ) : ViewModel() {
        val uiState: StateFlow<PerfilUiState> =
            calculo
                .observar()
                .map { PerfilUiState(cargando = false, tablero = it) }
                .catch { fallo ->
                    if (fallo is SQLiteException || fallo is IOException || fallo is IllegalStateException) {
                        emit(PerfilUiState(cargando = false, error = true))
                    } else {
                        throw fallo
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), PerfilUiState())
    }
