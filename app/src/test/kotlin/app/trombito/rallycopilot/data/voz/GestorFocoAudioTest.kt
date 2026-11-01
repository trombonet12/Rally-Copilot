package app.trombito.rallycopilot.data.voz

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
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
    private val gestor = GestorFocoAudio(app)

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
