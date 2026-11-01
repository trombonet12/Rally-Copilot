package app.trombito.rallycopilot.data.voz

import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Reproduce un PCM de 16 bits mono. */
interface ReproductorPcm {
    /**
     * Suena [muestras] y vuelve cuando acaba. Si la corrutina se cancela, corta el sonido y libera el recurso.
     * Lanza [ErrorSintesis] si el sistema no puede crear la salida de audio.
     */
    suspend fun reproducir(
        muestras: ShortArray,
        frecuenciaHz: Int,
    )
}

/**
 * [AudioTrack] en `MODE_STATIC` (las frases duran pocos segundos): se escribe todo el audio, se lanza y se espera a que
 * el cabezal llegue al final.
 */
class ReproductorAudioTrack : ReproductorPcm {
    override suspend fun reproducir(
        muestras: ShortArray,
        frecuenciaHz: Int,
    ) {
        if (muestras.isEmpty()) return
        val pista = crearPista(muestras.size, frecuenciaHz)
        try {
            pista.write(muestras, 0, muestras.size)
            pista.play()
            val limiteMs = System.currentTimeMillis() + muestras.size * MS_POR_S / frecuenciaHz + MARGEN_MS
            while (currentCoroutineContext().isActive &&
                pista.playbackHeadPosition < muestras.size &&
                System.currentTimeMillis() < limiteMs
            ) {
                delay(PERIODO_SONDEO_MS)
            }
        } finally {
            pista.stop()
            pista.release()
        }
    }

    private fun crearPista(
        muestras: Int,
        frecuenciaHz: Int,
    ): AudioTrack =
        try {
            AudioTrack
                .Builder()
                .setAudioAttributes(AtributosVoz.atributos)
                .setAudioFormat(
                    AudioFormat
                        .Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(frecuenciaHz)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                ).setBufferSizeInBytes(muestras * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (e: UnsupportedOperationException) {
            throw ErrorSintesis("No se pudo crear la salida de audio", e)
        }

    private companion object {
        const val PERIODO_SONDEO_MS = 20L
        const val MARGEN_MS = 1_500L
        const val MS_POR_S = 1_000L
    }
}
