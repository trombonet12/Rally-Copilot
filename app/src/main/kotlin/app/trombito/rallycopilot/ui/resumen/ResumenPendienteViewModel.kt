package app.trombito.rallycopilot.ui.resumen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.resumen.ViajesTerminados
import app.trombito.rallycopilot.ui.comun.PARADA_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Cuánto vale un viaje terminado sin abrir su resumen: pasado esto ya no se enseña solo (queda en la lista). */
internal const val CADUCIDAD_RESUMEN_MS = 30 * 60 * 1_000L

/** Por debajo de esta velocidad el coche cuenta como parado: ahí se enseña el resumen sin molestar al conducir. */
internal const val VELOCIDAD_PARADO_KMH = 5

/**
 * El resumen que toca enseñar tras terminar un viaje: el recorrido que el servicio acaba de cerrar (más de 1 km) y
 * aún no se ha abierto. Vive en la actividad (lo comparten el mapa y la raíz de navegación).
 */
@HiltViewModel
class ResumenPendienteViewModel
    internal constructor(
        private val viajes: ViajesTerminados,
        private val reloj: () -> Long,
    ) : ViewModel() {
        @Inject
        constructor(viajes: ViajesTerminados) : this(viajes, System::currentTimeMillis)

        /** Id del recorrido cuyo resumen toca enseñar; null si no hay ninguno reciente. */
        val pendiente: StateFlow<Long?> =
            viajes.ultimo
                .map { viaje -> viaje?.takeIf { reloj() - it.terminadoMs <= CADUCIDAD_RESUMEN_MS }?.recorridoId }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), null)

        /** Ya se abrió el resumen de [recorridoId]: no vuelve a salir solo. */
        fun consumir(recorridoId: Long) = viajes.consumir(recorridoId)
    }

/**
 * Si toca abrir el resumen con el mapa a la vista: hay un [pendiente] y el coche está parado ([velocidadKmh] bajo el
 * umbral, o desconocida: sin señal no se puede seguir esperando). Conduciendo no se abre nada.
 */
internal fun resumenAlParar(
    pendiente: Long?,
    velocidadKmh: Int?,
): Long? = pendiente?.takeIf { velocidadKmh == null || velocidadKmh < VELOCIDAD_PARADO_KMH }
