package app.trombito.rallycopilot.data.escucha

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

/** Frecuencia de muestreo del micrófono y del reconocedor (el modelo es de banda ancha a 16 kHz). */
const val FRECUENCIA_HZ = 16_000

/** Un micrófono abierto: [leer] rellena [buffer] con muestras PCM de 16 bits y devuelve cuántas (< 0 si falla). */
interface FuenteAudio : AutoCloseable {
    fun leer(buffer: ShortArray): Int
}

/** Abre el micrófono; `null` si no se puede (sin permiso, ocupado). */
fun interface FabricaAudio {
    fun abrir(): FuenteAudio?
}

/** Un reconocedor de voz con una gramática ya fijada. */
interface Reconocedor : AutoCloseable {
    /** Le da [n] muestras de [muestras]; `true` si con ellas acaba una frase (hay un [resultado] que recoger). */
    fun aceptar(
        muestras: ShortArray,
        n: Int,
    ): Boolean

    /** Texto parcial de la frase en curso (vacío si aún no hay nada). */
    fun parcial(): String

    /** Texto de la frase que acaba de terminar con [aceptar]. */
    fun resultado(): String

    /** Texto de lo que quede por decidir al parar (vacío si nada). */
    fun fin(): String
}

/** Crea reconocedores con una gramática JSON (`["frase", ..., "[unk]"]`); `null` si el modelo no está disponible. */
fun interface FabricaReconocedor {
    fun crear(gramaticaJson: String): Reconocedor?

    /** Deja el modelo listo en disco sin cargarlo en memoria (copia de assets); por defecto no hace nada. */
    fun precopiar() = Unit

    /** Libera el modelo cargado (el siguiente [crear] lo vuelve a cargar); por defecto no hace nada. */
    fun liberar() = Unit
}

/** Un [AudioRecord] (o su doble): costura para probar [FabricaAudioRecord] sin el framework de audio. */
internal interface Grabadora {
    /** `false` si el sistema no ha podido reservar el micrófono (permiso, otra app lo usa). */
    val inicializada: Boolean

    fun iniciar()

    fun leer(buffer: ShortArray): Int

    fun parar()

    fun liberar()
}

/** Micrófono real: [AudioRecord] a 16 kHz mono, fuente de reconocimiento de voz. */
class FabricaAudioRecord internal constructor(
    private val crear: () -> Grabadora,
) : FabricaAudio {
    constructor() : this(::grabadoraReal)

    override fun abrir(): FuenteAudio? =
        try {
            val grabadora = crear()
            if (grabadora.inicializada) {
                grabadora.iniciar()
                FuenteAudioRecord(grabadora)
            } else {
                grabadora.liberar()
                null
            }
        } catch (e: SecurityException) {
            Log.w(ETIQUETA, "Sin acceso al micrófono: ${e.javaClass.simpleName}")
            null
        } catch (e: IllegalStateException) {
            Log.w(ETIQUETA, "No se pudo abrir el micrófono: ${e.javaClass.simpleName}")
            null
        }

    private companion object {
        const val ETIQUETA = "Escucha"
    }
}

@SuppressLint("MissingPermission") // lo comprueba quien llama (Escucha.tienePermiso)
private fun grabadoraReal(): Grabadora {
    val canal = AudioFormat.CHANNEL_IN_MONO
    val formato = AudioFormat.ENCODING_PCM_16BIT
    val minimo = AudioRecord.getMinBufferSize(FRECUENCIA_HZ, canal, formato)
    val grabadora =
        AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            FRECUENCIA_HZ,
            canal,
            formato,
            maxOf(minimo, FRECUENCIA_HZ),
        )
    return object : Grabadora {
        override val inicializada: Boolean get() = grabadora.state == AudioRecord.STATE_INITIALIZED

        override fun iniciar() = grabadora.startRecording()

        override fun leer(buffer: ShortArray): Int = grabadora.read(buffer, 0, buffer.size)

        override fun parar() = grabadora.stop()

        override fun liberar() = grabadora.release()
    }
}

private class FuenteAudioRecord(
    private val grabadora: Grabadora,
) : FuenteAudio {
    override fun leer(buffer: ShortArray): Int = grabadora.leer(buffer)

    override fun close() {
        try {
            grabadora.parar()
        } catch (_: IllegalStateException) {
            // ya parada
        }
        grabadora.liberar()
    }
}
