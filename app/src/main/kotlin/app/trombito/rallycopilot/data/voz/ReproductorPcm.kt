package app.trombito.rallycopilot.data.voz

import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

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
 * el cabezal llegue al final con un marcador de posición (el sistema avisa una vez; no se sondea el cabezal). Si el
 * aviso no llegara, un plazo (duración de la frase y un margen) evita quedarse esperando.
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
            val plazoMs = muestras.size * MS_POR_S / frecuenciaHz + MARGEN_MS
            withTimeoutOrNull(plazoMs) { sonarHastaElFinal(pista, muestras.size) }
        } finally {
            pista.setPlaybackPositionUpdateListener(null)
            pista.stop()
            pista.release()
        }
    }

    private suspend fun sonarHastaElFinal(
        pista: AudioTrack,
        muestras: Int,
    ) = suspendCancellableCoroutine<Unit> { continuacion ->
        val aviso =
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(pista: AudioTrack?) {
                    if (continuacion.isActive) continuacion.resume(Unit)
                }

                override fun onPeriodicNotification(pista: AudioTrack?) = Unit
            }
        pista.setPlaybackPositionUpdateListener(aviso, Handler(Looper.getMainLooper()))
        // Un fotograma antes del final: el aviso en el último fotograma exacto no está garantizado en MODE_STATIC.
        pista.notificationMarkerPosition = (muestras - 1).coerceAtLeast(1)
        pista.play()
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
        const val MARGEN_MS = 1_500L
        const val MS_POR_S = 1_000L
    }
}
