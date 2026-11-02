package app.trombito.rallycopilot.data.escucha

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import app.trombito.rallycopilot.data.voz.FocoAudio

/**
 * Foco `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` mientras el copiloto escucha una orden: la música de otras apps se
 * PAUSA (no baja de volumen, decisión de Joan 2026-11-02) y vuelve al soltarlo.
 */
class FocoAudioExclusivo(
    contexto: Context,
) : FocoAudio {
    private val audio = contexto.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val peticion: AudioFocusRequest =
        AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            ).setOnAudioFocusChangeListener { }
            .build()
    private var concedido = false

    @Synchronized
    override fun pedir(): Boolean {
        if (!concedido) {
            concedido = audio.requestAudioFocus(peticion) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        return concedido
    }

    @Synchronized
    override fun soltar() {
        if (concedido) {
            audio.abandonAudioFocusRequest(peticion)
            concedido = false
        }
    }
}
