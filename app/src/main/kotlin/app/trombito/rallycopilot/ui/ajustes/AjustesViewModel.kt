package app.trombito.rallycopilot.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.EstiloManiobras
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.Tema
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Ajustes de conducción: zoom del mapa, icono de posición, orientación, estilo de las maniobras, anticipación de las
 * notas, notas simplificadas, modo, voz y volumen del copiloto, tema, capa y relieve, persistidos en el
 * repositorio; la voz se puede probar y se puede oír la nota de ejemplo.
 */
@HiltViewModel
@Suppress("TooManyFunctions") // un método por ajuste de la pantalla
class AjustesViewModel
    @Inject
    constructor(
        private val repositorio: RepositorioAjustesConduccion,
        private val locutor: Locutor,
    ) : ViewModel() {
        val uiState: StateFlow<AjustesConduccion> =
            repositorio.ajustes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), AjustesConduccion())

        /** El nivel se acota a -2..2 (más lejos a más cerca). */
        fun onNivelZoom(nivel: Int) {
            guardar { it.copy(nivelZoom = nivel.coerceIn(AjustesConduccion.NIVEL_MIN, AjustesConduccion.NIVEL_MAX)) }
        }

        fun onIcono(icono: IconoPosicion) {
            guardar { it.copy(icono = icono) }
        }

        fun onOrientacion(orientacion: Orientacion) {
            guardar { it.copy(orientacion = orientacion) }
        }

        fun onEstiloManiobras(estilo: EstiloManiobras) {
            guardar { it.copy(estiloManiobras = estilo) }
        }

        fun onAnticipacion(nivel: NivelAnticipacion) {
            guardar { it.copy(anticipacion = nivel) }
        }

        fun onNotasSimplificadas(simplificadas: Boolean) {
            guardar { it.copy(notasSimplificadas = simplificadas) }
        }

        fun onActivarConCopiloto(activar: Boolean) {
            viewModelScope.launch { repositorio.elegirActivarConCopiloto(activar) }
        }

        fun onModoCopiloto(modo: ModoCopiloto) {
            viewModelScope.launch { repositorio.elegirModoCopiloto(modo) }
        }

        fun onVozCopiloto(voz: VozCopiloto) {
            viewModelScope.launch { repositorio.elegirVoz(voz) }
        }

        /** Dice la frase de muestra con [voz] sin cambiar la elegida y con el volumen elegido. */
        fun onProbarVoz(voz: VozCopiloto) {
            viewModelScope.launch {
                locutor.usarVolumen(repositorio.ajustes.first().volumenVoz)
                locutor.probar(voz)
            }
        }

        /** Volumen propio del copiloto (50–150 %): se guarda y se aplica al locutor al instante. */
        fun onVolumenVoz(porcentaje: Int) {
            val volumen = porcentaje.coerceIn(AjustesConduccion.VOLUMEN_VOZ_MIN, AjustesConduccion.VOLUMEN_VOZ_MAX)
            locutor.usarVolumen(volumen)
            guardar { it.copy(volumenVoz = volumen) }
        }

        /** «Escuchar ejemplo»: la nota de ejemplo con la voz, el volumen y el modo simplificado de los ajustes. */
        fun onEscucharEjemplo() {
            viewModelScope.launch { locutor.decirEjemplo(repositorio.ajustes.first()) }
        }

        fun onTema(tema: Tema) {
            guardar { it.copy(tema = tema) }
        }

        /** «Cronometrar trayectos»: vale desde la próxima conducción. */
        fun onCronometrar(cronometrar: Boolean) {
            guardar { it.copy(cronometrar = cronometrar) }
        }

        /** «Salida manual» del cronómetro: vale desde el siguiente «LISTO», también con una conducción en curso. */
        fun onSalidaManual(manual: Boolean) {
            guardar { it.copy(salidaManual = manual) }
        }

        fun onNotasGrandes(grandes: Boolean) {
            guardar { it.copy(notasGrandes = grandes) }
        }

        fun onCapa(capa: CapaMapa) {
            guardar { it.copy(capa = capa) }
        }

        fun onRelieve(relieve: Boolean) {
            guardar { it.copy(relieve = relieve) }
        }

        private fun guardar(cambio: (AjustesConduccion) -> AjustesConduccion) {
            viewModelScope.launch { repositorio.guardar(cambio(repositorio.ajustes.first())) }
        }

        private companion object {
            const val PARADA_MS = 5_000L
        }
    }
