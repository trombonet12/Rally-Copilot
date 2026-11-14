package app.trombito.rallycopilot.ui.mapa

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.carreteras.CalculoCarreteras
import app.trombito.rallycopilot.domain.carreteras.MisCarreteras
import app.trombito.rallycopilot.ui.comun.PARADA_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.io.IOException
import javax.inject.Inject

/**
 * La capa de calor del mapa: interruptor y modo (guardados en [AlmacenCalor]), la ficha de la carretera tocada y las
 * vías conducidas con su uso y velocidad media (de [CalculoCarreteras], que las cachea por recorrido). Con la capa
 * apagada no se calcula nada. Un fallo al leer los recorridos deja la capa vacía en vez de cerrar la app.
 */
@HiltViewModel
class CalorViewModel
    @Inject
    constructor(
        calculo: CalculoCarreteras,
        private val almacen: AlmacenCalor,
    ) : ViewModel() {
        private val activo = MutableStateFlow(almacen.activo())
        private val modo = MutableStateFlow(almacen.modo())
        private val ficha = MutableStateFlow<FichaCalor?>(null)

        val estado: StateFlow<CalorUi> =
            combine(activo, modo, ficha, ::CalorUi)
                .stateIn(viewModelScope, SharingStarted.Eagerly, CalorUi(activo.value, modo.value, null))

        /** Las vías conducidas listas para pintar; vacío con la capa apagada o mientras se calculan. */
        @OptIn(ExperimentalCoroutinesApi::class)
        val tramos: StateFlow<List<TramoCalor>> =
            activo
                .flatMapLatest { if (it) calculo.observar().map(MisCarreteras::aTramosCalor) else flowOf(emptyList()) }
                .catch { fallo ->
                    if (fallo is SQLiteException || fallo is IOException || fallo is IllegalStateException) {
                        emit(emptyList())
                    } else {
                        throw fallo
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), emptyList())

        fun onActivo(encendida: Boolean) {
            almacen.guardarActivo(encendida)
            activo.value = encendida
            if (!encendida) ficha.value = null
        }

        fun onModo(nuevo: ModoCalor) {
            almacen.guardarModo(nuevo)
            modo.value = nuevo
        }

        /** Se tocó una carretera de la capa (su ficha) o se tocó otra cosa (null: se cierra la ficha). */
        fun onFicha(nueva: FichaCalor?) {
            ficha.update { nueva }
        }

        fun onCerrarFicha() = onFicha(null)
    }

/** Las vías conducidas como tramos de calor: uso del agregado y velocidad media de [MisCarreteras.velocidades]. */
internal fun MisCarreteras.aTramosCalor(): List<TramoCalor> {
    val velocidadPorVia = velocidades.associateBy { it.via.id }
    return resumen.vias.map { conducida ->
        TramoCalor(
            viaId = conducida.via.id,
            nombre =
                conducida.via.nombre
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
            ref =
                conducida.via.ref
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
            puntos = conducida.via.puntos,
            pasadas = conducida.pasadas,
            km = metrosAKm(conducida.metros),
            velMediaKmh = velocidadPorVia[conducida.via.id]?.velMediaKmh,
        )
    }
}
