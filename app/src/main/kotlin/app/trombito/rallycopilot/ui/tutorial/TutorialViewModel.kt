package app.trombito.rallycopilot.ui.tutorial

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Estado del tutorial para la navegación: si la bienvenida ya se vio (se lee de golpe, para decidir la pantalla de
 * arranque sin parpadeo) y qué pistas ya se enseñaron.
 */
@HiltViewModel
class TutorialViewModel
    @Inject
    constructor(
        private val almacen: AlmacenTutorial,
    ) : ViewModel() {
        /** Verdadero si la bienvenida ya se vio. Se fija al crear: no cambia durante la sesión de arranque. */
        val bienvenidaVista: Boolean = almacen.bienvenidaVista()

        private val _pistasVistas = MutableStateFlow(almacen.pistasVistas())
        val pistasVistas: StateFlow<Set<Pista>> = _pistasVistas.asStateFlow()

        fun onBienvenidaVista() = almacen.marcarBienvenidaVista()

        fun onPistaEntendida(pista: Pista) {
            almacen.marcarPistaVista(pista)
            _pistasVistas.value = almacen.pistasVistas()
        }

        /** Vuelve a enseñar la bienvenida y las pistas (Ajustes > Ayuda). */
        fun onReiniciar() {
            almacen.reiniciar()
            _pistasVistas.value = almacen.pistasVistas()
        }
    }
