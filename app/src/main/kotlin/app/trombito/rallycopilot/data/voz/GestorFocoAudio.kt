package app.trombito.rallycopilot.data.voz

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import app.trombito.rallycopilot.data.audio.ModoAudio
import app.trombito.rallycopilot.data.audio.RutaAudio
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
 * Foco `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`: se pide antes de sonar y se suelta al acabar, de modo que la música de
 * otras apps baja mientras habla el copiloto y vuelve después. Se pide con los mismos atributos con que suena la frase
 * ([ReproductorAudioTrack]): [AtributosVoz.atributosCasco] si la voz va por el intercom ([RutaAudio.modo]) y
 * [AtributosVoz.atributos] si va por el móvil; el foco y el sonido tienen que coincidir para que el sistema aplique
 * el ducking al flujo que toca.
 */
class GestorFocoAudio
    @Inject
    constructor(
        @ApplicationContext contexto: Context,
        private val ruta: RutaAudio,
    ) : FocoAudio {
        private val audio = contexto.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        /** La petición vigente (con la que se concedió el foco), para abandonar justo esa aunque el modo cambie. */
        private var concedida: AudioFocusRequest? = null

        @Synchronized
        override fun pedir(): Boolean {
            if (concedida == null) {
                val nueva = peticion(porCasco = ruta.modo.value == ModoAudio.CASCO)
                if (audio.requestAudioFocus(nueva) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) concedida = nueva
            }
            return concedida != null
        }

        @Synchronized
        override fun soltar() {
            concedida?.let {
                audio.abandonAudioFocusRequest(it)
                concedida = null
            }
        }

        private fun peticion(porCasco: Boolean): AudioFocusRequest =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(if (porCasco) AtributosVoz.atributosCasco else AtributosVoz.atributos)
                .setOnAudioFocusChangeListener { }
                .build()
    }
