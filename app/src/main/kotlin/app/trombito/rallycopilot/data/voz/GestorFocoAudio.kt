package app.trombito.rallycopilot.data.voz

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Atributos de la voz del copiloto: guía de navegación (el sistema la trata como los giros de un navegador). */
object AtributosVoz {
    val atributos: AudioAttributes =
        AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    /**
     * Con intercom de casco la voz va como comunicación: es el uso que sigue al dispositivo fijado con
     * `setCommunicationDevice`/SCO (la guía de navegación va por la estrategia multimedia y puede quedarse en A2DP o
     * en el altavoz).
     */
    val atributosCasco: AudioAttributes =
        AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
}

/** Foco de audio alrededor de cada frase. */
interface FocoAudio {
    /** Pide el foco; `true` si se concede (o ya se tenía). Si es `false`, no se debe sonar. */
    fun pedir(): Boolean

    /** Suelta el foco (la música vuelve a su volumen). No hace nada si no se tenía. */
    fun soltar()
}

/**
 * Foco `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` con los [AtributosVoz]: se pide antes de sonar y se suelta al acabar, de
 * modo que la música de otras apps baja mientras habla el copiloto y vuelve después.
 */
class GestorFocoAudio
    @Inject
    constructor(
        @ApplicationContext contexto: Context,
    ) : FocoAudio {
        private val audio = contexto.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        private val peticion: AudioFocusRequest =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AtributosVoz.atributos)
                .setOnAudioFocusChangeListener { }
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
