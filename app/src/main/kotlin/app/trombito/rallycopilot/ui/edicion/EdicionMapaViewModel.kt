package app.trombito.rallycopilot.ui.edicion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.edicion.EdicionNotasEnVivo
import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.domain.notas.ProveedorNotas
import app.trombito.rallycopilot.motor.edicion.AccionEdicion
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.velocidad.msAKmh
import app.trombito.rallycopilot.ui.comun.PARADA_MS
import app.trombito.rallycopilot.ui.mapa.MarcadorNota
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/** Por debajo de esta velocidad (km/h) el coche se considera parado y se pueden editar las notas del mapa. */
const val VELOCIDAD_PARADO_KMH = 3.0

/** Metros de la hoja por detrás y por delante de la posición cuyas notas se pintan en el mapa. */
private const val ATRAS_M = 300.0
private const val ADELANTE_M = 3_000.0
private const val TOLERANCIA_POSICION_M = 1.0

/** Callbacks del editor de notas del mapa. */
data class AccionesEdicionMapa(
    val onAplicar: (Nota) -> Unit = {},
    val onQuitar: () -> Unit = {},
    val onCerrar: () -> Unit = {},
)

/** La nota que se está editando en el mapa: su posición en la hoja y la nota tal cual está ahora. */
data class EditorEnMapa(
    val posicionM: Double,
    val nota: Nota,
    val simplificadas: Boolean,
)

data class EdicionMapaUiState(
    val marcadores: List<MarcadorNota> = emptyList(),
    val editor: EditorEnMapa? = null,
)

/** El coche está parado: la velocidad del GPS es conocida y menor de [VELOCIDAD_PARADO_KMH]. */
fun estaParado(notas: NotasEnCurso?): Boolean = notas?.vMs?.let { it.msAKmh() < VELOCIDAD_PARADO_KMH } == true

/**
 * Las notas de la hoja cercanas a la posición, listas para pintar y tocar; vacío si el coche no está parado
 * (conduciendo
 * las notas no se tocan, solo se dictan) o si la hoja no sabe dónde cae cada posición.
 */
fun marcadoresDeNotas(notas: NotasEnCurso?): List<MarcadorNota> {
    val puntoEn = notas?.puntoEn
    if (notas == null || puntoEn == null || !estaParado(notas)) return emptyList()
    return notas.hoja.notas
        .filter { it.posicionM in (notas.posicionM - ATRAS_M)..(notas.posicionM + ADELANTE_M) }
        .map {
            val punto = puntoEn(it.posicionM)
            MarcadorNota(it.posicionM, punto.lat, punto.lon, it.cuidado)
        }
}

/**
 * Edición de notas desde el mapa. Solo con el coche parado se pintan las notas y se puede tocar una; el editor se
 * aplica
 * a la hoja vigente y se guarda ligado al tramo activo (o pendiente de anclar). Conduciendo, las notas solo se editan
 * por voz.
 */
@HiltViewModel
class EdicionMapaViewModel
    @Inject
    constructor(
        private val proveedor: ProveedorNotas,
        ajustes: RepositorioAjustesConduccion,
        private val edicion: EdicionNotasEnVivo,
    ) : ViewModel() {
        private val seleccion = MutableStateFlow<Double?>(null)

        val uiState: StateFlow<EdicionMapaUiState> =
            combine(proveedor.notas, ajustes.ajustes, seleccion) { notas, preferencias, elegida ->
                val nota =
                    elegida?.let { p ->
                        notas?.hoja?.notas?.firstOrNull {
                            abs(it.posicionM - p) <
                                TOLERANCIA_POSICION_M
                        }
                    }
                EdicionMapaUiState(
                    marcadores = marcadoresDeNotas(notas),
                    editor = nota?.let { EditorEnMapa(it.posicionM, it, preferencias.notasSimplificadas) },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), EdicionMapaUiState())

        fun onTocarNota(posicionM: Double) {
            if (estaParado(proveedor.notas.value)) seleccion.value = posicionM
        }

        fun onAplicar(nota: Nota) = aplicar(AccionEdicion.Sustituir(nota))

        fun onQuitar() = aplicar(AccionEdicion.Quitar)

        fun onCerrar() {
            seleccion.value = null
        }

        private fun aplicar(accion: AccionEdicion) {
            val posicionM = seleccion.value ?: return
            seleccion.value = null
            viewModelScope.launch { edicion.editar(posicionM, accion) }
        }
    }
