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
}

/** Micrófono real: [AudioRecord] a 16 kHz mono, fuente de reconocimiento de voz. */
class FabricaAudioRecord : FabricaAudio {
    @SuppressLint("MissingPermission") // lo comprueba quien llama (Escucha.tienePermiso)
    override fun abrir(): FuenteAudio? =
        try {
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
            if (grabadora.state == AudioRecord.STATE_INITIALIZED) {
                grabadora.startRecording()
                FuenteAudioRecord(grabadora)
            } else {
                grabadora.release()
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

private class FuenteAudioRecord(
    private val grabadora: AudioRecord,
) : FuenteAudio {
    override fun leer(buffer: ShortArray): Int = grabadora.read(buffer, 0, buffer.size)

    override fun close() {
        try {
            grabadora.stop()
        } catch (_: IllegalStateException) {
            // ya parada
        }
        grabadora.release()
    }
}
