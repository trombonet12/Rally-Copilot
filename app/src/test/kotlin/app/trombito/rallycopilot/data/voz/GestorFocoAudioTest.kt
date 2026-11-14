package app.trombito.rallycopilot.data.voz

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.data.audio.ModoAudio
import app.trombito.rallycopilot.data.audio.ReservaAltavoz
import app.trombito.rallycopilot.data.audio.ReservaAudio
import app.trombito.rallycopilot.data.audio.RutaAudio
import app.trombito.rallycopilot.data.audio.UsoRuta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class GestorFocoAudioTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val sombra = shadowOf(app.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
    private val modo = MutableStateFlow(ModoAudio.ALTAVOZ)
    private val ruta =
        object : RutaAudio {
            override val modo: StateFlow<ModoAudio> = this@GestorFocoAudioTest.modo

            override fun reservar(uso: UsoRuta): ReservaAudio = ReservaAltavoz

            override fun infoSalida(reserva: ReservaAudio): AudioDeviceInfo? = null

            override fun infoEntrada(reserva: ReservaAudio): AudioDeviceInfo? = null
        }
    private val gestor = GestorFocoAudio(app, ruta)

    @Test
    fun `con el casco puesto pide el foco con los atributos de comunicacion que usa la voz`() {
        modo.value = ModoAudio.CASCO

        assertTrue(gestor.pedir())

        val peticion = requireNotNull(sombra.lastAudioFocusRequest).audioFocusRequest
        assertEquals(AudioAttributes.USAGE_VOICE_COMMUNICATION, peticion.audioAttributes.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, peticion.audioAttributes.contentType)
    }

    @Test
    fun `soltar abandona la peticion con la que se pidio aunque el modo haya cambiado entre medias`() {
        modo.value = ModoAudio.CASCO
        gestor.pedir()
        val pedida = requireNotNull(sombra.lastAudioFocusRequest).audioFocusRequest
        modo.value = ModoAudio.ALTAVOZ

        gestor.soltar()

        assertSame(pedida, requireNotNull(sombra.lastAbandonedAudioFocusRequest))
    }

    @Test
    fun `pide foco transitorio con ducking y atributos de guia de navegacion`() {
        assertTrue(gestor.pedir())

        val peticion = requireNotNull(sombra.lastAudioFocusRequest).audioFocusRequest
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, peticion.focusGain)
        assertEquals(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, peticion.audioAttributes.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, peticion.audioAttributes.contentType)
    }

    @Test
    fun `soltar abandona la peticion concedida`() {
        gestor.pedir()
        assertNull(sombra.lastAbandonedAudioFocusRequest)

        gestor.soltar()

        assertNotNull(sombra.lastAbandonedAudioFocusRequest)
    }

    @Test
    fun `si se deniega el foco no se concede y soltar no abandona nada`() {
        sombra.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        assertFalse(gestor.pedir())
        gestor.soltar()

        assertNull(sombra.lastAbandonedAudioFocusRequest)
    }

    @Test
    fun `pedir dos veces seguidas no repite la peticion`() {
        assertTrue(gestor.pedir())
        val primera = sombra.lastAudioFocusRequest
        assertTrue(gestor.pedir())
        assertSame(primera, sombra.lastAudioFocusRequest)
    }
}
