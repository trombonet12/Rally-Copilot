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

        /** Quién publica ahora: solo la sesión vigente puede dar la salida o retirar el cronómetro. */
        private var propietario: Any? = null

        /**
         * Enlaza una [SesionCrono] con el cronómetro en vivo: [crear] recibe el publicador de esa sesión (el que debe
         * usar como `publicar`) y devuelve la sesión. La salida manual ([darSalida]) va a la sesión que publicó la
         * última vez, y su cierre solo retira el cronómetro si sigue siendo la vigente: ni una sesión que nunca
         * publica (la de limpiar huérfanos) ni el cierre tardío de la anterior le quitan la salida a la nueva.
         */
        fun enlazar(crear: ((CronoEnCurso?) -> Unit) -> SesionCrono): SesionCrono {
            val duenio = Any()
            var sesion: SesionCrono? = null
            val publicador: (CronoEnCurso?) -> Unit = { crono ->
                publicarDe(duenio, crono) { sesion?.darSalida() }
            }
            return crear(publicador).also { sesion = it }
        }

        @Synchronized
        private fun publicarDe(
            duenio: Any,
            crono: CronoEnCurso?,
            salida: () -> Unit,
        ) {
            if (crono != null) {
                propietario = duenio
                accionSalida = salida
                actual.value = crono
            } else if (propietario === duenio) {
                propietario = null
                accionSalida = null
                actual.value = null
            }
        }

        override fun darSalida() {
            accionSalida?.invoke()
        }
    }
