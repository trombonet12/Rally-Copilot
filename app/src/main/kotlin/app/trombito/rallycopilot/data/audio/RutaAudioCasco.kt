package app.trombito.rallycopilot.data.audio

import android.media.AudioDeviceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * [RutaAudio] con intercom de casco.
 *
 * - **Detección**: con la API 31 o más, un dispositivo de comunicación SCO o BLE headset
 *   ([SistemaAudio.dispositivosComunicacion]); antes, una salida SCO conectada (o un A2DP si el móvil admite SCO:
 *   en Android 11 o anterior el SCO solo se lista con el canal abierto). Sin `BLUETOOTH_CONNECT` (API 31+) no hay
 *   casco: todo por el móvil. [modo] se actualiza en caliente con las altas y bajas de dispositivos.
 * - **Canal**: `setCommunicationDevice` (API 31+) o `startBluetoothSco` (antes), con `MODE_IN_COMMUNICATION` mientras
 *   está abierto. Compromiso: abrir SCO pasa la música A2DP del intercom a calidad de llamada (o la corta) mientras
 *   dura; por eso solo se abre al hablar o escuchar y se cierra [LINGER_MS] después del último uso (frases seguidas no
 *   lo reabren: abrirlo cuesta [ESPERA_SCO_MS] antes de que suene bien). Con el micrófono continuo («Copiloto») el
 *   canal queda abierto mientras dure.
 * - **Caída**: si el casco desaparece (o cambia), se corta el canal al instante y las reservas vivas pasan a
 *   [ReservaAudio.perdida]: quien suena o graba reintenta por el móvil.
 */
@Suppress("TooManyFunctions") // detección, canal y reservas comparten un solo estado protegido
class RutaAudioCasco(
    private val sistema: SistemaAudio,
    private val sdk: Int,
    /** `BLUETOOTH_CONNECT` concedido (o no necesario en esta API). Se consulta en cada reserva. */
    private val permiso: () -> Boolean,
    private val ambito: CoroutineScope,
    private val reloj: () -> Long,
    /** Ajuste «Audio por el casco»: `true` = automático (usa el casco si lo hay), `false` = siempre por el móvil. */
    private val activado: () -> Boolean = { true },
) : RutaAudio {
    private val estado = MutableStateFlow(ModoAudio.ALTAVOZ)
    override val modo: StateFlow<ModoAudio> = estado.asStateFlow()

    private val lock = Any()

    /** Sube cada vez que se corta el canal por caída o cambio de casco; las reservas comparan con la suya. */
    private val generacion = MutableStateFlow(0)
    private var abierto: DispositivoAudio? = null
    private var usos = 0
    private var cierre: Job? = null

    @Volatile private var listoEnMs = 0L

    init {
        sistema.escucharCambios { refrescar() }
        refrescar()
    }

    /**
     * Hay que llamarlo cuando cambia [activado]: recalcula [modo] y, si el casco deja de valer, corta el canal (las
     * reservas vivas pasan al móvil como en una caída).
     */
    fun reevaluar() {
        refrescar()
    }

    override fun reservar(uso: UsoRuta): ReservaAudio {
        val casco = refrescar() ?: return ReservaAltavoz
        return synchronized(lock) {
            cierre?.cancel()
            cierre = null
            if (abierto != null || abrirYAnotar(casco)) {
                usos++
                Log.d(ETIQUETA, "Ruta por el casco para $uso")
                ReservaCasco(casco, generacion.value)
            } else {
                ReservaAltavoz
            }
        }
    }

    /** Abre el canal y apunta cuándo estará listo (con [lock] tomado). */
    private fun abrirYAnotar(casco: DispositivoAudio): Boolean {
        if (!abrir(casco)) return false
        abierto = casco
        listoEnMs = reloj() + ESPERA_SCO_MS
        return true
    }

    override fun infoSalida(reserva: ReservaAudio): AudioDeviceInfo? =
        reserva.dispositivo?.let { sistema.infoSalida(it.id) }

    override fun infoEntrada(reserva: ReservaAudio): AudioDeviceInfo? =
        reserva.dispositivo?.let { sistema.infoEntrada(it.id) }

    /** Recalcula [modo], corta el canal si el casco ya no está y devuelve el casco utilizable (o `null`). */
    private fun refrescar(): DispositivoAudio? {
        val casco = candidato()
        synchronized(lock) {
            estado.value = if (casco != null) ModoAudio.CASCO else ModoAudio.ALTAVOZ
            val actual = abierto
            if (actual != null && cambioDeCasco(casco, actual)) {
                Log.i(ETIQUETA, "El casco ya no está: vuelta al altavoz del móvil")
                cortar()
            }
        }
        return casco
    }

    /** El casco desapareció o, con la API 31, es otro distinto del abierto (en Android 11 los id no son estables). */
    private fun cambioDeCasco(
        casco: DispositivoAudio?,
        actual: DispositivoAudio,
    ): Boolean = casco == null || (sdk >= Build.VERSION_CODES.S && casco.id != actual.id)

    private fun candidato(): DispositivoAudio? {
        if (!activado() || !permiso()) return null
        return if (sdk >= Build.VERSION_CODES.S) {
            val lista = sistema.dispositivosComunicacion()
            lista.firstOrNull { it.tipo == TipoDispositivo.CASCO_SCO }
                ?: lista.firstOrNull { it.tipo == TipoDispositivo.CASCO_BLE }
        } else {
            val lista = sistema.salidasConectadas()
            lista.firstOrNull { it.tipo == TipoDispositivo.CASCO_SCO }
                ?: lista.firstOrNull { it.tipo == TipoDispositivo.A2DP }?.takeIf { sistema.scoDisponible() }
        }
    }

    /** Abre el canal de comunicación; `false` si el sistema lo rechaza (y deja todo como estaba). */
    private fun abrir(casco: DispositivoAudio): Boolean {
        sistema.fijarModoComunicacion(true)
        val abierto =
            try {
                if (sdk >= Build.VERSION_CODES.S) {
                    sistema.fijarComunicacion(casco)
                } else {
                    sistema.fijarSco(true)
                    true
                }
            } catch (e: SecurityException) {
                Log.w(ETIQUETA, "Sin permiso para abrir el canal del casco: ${e.javaClass.simpleName}")
                false
            } catch (e: IllegalStateException) {
                Log.w(ETIQUETA, "No se pudo abrir el canal del casco: ${e.javaClass.simpleName}")
                false
            }
        if (!abierto) sistema.fijarModoComunicacion(false)
        return abierto
    }

    /** Cierra el canal en el sistema (con [lock] tomado). */
    private fun cerrarCanal() {
        try {
            if (sdk >= Build.VERSION_CODES.S) sistema.liberarComunicacion() else sistema.fijarSco(false)
        } catch (e: IllegalStateException) {
            Log.w(ETIQUETA, "No se pudo cerrar el canal del casco: ${e.javaClass.simpleName}")
        }
        sistema.fijarModoComunicacion(false)
        abierto = null
    }

    /** Caída: se cierra el canal y las reservas vivas quedan perdidas (con [lock] tomado). */
    private fun cortar() {
        cierre?.cancel()
        cierre = null
        cerrarCanal()
        usos = 0
        generacion.value += 1
    }

    private fun programarCierre() {
        cierre?.cancel()
        cierre =
            ambito.launch {
                delay(LINGER_MS)
                synchronized(lock) {
                    if (usos == 0 && abierto != null) cerrarCanal()
                }
            }
    }

    private inner class ReservaCasco(
        override val dispositivo: DispositivoAudio,
        private val generacionPropia: Int,
    ) : ReservaAudio {
        private var cerrada = false

        override val perdida: Boolean get() = generacion.value != generacionPropia

        override fun msHastaListo(): Long = (listoEnMs - reloj()).coerceAtLeast(0L)

        override suspend fun esperarCaida() {
            generacion.first { it != generacionPropia }
        }

        override fun close() {
            synchronized(lock) {
                if (cerrada) return
                cerrada = true
                if (generacion.value == generacionPropia) {
                    usos -= 1
                    if (usos <= 0) programarCierre()
                }
            }
        }
    }

    companion object {
        /** Cuánto se mantiene el canal tras el último uso (cubre frases seguidas de una misma curva). */
        const val LINGER_MS = 5_000L

        /** Margen para que el canal SCO quede establecido antes de sonar o grabar (no hay aviso fiable en todas). */
        const val ESPERA_SCO_MS = 400L
        private const val ETIQUETA = "RutaAudio"
    }
}
