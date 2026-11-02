package app.trombito.rallycopilot.ui.mistramos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * «Mis tramos»: la lista por último uso con su filtro. Borrar es diferido: la fila se oculta al momento y el tramo
 * se borra de verdad al terminar el aviso de deshacer (o al borrar otro, o al salir de la pantalla).
 */
@HiltViewModel
class MisTramosViewModel
    @Inject
    constructor(
        private val repositorio: RepositorioTramos,
    ) : ViewModel() {
        private val filtro = MutableStateFlow(FiltroTramos.TODOS)
        private val ocultos = MutableStateFlow<Set<String>>(emptySet())
        private val renombrando = MutableStateFlow<String?>(null)
        private val pendiente = MutableStateFlow<BorradoPendiente?>(null)

        @OptIn(ExperimentalCoroutinesApi::class)
        private val lista =
            filtro.flatMapLatest { f -> repositorio.observarResumenes(f).map { f to it } }

        val uiState: StateFlow<MisTramosUiState> =
            combine(lista, ocultos, renombrando, pendiente) { (f, tramos), ocultosAhora, idRenombrando, borrado ->
                MisTramosUiState(
                    cargando = false,
                    filtro = f,
                    tramos = tramos.filter { it.id !in ocultosAhora },
                    renombrando = tramos.firstOrNull { it.id == idRenombrando },
                    borradoPendiente = borrado,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), MisTramosUiState())

        fun onFiltro(nuevo: FiltroTramos) {
            filtro.value = nuevo
        }

        fun onFavorito(tramo: ResumenTramo) {
            viewModelScope.launch { repositorio.marcarFavorito(tramo.id, !tramo.favorito) }
        }

        fun onBorrar(tramo: ResumenTramo) {
            pendiente.value?.let { borrarDeVerdad(it.id) }
            ocultos.update { it + tramo.id }
            pendiente.value = BorradoPendiente(tramo.id, tramo.nombre)
        }

        /** Vuelve a mostrar el tramo borrado más reciente: no llegó a borrarse. */
        fun onDeshacer() {
            val borrado = pendiente.value ?: return
            pendiente.value = null
            ocultos.update { it - borrado.id }
        }

        /** El aviso de deshacer terminó sin que se pulsara: el borrado pasa a ser real. */
        fun onBorradoConfirmado() {
            val borrado = pendiente.value ?: return
            pendiente.value = null
            borrarDeVerdad(borrado.id)
        }

        /** Al salir de la pantalla con un aviso abierto no se pierde el borrado. */
        fun onSalir() = onBorradoConfirmado()

        fun onPedirRenombrar(tramo: ResumenTramo) {
            renombrando.value = tramo.id
        }

        fun onCancelarRenombrar() {
            renombrando.value = null
        }

        /** [nombre] en blanco devuelve el nombre automático. */
        fun onRenombrar(nombre: String) {
            val id = renombrando.value ?: return
            renombrando.value = null
            viewModelScope.launch { repositorio.renombrar(id, nombre.trim().ifEmpty { null }) }
        }

        // Sin cancelación: el borrado que se confirma al cerrar la pantalla no debe perderse con el ViewModel.
        private fun borrarDeVerdad(id: String) {
            viewModelScope.launch(NonCancellable) { repositorio.borrar(id) }
        }

        private companion object {
            const val PARADA_MS = 5_000L
        }
    }
