package app.trombito.rallycopilot.data.resumen

import app.trombito.rallycopilot.domain.resumen.ViajeTerminado
import app.trombito.rallycopilot.domain.resumen.ViajesTerminados
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** [ViajesTerminados] en memoria: un solo pendiente (el último), que sustituye al anterior. */
@Singleton
class ViajesTerminadosEnMemoria
    @Inject
    constructor() : ViajesTerminados {
        private val _ultimo = MutableStateFlow<ViajeTerminado?>(null)
        override val ultimo: StateFlow<ViajeTerminado?> = _ultimo.asStateFlow()

        override fun publicar(viaje: ViajeTerminado) {
            _ultimo.value = viaje
        }

        override fun consumir(recorridoId: Long) {
            _ultimo.compareAndSet(_ultimo.value?.takeIf { it.recorridoId == recorridoId }, null)
        }
    }
