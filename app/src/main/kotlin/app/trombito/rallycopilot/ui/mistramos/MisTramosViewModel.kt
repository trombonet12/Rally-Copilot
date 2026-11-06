package app.trombito.rallycopilot.ui.mistramos

import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * «Mis tramos»: la lista por último uso con su filtro. Borrar es diferido: la fila se oculta al momento y el tramo
 * se borra de verdad al terminar el aviso de deshacer (o al borrar otro, o al salir de la pantalla).
 */
@HiltViewModel
@Suppress("TooManyFunctions") // un callback por acción de la lista
class MisTramosViewModel
    @Inject
    constructor(
        private val repositorio: RepositorioTramos,
    ) : ViewModel() {
        private val filtro = MutableStateFlow(FiltroTramos.TODOS)
        private val ocultos = MutableStateFlow<Set<String>>(emptySet())
        private val renombrando = MutableStateFlow<String?>(null)
        private val pendiente = MutableStateFlow<BorradoPendiente?>(null)
        private val fallo = MutableStateFlow<String?>(null)
        private val trazas = MutableStateFlow<Map<String, List<PuntoGeo>>>(emptyMap())
        private val trazasPedidas = mutableSetOf<String>()

        @OptIn(ExperimentalCoroutinesApi::class)
        private val lista =
            filtro.flatMapLatest { f ->
                repositorio.observarResumenes(f).map { f to it }.onEach { (_, tramos) -> cargarTrazas(tramos) }
            }

        private val base =
            combine(
                lista,
                ocultos,
                renombrando,
                pendiente,
                fallo,
            ) { (f, tramos), ocultosAhora, id, borrado, error ->
                MisTramosUiState(
                    cargando = false,
                    filtro = f,
                    tramos = tramos.filter { it.id !in ocultosAhora },
                    renombrando = tramos.firstOrNull { it.id == id },
                    borradoPendiente = borrado,
                    falloBorrado = error,
                )
            }

        val uiState: StateFlow<MisTramosUiState> =
            combine(base, trazas) { estado, siluetas -> estado.copy(trazas = siluetas) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), MisTramosUiState())

        /** Lee, una sola vez por tramo, la silueta para la miniatura (aligerada: solo hace falta la forma). */
        private fun cargarTrazas(tramos: List<ResumenTramo>) {
            val nuevos = tramos.map { it.id }.filter { trazasPedidas.add(it) }
            if (nuevos.isEmpty()) return
            viewModelScope.launch {
                nuevos.forEach { id ->
                    val traza = repositorio.tramo(id)?.traza?.let { aligerar(it, PUNTOS_MINIATURA) } ?: return@forEach
                    trazas.update { it + (id to traza) }
                }
            }
        }

        fun onFiltro(nuevo: FiltroTramos) {
            filtro.value = nuevo
        }

        fun onFavorito(tramo: ResumenTramo) {
            viewModelScope.launch { repositorio.marcarFavorito(tramo.id, !tramo.favorito) }
        }

        fun onBorrar(tramo: ResumenTramo) {
            pendiente.value?.let { borrarDeVerdad(it) }
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
            borrarDeVerdad(borrado)
        }

        /** El aviso del borrado fallido ya se mostró. */
        fun onFalloVisto() {
            fallo.value = null
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

        // Sin cancelación: el borrado que se confirma al cerrar la pantalla no debe perderse con el ViewModel. Si la
        // base falla, el tramo sigue guardado: vuelve a la lista y se avisa, en lugar de cerrar la app.
        private fun borrarDeVerdad(borrado: BorradoPendiente) {
            viewModelScope.launch(NonCancellable) {
                val error =
                    try {
                        repositorio.borrar(borrado.id)
                        null
                    } catch (e: SQLiteException) {
                        e
                    } catch (e: IllegalStateException) {
                        e
                    }
                if (error != null) {
                    // Solo el tipo de la excepción al registro: nada de datos del tramo.
                    Log.w(ETIQUETA_LOG, "No se pudo borrar un tramo: ${error.javaClass.simpleName}")
                    ocultos.update { it - borrado.id }
                    fallo.value = borrado.nombre
                }
            }
        }

        private companion object {
            const val PARADA_MS = 5_000L
            const val PUNTOS_MINIATURA = 80
            const val ETIQUETA_LOG = "MisTramos"
        }
    }
