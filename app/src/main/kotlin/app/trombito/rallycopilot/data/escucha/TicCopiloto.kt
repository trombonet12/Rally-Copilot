package app.trombito.rallycopilot.data.escucha

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import app.trombito.rallycopilot.data.audio.ReservaAudio
import app.trombito.rallycopilot.data.audio.RutaAudio
import app.trombito.rallycopilot.data.audio.RutaSoloAltavoz
import app.trombito.rallycopilot.data.audio.UsoRuta
import app.trombito.rallycopilot.data.voz.AtributosVoz
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** El «tic» corto que suena tras oír «Copiloto», antes de escuchar la orden (no hay clip empaquetado: se genera). */
object GeneradorTic {
    const val FRECUENCIA_MUESTREO_HZ = 22_050
    const val TONO_HZ = 1_300.0
    const val DURACION_MS = 70
    private const val AMPLITUD = 0.55
    private const val RAMPA_MS = 8
    private const val MS_POR_S = 1_000

    /** PCM 16 bits mono: un seno corto con rampa de subida y bajada (sin chasquidos). */
    fun pcm(): ShortArray {
        val total = FRECUENCIA_MUESTREO_HZ * DURACION_MS / MS_POR_S
        val rampa = FRECUENCIA_MUESTREO_HZ * RAMPA_MS / MS_POR_S
        return ShortArray(total) { i ->
            val borde = minOf(i, total - 1 - i)
            val envolvente = if (borde < rampa) borde.toDouble() / rampa else 1.0
            val onda = sin(2 * PI * TONO_HZ * i / FRECUENCIA_MUESTREO_HZ)
            (onda * envolvente * AMPLITUD * Short.MAX_VALUE).toInt().toShort()
        }
    }
}

/**
 * Reproduce el tic por el canal de guía de navegación (`USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`, el mismo que la voz del
 * copiloto) y espera a que acabe, para que el micrófono no lo oiga como parte de la orden. Si el dispositivo no lo
 * puede reproducir, no suena y no pasa nada. Con intercom de casco ([ruta]) suena por él, como la voz.
 */
class ReproductorTic(
    private val ruta: RutaAudio = RutaSoloAltavoz,
) {
    suspend fun sonar() {
        val muestras = GeneradorTic.pcm()
        val reserva = ruta.reservar(UsoRuta.SALIDA)
        try {
            // El tic es lo primero que suena: si el canal del casco aún se está abriendo, se espera a que esté.
            delay(reserva.msHastaListo())
            val pista = crearPista(reserva, muestras) ?: return
            try {
                pista.play()
                delay(GeneradorTic.DURACION_MS + MARGEN_MS)
            } catch (e: IllegalStateException) {
                // Pista en mal estado: sin tic, la orden se escucha igual.
                android.util.Log.w("TicCopiloto", "No se pudo reproducir el tic: ${e.javaClass.simpleName}")
            } finally {
                pista.release()
            }
        } finally {
            reserva.close()
        }
    }

    private fun crearPista(
        reserva: ReservaAudio,
        muestras: ShortArray,
    ): AudioTrack? =
        try {
            val porCasco = reserva.dispositivo != null
            val pista =
                AudioTrack
                    .Builder()
                    .setAudioAttributes(
                        if (porCasco) {
                            AtributosVoz.atributosCasco
                        } else {
                            AudioAttributes
                                .Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        },
                    ).setAudioFormat(
                        AudioFormat
                            .Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(GeneradorTic.FRECUENCIA_MUESTREO_HZ)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    ).setBufferSizeInBytes(muestras.size * Short.SIZE_BYTES)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            if (porCasco) pista.preferredDevice = ruta.infoSalida(reserva)
            pista.write(muestras, 0, muestras.size)
            pista
        } catch (e: UnsupportedOperationException) {
            android.util.Log.w("TicCopiloto", "Sin pista de audio: ${e.javaClass.simpleName}")
            null
        } catch (e: IllegalArgumentException) {
            android.util.Log.w("TicCopiloto", "Sin pista de audio: ${e.javaClass.simpleName}")
            null
        }

    private companion object {
        const val MARGEN_MS = 30L
    }
}
