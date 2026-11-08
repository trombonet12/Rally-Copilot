package app.trombito.rallycopilot.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.Tema
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.datos.RepositorioDatosMapa
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.VelocidadHabla
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.datos.Complemento
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.PARADA_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
        private val datos: RepositorioDatosMapa,
    ) : ViewModel() {
        val uiState: StateFlow<AjustesConduccion> =
            repositorio.ajustes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), AjustesConduccion())

        /** Sharvard no va en el APK: si está instalada, descargándose o por bajar (con su tamaño). */
        val vozSharvard: StateFlow<EstadoVozDescarga> =
            datos.estado
                .map(EstadoVozDescarga::de)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), EstadoVozDescarga())

        /** Qué hay descargado, para el detalle de Mapas offline en Datos; null mientras se siembran los del APK. */
        val mapas: StateFlow<ResumenMapas?> =
            datos.estado
                .map(ResumenMapas::de)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), null)

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

        fun onAnticipacion(nivel: NivelAnticipacion) {
            guardar { it.copy(anticipacion = nivel) }
        }

        fun onNotasSimplificadas(simplificadas: Boolean) {
            guardar { it.copy(notasSimplificadas = simplificadas) }
        }

        fun onActivarConCopiloto(activar: Boolean) {
            viewModelScope.launch { repositorio.elegirActivarConCopiloto(activar) }
        }

        /** «Audio por el casco»: automático (por el intercom si lo hay) o apagado (siempre por el móvil). */
        fun onAudioCasco(automatico: Boolean) {
            guardar { it.copy(audioCasco = automatico) }
        }

        /** «Escucha con casco»: «Copiloto» escucha siempre o solo al pulsar el micrófono. */
        fun onEscuchaCascoSiempre(siempre: Boolean) {
            guardar { it.copy(escuchaCascoSiempre = siempre) }
        }

        fun onModoCopiloto(modo: ModoCopiloto) {
            viewModelScope.launch { repositorio.elegirModoCopiloto(modo) }
        }

        /** Elegir Sharvard sin tenerla baja la voz (con la red que haya); hasta entonces habla Dave. */
        fun onVozCopiloto(voz: VozCopiloto) {
            viewModelScope.launch {
                repositorio.elegirVoz(voz)
                if (voz == VozCopiloto.SHARVARD) pedirSharvardSiFalta()
            }
        }

        /** «Descargar» de una voz: solo Sharvard se baja; Dave va en el APK. */
        fun onDescargarVoz(voz: VozCopiloto) {
            if (voz == VozCopiloto.SHARVARD) viewModelScope.launch { pedirSharvardSiFalta() }
        }

        private suspend fun pedirSharvardSiFalta() {
            val sharvard = datos.estado.first().complemento(Complemento.VOZ_SHARVARD)
            if (!sharvard.instalado && !sharvard.descargando) datos.descargar(Complemento.VOZ_SHARVARD)
        }

        /** Dice la frase de muestra con [voz] sin cambiar la elegida y con el volumen elegido. */
        fun onProbarVoz(voz: VozCopiloto) {
            viewModelScope.launch {
                val ajustes = repositorio.ajustes.first()
                locutor.usarVolumen(ajustes.volumenVoz)
                locutor.usarVelocidad(ajustes.velocidadHabla)
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

        /** Indicaciones que dice y enseña el copiloto: cruces, puentes, salidas y entradas de autopista y rotondas. */
        fun onAvisarCruces(avisar: Boolean) {
            guardar { it.copy(avisarCruces = avisar) }
        }

        fun onAvisarPuentes(avisar: Boolean) {
            guardar { it.copy(avisarPuentes = avisar) }
        }

        fun onAvisarSalidasAutopista(avisar: Boolean) {
            guardar { it.copy(avisarSalidasAutopista = avisar) }
        }

        fun onAvisarRotondas(avisar: Boolean) {
            guardar { it.copy(avisarRotondas = avisar) }
        }

        /** Familias de avisos que se dicen por voz: radares, incidencias y velocidad (exceso y media del tramo). */
        fun onAvisarRadares(avisar: Boolean) {
            guardar { it.copy(avisarRadares = avisar) }
        }

        fun onAvisarIncidencias(avisar: Boolean) {
            guardar { it.copy(avisarIncidencias = avisar) }
        }

        fun onAvisarVelocidad(avisar: Boolean) {
            guardar { it.copy(avisarVelocidad = avisar) }
        }

        /** Velocidad de habla: se guarda, se aplica al locutor al instante y suena la frase de ejemplo con ella. */
        fun onVelocidadHabla(velocidad: VelocidadHabla) {
            locutor.usarVelocidad(velocidad)
            viewModelScope.launch {
                val nuevos = repositorio.ajustes.first().copy(velocidadHabla = velocidad)
                repositorio.guardar(nuevos)
                locutor.decirEjemplo(nuevos)
            }
        }

        fun onCapa(capa: CapaMapa) {
            guardar { it.copy(capa = capa) }
        }

        /** «Borrar» la casa guardada desde el mapa (Ajustes > Mapa). */
        fun onBorrarCasa() {
            viewModelScope.launch { repositorio.fijarCasa(null) }
        }

        fun onRelieve(relieve: Boolean) {
            guardar { it.copy(relieve = relieve) }
        }

        private fun guardar(cambio: (AjustesConduccion) -> AjustesConduccion) {
            viewModelScope.launch { repositorio.guardar(cambio(repositorio.ajustes.first())) }
        }
    }
