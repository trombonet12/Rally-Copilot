package app.trombito.rallycopilot.data.audio

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * El [AudioManager] visto por [RutaAudioCasco], para probar la selección de ruta con dobles. Todas las funciones
 * son seguras en cualquier API (las que no existen en la versión devuelven vacío o `false`).
 */
interface SistemaAudio {
    /** Dispositivos de comunicación disponibles (`getAvailableCommunicationDevices`, API 31+); vacío antes. */
    fun dispositivosComunicacion(): List<DispositivoAudio>

    /** Salidas de audio conectadas (`getDevices(GET_DEVICES_OUTPUTS)`): la detección en Android 11 o anterior. */
    fun salidasConectadas(): List<DispositivoAudio>

    /** `isBluetoothScoAvailableOffCall`: el móvil admite SCO sin llamada (Android 11 o anterior). */
    fun scoDisponible(): Boolean

    /** `setCommunicationDevice` (API 31+): abre el canal de comunicación por [dispositivo]; `false` si no puede. */
    fun fijarComunicacion(dispositivo: DispositivoAudio): Boolean

    /** `clearCommunicationDevice` (API 31+): cierra el canal y la música A2DP vuelve a su calidad. */
    fun liberarComunicacion()

    /** `startBluetoothSco` o `stopBluetoothSco` (deprecados, solo antes de la API 31). */
    fun fijarSco(activo: Boolean)

    /** `MODE_IN_COMMUNICATION` mientras el canal está abierto; al desactivar se restaura el modo anterior. */
    fun fijarModoComunicacion(activo: Boolean)

    /** Avisa (en el hilo principal) de altas y bajas de dispositivos; cerrar el resultado deja de avisar. */
    fun escucharCambios(alCambiar: () -> Unit): AutoCloseable

    fun infoSalida(id: Int): AudioDeviceInfo?

    /** El micrófono que corresponde a la salida [id] (mismo tipo y dirección Bluetooth). */
    fun infoEntrada(id: Int): AudioDeviceInfo?
}

/** [SistemaAudio] sobre el [AudioManager] real. */
@Suppress("TooManyFunctions") // una función por operación del AudioManager
class SistemaAudioAndroid(
    private val audio: AudioManager,
) : SistemaAudio {
    private var modoAnterior: Int? = null

    override fun dispositivosComunicacion(): List<DispositivoAudio> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audio.availableCommunicationDevices.map(::convertir)
        } else {
            emptyList()
        }

    override fun salidasConectadas(): List<DispositivoAudio> =
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map(::convertir)

    @Suppress("DEPRECATION")
    override fun scoDisponible(): Boolean = audio.isBluetoothScoAvailableOffCall

    override fun fijarComunicacion(dispositivo: DispositivoAudio): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val info = audio.availableCommunicationDevices.firstOrNull { it.id == dispositivo.id }
        return info != null && audio.setCommunicationDevice(info)
    }

    override fun liberarComunicacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio.clearCommunicationDevice()
    }

    @Suppress("DEPRECATION")
    override fun fijarSco(activo: Boolean) = if (activo) audio.startBluetoothSco() else audio.stopBluetoothSco()

    @Synchronized
    override fun fijarModoComunicacion(activo: Boolean) {
        if (activo) {
            if (modoAnterior == null) modoAnterior = audio.mode
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
        } else {
            modoAnterior?.let { audio.mode = it }
            modoAnterior = null
        }
    }

    override fun escucharCambios(alCambiar: () -> Unit): AutoCloseable {
        val aviso =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = alCambiar()

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = alCambiar()
            }
        audio.registerAudioDeviceCallback(aviso, Handler(Looper.getMainLooper()))
        return AutoCloseable { audio.unregisterAudioDeviceCallback(aviso) }
    }

    override fun infoSalida(id: Int): AudioDeviceInfo? =
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id }

    override fun infoEntrada(id: Int): AudioDeviceInfo? {
        val salida = infoSalida(id) ?: return null
        return audio
            .getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == salida.type && direccion(it) == direccion(salida) }
    }

    /** `getAddress` es de la API 28: antes se empareja solo por tipo (un único intercom a la vez). */
    private fun direccion(info: AudioDeviceInfo): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.address else null

    private fun convertir(info: AudioDeviceInfo): DispositivoAudio = DispositivoAudio(info.id, tipoDe(info.type))

    private companion object {
        /** `AudioDeviceInfo.TYPE_BLE_HEADSET`: la constante es de la API 31 y se usa solo con la lista de la API 31. */
        const val TIPO_BLE_HEADSET = 26

        fun tipoDe(tipo: Int): TipoDispositivo =
            when (tipo) {
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> TipoDispositivo.CASCO_SCO
                TIPO_BLE_HEADSET -> TipoDispositivo.CASCO_BLE
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> TipoDispositivo.A2DP
                else -> TipoDispositivo.OTRO
            }
    }
}
