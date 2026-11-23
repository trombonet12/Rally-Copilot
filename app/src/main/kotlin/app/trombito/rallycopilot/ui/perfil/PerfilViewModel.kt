package app.trombito.rallycopilot.ui.perfil

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.di.DispatcherDefault
import app.trombito.rallycopilot.domain.perfil.CalculoTablero
import app.trombito.rallycopilot.domain.perfil.CalculoTemporada
import app.trombito.rallycopilot.domain.perfil.TemporadaPiloto
import app.trombito.rallycopilot.motor.analisis.ResumenMes
import app.trombito.rallycopilot.ui.comun.PARADA_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Lo que va llegando de la temporada: nada todavía, el resultado o un fallo de lectura. */
private data class EstadoTemporada(
    val temporada: TemporadaPiloto? = null,
    val error: Boolean = false,
)

private fun esFalloDeLectura(fallo: Throwable) =
    fallo is SQLiteException || fallo is IOException || fallo is IllegalStateException

/**
 * Perfil del piloto: empieza cargando y, cuando [CalculoTablero] termina (en segundo plano), enseña el tablero; la
 * temporada llega después, también en segundo plano. Si la base de datos o los puntos fallan, el tablero pasa a estado
 * de error en vez de cerrar la app, y un fallo solo de la temporada deja el tablero y lo avisa en su pestaña.
 */
@HiltViewModel
class PerfilViewModel
    @Inject
    constructor(
        calculo: CalculoTablero,
        calculoTemporada: CalculoTemporada,
        @DispatcherDefault private val despachador: CoroutineDispatcher,
    ) : ViewModel() {
        val uiState: StateFlow<PerfilUiState> =
            combine(calculo.observar(), temporadaDe(calculoTemporada)) { tablero, temporada ->
                PerfilUiState(
                    cargando = false,
                    tablero = tablero,
                    temporada = temporada.temporada,
                    temporadaError = temporada.error,
                )
            }.catch { fallo ->
                if (esFalloDeLectura(fallo)) {
                    emit(PerfilUiState(cargando = false, error = true))
                } else {
                    throw fallo
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), PerfilUiState())

        private fun temporadaDe(calculo: CalculoTemporada): Flow<EstadoTemporada> =
            calculo
                .observar()
                .map { EstadoTemporada(temporada = it) }
                .catch { fallo ->
                    if (esFalloDeLectura(fallo)) emit(EstadoTemporada(error = true)) else throw fallo
                }.onStart { emit(EstadoTemporada()) }

        /** Dibuja y escribe el póster del [resumen] en la caché, fuera del hilo principal; null si no se pudo. */
        suspend fun escribirPosterMes(
            contexto: Context,
            resumen: ResumenMes,
        ): File? = withContext(despachador) { escribirPoster(contexto, resumen) }
    }
