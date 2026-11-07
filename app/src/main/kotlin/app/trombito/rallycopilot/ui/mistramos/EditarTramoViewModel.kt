package app.trombito.rallycopilot.ui.mistramos

import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.datos.RepositorioDatosMapa
import app.trombito.rallycopilot.domain.edicion.EditorTramo
import app.trombito.rallycopilot.domain.edicion.TramoEditable
import app.trombito.rallycopilot.motor.edicion.AccionEdicion
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.tramos.CortesTramo
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.ui.RutaEditarTramo
import app.trombito.rallycopilot.ui.comun.PARADA_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val ETIQUETA = "EditarTramo"

/** La nota que se edita en el detalle: una existente (con su posición) o una nueva en [posicionM]. */
@Immutable
data class EditorDeTramo(
    val posicionM: Double,
    val nota: Nota?,
)

@Immutable
data class EditarTramoUiState(
    val cargando: Boolean = true,
    val encontrado: Boolean = true,
    val datos: TramoEditable? = null,
    val sectoresM: List<Double> = emptyList(),
    val microsectoresPorKm: Int = 0,
    val microsectoresM: List<Double> = emptyList(),
    val editor: EditorDeTramo? = null,
    val simplificadas: Boolean = false,
    /** Estilo del mapa offline; null = aún no se ha leído o no hay mapa instalado. */
    val estiloMapaJson: String? = null,
    /** Inicio (m) de la nota elegida en el mapa o en la lista; null = ninguna. */
    val seleccionM: Double? = null,
    /** Sube cada vez que una elección pide centrar el mapa en la nota (la de la lista, no la del mapa). */
    val centrarVersion: Int = 0,
    /** La elección actual pide centrar el mapa (viene de la lista); la del mapa no mueve la cámara. */
    val centrarSeleccion: Boolean = false,
)

/** La nota elegida y cuántas veces se ha pedido centrarla. */
private data class Seleccion(
    val posicionM: Double? = null,
    val centrarVersion: Int = 0,
    val centrar: Boolean = false,
)

/** Lo que el ViewModel lleva aparte del tramo guardado: cortes en vuelo, estilo del mapa y nota elegida. */
private class Locales(
    val sectores: List<Double>?,
    val microsectores: Int?,
    val estilo: String?,
    val seleccion: Seleccion,
)

/**
 * Edición de las notas y los sectores de un tramo guardado, sobre su traza. Tocar una nota la corrige, tocar un punto
 * vacío del trazo añade una, y los cortes de sector se arrastran (se guardan al soltar). Las ediciones se guardan
 * ligadas al tramo y siempre prevalecen sobre las notas automáticas.
 */
@HiltViewModel
@Suppress("TooManyFunctions") // un callback por gesto del lienzo (nota, punto, corte, microsectores) y del editor
class EditarTramoViewModel
    @Inject
    constructor(
        guardado: SavedStateHandle,
        private val editor: EditorTramo,
        ajustes: RepositorioAjustesConduccion,
        datosMapa: RepositorioDatosMapa,
    ) : ViewModel() {
        private val tramoId = guardado.toRoute<RutaEditarTramo>().tramoId
        private val editando = MutableStateFlow<EditorDeTramo?>(null)

        /** Cortes mientras se arrastra o recién guardados; null = los del tramo cargado. */
        private val sectoresLocales = MutableStateFlow<List<Double>?>(null)
        private val microsectoresLocales = MutableStateFlow<Int?>(null)
        private val estiloMapa = MutableStateFlow<String?>(null)
        private val seleccion = MutableStateFlow(Seleccion())

        private val tramoEditable = editor.observar(tramoId)

        private val locales =
            combine(sectoresLocales, microsectoresLocales, estiloMapa, seleccion, ::Locales)

        val uiState: StateFlow<EditarTramoUiState> =
            combine(tramoEditable, editando, locales, ajustes.ajustes) { datos, abierto, local, preferencias ->
                EditarTramoUiState(
                    cargando = false,
                    encontrado = datos != null,
                    datos = datos,
                    sectoresM = local.sectores ?: datos?.tramo?.sectores.orEmpty(),
                    microsectoresPorKm =
                        local.microsectores
                            ?: datos?.let { CortesTramo.porKmDe(it.tramo.longitudM, it.tramo.microsectores) }
                            ?: 0,
                    microsectoresM =
                        local.microsectores?.let { CortesTramo.microsectoresPorKm(datos?.tramo?.longitudM ?: 0.0, it) }
                            ?: datos?.tramo?.microsectores.orEmpty(),
                    editor = abierto,
                    simplificadas = preferencias.notasSimplificadas,
                    estiloMapaJson = local.estilo,
                    seleccionM = local.seleccion.posicionM,
                    centrarVersion = local.seleccion.centrarVersion,
                    centrarSeleccion = local.seleccion.centrar,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), EditarTramoUiState())

        init {
            viewModelScope.launch { estiloMapa.value = datosMapa.estilos()?.vectorial }
        }

        /** Elige [nota] (resaltada en el mapa y en la lista); con [centrar] el mapa se va a ella. */
        fun onSeleccionar(
            nota: Nota,
            centrar: Boolean,
        ) {
            seleccion.update {
                Seleccion(nota.posicionM, if (centrar) it.centrarVersion + 1 else it.centrarVersion, centrar)
            }
        }

        fun onTocarNota(nota: Nota) {
            editando.value = EditorDeTramo(nota.posicionM, nota)
        }

        fun onTocarPunto(metros: Double) {
            editando.value = EditorDeTramo(metros, null)
        }

        fun onCerrarEditor() {
            editando.value = null
        }

        fun onAplicar(nota: Nota) {
            val abierto = editando.value ?: return
            val accion = if (abierto.nota == null) AccionEdicion.Anadir(nota) else AccionEdicion.Sustituir(nota)
            aplicar(abierto.posicionM, accion)
        }

        fun onQuitar() {
            val abierto = editando.value ?: return
            aplicar(abierto.posicionM, AccionEdicion.Quitar)
        }

        fun onRestaurar() {
            viewModelScope.launch { editor.restaurar(tramoId) }
        }

        /** Arrastra el corte [indice] a [metros] (sin pasar de los vecinos); no se guarda hasta [onSoltarCorte]. */
        fun onMoverCorte(
            indice: Int,
            metros: Double,
        ) {
            val tramo = uiState.value.datos?.tramo ?: return
            sectoresLocales.update { actual ->
                CortesTramo.mover(actual ?: tramo.sectores, indice, metros, tramo.metaM)
            }
        }

        fun onSoltarCorte() {
            val tramo = uiState.value.datos?.tramo ?: return
            val sectores = sectoresLocales.value ?: return
            guardarCortes(sectores, microsectoresActuales(tramo))
        }

        /** [porKm] microsectores por kilómetro, de 0 a 4. */
        fun onMicrosectores(porKm: Int) {
            val tramo = uiState.value.datos?.tramo ?: return
            val valor = porKm.coerceIn(0, CortesTramo.MICROSECTORES_POR_KM_MAX)
            microsectoresLocales.value = valor
            guardarCortes(
                sectoresLocales.value ?: tramo.sectores,
                CortesTramo.microsectoresPorKm(tramo.longitudM, valor),
            )
        }

        private fun microsectoresActuales(tramo: Tramo): List<Double> =
            microsectoresLocales.value?.let { CortesTramo.microsectoresPorKm(tramo.longitudM, it) }
                ?: tramo.microsectores

        private fun aplicar(
            posicionM: Double,
            accion: AccionEdicion,
        ) {
            editando.value = null
            viewModelScope.launch { editor.editar(tramoId, posicionM, accion) }
        }

        private fun volverALosGuardados() {
            sectoresLocales.value = null
            microsectoresLocales.value = null
        }

        private fun guardarCortes(
            sectores: List<Double>,
            microsectores: List<Double>,
        ) {
            viewModelScope.launch {
                try {
                    editor.guardarCortes(tramoId, sectores, microsectores)
                } catch (e: SQLiteException) {
                    Log.w(ETIQUETA, "No se pudieron guardar los cortes: ${e.javaClass.simpleName}")
                    volverALosGuardados()
                } catch (e: IllegalArgumentException) {
                    // Cortes que el tramo no admite: se vuelve a los guardados.
                    Log.w(ETIQUETA, "Cortes no válidos para el tramo: ${e.javaClass.simpleName}")
                    volverALosGuardados()
                }
            }
        }
    }
