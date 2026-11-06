package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.domain.tramos.ControlSalidaCrono
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Cronómetro en vivo: lo publica la [SesionCrono] de la conducción en curso y lo lee quien quiera (la UI). */
@Singleton
class CronoEnVivoImpl
    @Inject
    constructor() :
    ProveedorCrono,
        ControlSalidaCrono {
        private val actual = MutableStateFlow<CronoEnCurso?>(null)

        override val crono: StateFlow<CronoEnCurso?> = actual.asStateFlow()

        @Volatile private var accionSalida: (() -> Unit)? = null

        fun publicar(crono: CronoEnCurso?) {
            actual.value = crono
            if (crono == null) accionSalida = null
        }

        /** La conducción en curso registra aquí cómo dar su salida (manual); se olvida al cerrar. */
        fun registrarSalida(accion: () -> Unit) {
            accionSalida = accion
        }

        override fun darSalida() {
            accionSalida?.invoke()
        }
    }
