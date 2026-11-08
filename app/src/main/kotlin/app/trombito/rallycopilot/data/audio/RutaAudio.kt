package app.trombito.rallycopilot.data.audio

import android.media.AudioDeviceInfo
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Qué clase de dispositivo de audio es (solo importan los de tipo casco). */
enum class TipoDispositivo {
    /** Manos libres (HFP) por SCO: el intercom del casco (Cardo Packtalk, Freecom, Spirit). */
    CASCO_SCO,

    /** Auricular Bluetooth LE (LE Audio), API 31+. */
    CASCO_BLE,

    /** Audio Bluetooth A2DP: sin micrófono por sí solo (solo cuenta en Android 11 o anterior, ver [SistemaAudio]). */
    A2DP,

    /** Cualquier otro (altavoz, auricular con cable...). */
    OTRO,
}

/** Un dispositivo de audio del sistema; [id] es el `AudioDeviceInfo.getId()`. */
data class DispositivoAudio(
    val id: Int,
    val tipo: TipoDispositivo,
)

/** Por dónde sale la voz y entra el micrófono. */
enum class ModoAudio { ALTAVOZ, CASCO }

/** Para qué se pide la ruta: hablar o escuchar. */
enum class UsoRuta { SALIDA, ENTRADA }

/**
 * Un uso de la ruta de audio (una frase, un tic, una escucha). Cerrarlo avisa de que ya no hace falta: con el casco, el
 * canal de comunicación se mantiene unos segundos más por si llega otro uso y luego se cierra ([RutaAudioCasco]).
 */
interface ReservaAudio : AutoCloseable {
    /** El casco por el que va este uso, o `null` si va por el altavoz y el micrófono del móvil. */
    val dispositivo: DispositivoAudio?

    /** `true` si el casco se ha caído (o cambiado) desde que se reservó: lo que suene o se grabe ya no va por él. */
    val perdida: Boolean

    /** Milisegundos que faltan para que el canal del casco esté establecido (0 si ya lo está o es el altavoz). */
    fun msHastaListo(): Long

    /** Espera a que se pierda el casco; con el altavoz no vuelve nunca (se cancela). */
    suspend fun esperarCaida()
}

/** Uso por el altavoz y el micrófono del móvil: no hay nada que abrir ni que perder. */
object ReservaAltavoz : ReservaAudio {
    override val dispositivo: DispositivoAudio? = null
    override val perdida: Boolean = false

    override fun msHastaListo(): Long = 0L

    override suspend fun esperarCaida() = awaitCancellation()

    override fun close() = Unit
}

/** Enruta la voz del copiloto y el micrófono al intercom del casco cuando lo hay, y al móvil cuando no. */
interface RutaAudio {
    /** [ModoAudio.CASCO] mientras haya un intercom Bluetooth utilizable (conectado y con permiso). */
    val modo: StateFlow<ModoAudio>

    /** Pide la ruta para un uso (sin bloquear: el canal del casco tarda un poco, ver [ReservaAudio.msHastaListo]). */
    fun reservar(uso: UsoRuta): ReservaAudio

    /** El dispositivo de salida de [reserva], para `AudioTrack.setPreferredDevice` (`null` si va por el móvil). */
    fun infoSalida(reserva: ReservaAudio): AudioDeviceInfo?

    /** El micrófono de [reserva], para `AudioRecord.setPreferredDevice` (`null` si va por el móvil). */
    fun infoEntrada(reserva: ReservaAudio): AudioDeviceInfo?
}

/** Sin intercom nunca: todo por el móvil (valor por defecto y doble para pruebas). */
object RutaSoloAltavoz : RutaAudio {
    override val modo: StateFlow<ModoAudio> = MutableStateFlow(ModoAudio.ALTAVOZ)

    override fun reservar(uso: UsoRuta): ReservaAudio = ReservaAltavoz

    override fun infoSalida(reserva: ReservaAudio): AudioDeviceInfo? = null

    override fun infoEntrada(reserva: ReservaAudio): AudioDeviceInfo? = null
}
