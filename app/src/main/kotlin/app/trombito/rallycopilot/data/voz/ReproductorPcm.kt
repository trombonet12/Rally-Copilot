package app.trombito.rallycopilot.data.voz

import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import app.trombito.rallycopilot.data.audio.ReservaAudio
import app.trombito.rallycopilot.data.audio.RutaAudio
import app.trombito.rallycopilot.data.audio.RutaSoloAltavoz
import app.trombito.rallycopilot.data.audio.UsoRuta
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
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
 *
 * Con un intercom de casco ([ruta]) suena por el canal de comunicación (`USAGE_VOICE_COMMUNICATION`, que sigue al
 * dispositivo de comunicación fijado; la guía de navegación va a la estrategia multimedia, que no es la del HFP). Si el
 * casco se cae a media frase, se corta y se repite entera por el altavoz del móvil.
 */
class ReproductorAudioTrack(
    private val ruta: RutaAudio = RutaSoloAltavoz,
) : ReproductorPcm {
    override suspend fun reproducir(
        muestras: ShortArray,
        frecuenciaHz: Int,
    ) {
        if (muestras.isEmpty()) return
        repeat(INTENTOS) {
            val reserva = ruta.reservar(UsoRuta.SALIDA)
            val completa =
                try {
                    sonarPorLaRuta(reserva, muestras, frecuenciaHz)
                } finally {
                    reserva.close()
                }
            if (completa) return
        }
    }

    /** `false` si el casco se cayó antes de acabar la frase (hay que repetirla por el móvil). */
    private suspend fun sonarPorLaRuta(
        reserva: ReservaAudio,
        muestras: ShortArray,
        frecuenciaHz: Int,
    ): Boolean {
        if (reserva.dispositivo == null) {
            sonar(reserva, muestras, frecuenciaHz)
            return true
        }
        return coroutineScope {
            val sonido =
                async {
                    delay(reserva.msHastaListo())
                    sonar(reserva, muestras, frecuenciaHz)
                    true
                }
            val caida =
                async {
                    reserva.esperarCaida()
                    false
                }
            select<Boolean> {
                sonido.onAwait { it }
                caida.onAwait { it }
            }.also { coroutineContext.cancelChildren() }
        }
    }

    private suspend fun sonar(
        reserva: ReservaAudio,
        muestras: ShortArray,
        frecuenciaHz: Int,
    ) {
        val pista = crearPista(reserva, muestras.size, frecuenciaHz)
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
        reserva: ReservaAudio,
        muestras: Int,
        frecuenciaHz: Int,
    ): AudioTrack =
        try {
            val porCasco = reserva.dispositivo != null
            AudioTrack
                .Builder()
                .setAudioAttributes(if (porCasco) AtributosVoz.atributosCasco else AtributosVoz.atributos)
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
                .also { pista -> if (porCasco) pista.preferredDevice = ruta.infoSalida(reserva) }
        } catch (e: UnsupportedOperationException) {
            throw ErrorSintesis("No se pudo crear la salida de audio", e)
        }

    private companion object {
        const val INTENTOS = 2
        const val MARGEN_MS = 1_500L
        const val MS_POR_S = 1_000L
    }
}
