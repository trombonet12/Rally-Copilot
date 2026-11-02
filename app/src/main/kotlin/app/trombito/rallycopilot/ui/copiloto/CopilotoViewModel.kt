package app.trombito.rallycopilot.ui.copiloto

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.escucha.ControlOrdenes
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.ajustes.decirEjemplo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Estado del copiloto en la pantalla de conducción: el modo, la voz elegida (null = sin elegir) y si hay que
 * preguntarla.
 * Mientras los ajustes no se han leído ([cargado] falso) no se pregunta nada, para que el diálogo no parpadee.
 */
data class CopilotoUiState(
    val cargado: Boolean = false,
    val modo: ModoCopiloto = ModoCopiloto.POR_DEFECTO,
    val voz: VozCopiloto? = null,
    /** El usuario cerró el diálogo con atrás: no se vuelve a preguntar hasta la próxima apertura del mapa. */
    val vozPospuesta: Boolean = false,
    /** El copiloto está escuchando una orden de voz (el micrófono late). */
    val escuchando: Boolean = false,
    /** Ajustes rápidos del mapa: cuánto antes se canta la nota, notas simplificadas y volumen propio de la voz. */
    val anticipacion: NivelAnticipacion = NivelAnticipacion.NORMAL,
    val notasSimplificadas: Boolean = false,
    val volumenVoz: Int = AjustesConduccion.VOLUMEN_VOZ_NORMAL,
) {
    /** Hay que enseñar el diálogo «Elige la voz del copiloto». */
    val pedirVoz: Boolean get() = cargado && voz == null && !vozPospuesta
}

/** Presentación del selector de modo y del diálogo de voz del primer uso. */
@HiltViewModel
class CopilotoViewModel
    @Inject
    constructor(
        private val repositorio: RepositorioAjustesConduccion,
        private val locutor: Locutor,
        private val ordenes: ControlOrdenes,
    ) : ViewModel() {
        private val pospuesta = MutableStateFlow(false)

        val uiState: StateFlow<CopilotoUiState> =
            combine(repositorio.ajustes, pospuesta, ordenes.escuchando) { ajustes, aplazada, escuchando ->
                CopilotoUiState(
                    cargado = true,
                    modo = ajustes.modoCopiloto,
                    voz = ajustes.voz,
                    vozPospuesta = aplazada,
                    escuchando = escuchando,
                    anticipacion = ajustes.anticipacion,
                    notasSimplificadas = ajustes.notasSimplificadas,
                    volumenVoz = ajustes.volumenVoz,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), CopilotoUiState())

        fun onModo(modo: ModoCopiloto) {
            viewModelScope.launch { repositorio.elegirModoCopiloto(modo) }
        }

        fun onElegirVoz(voz: VozCopiloto) {
            viewModelScope.launch { repositorio.elegirVoz(voz) }
        }

        /** Atrás en el diálogo: se deja la voz sin elegir y se vuelve a preguntar al abrir el mapa otra vez. */
        fun onPosponerVoz() {
            pospuesta.value = true
        }

        /** El micrófono del panel: el copiloto escucha una orden (el permiso ya lo pidió la interfaz). */
        fun onEscuchar() {
            ordenes.escucharOrden()
        }

        /** Hace sonar la frase de muestra con [voz] sin cambiar la elegida. */
        fun onProbarVoz(voz: VozCopiloto) {
            locutor.probar(voz)
        }

        /** Anticipación de las notas desde la hoja de ajustes rápidos: vale desde la siguiente fijación. */
        fun onAnticipacion(nivel: NivelAnticipacion) {
            cambiar { it.copy(anticipacion = nivel) }
        }

        fun onNotasSimplificadas(simplificadas: Boolean) {
            cambiar { it.copy(notasSimplificadas = simplificadas) }
        }

        /** Volumen propio de la voz (50–150 %): se aplica al locutor al instante, también conduciendo. */
        fun onVolumenVoz(porcentaje: Int) {
            val volumen = porcentaje.coerceIn(AjustesConduccion.VOLUMEN_VOZ_MIN, AjustesConduccion.VOLUMEN_VOZ_MAX)
            locutor.usarVolumen(volumen)
            cambiar { it.copy(volumenVoz = volumen) }
        }

        /** «Escuchar ejemplo» de la hoja rápida, con la voz, el volumen y las simplificadas de los ajustes. */
        fun onEscucharEjemplo() {
            viewModelScope.launch { locutor.decirEjemplo(repositorio.ajustes.first()) }
        }

        private fun cambiar(cambio: (AjustesConduccion) -> AjustesConduccion) {
            viewModelScope.launch { repositorio.guardar(cambio(repositorio.ajustes.first())) }
        }

        private companion object {
            const val PARADA_MS = 5_000L
        }
    }
