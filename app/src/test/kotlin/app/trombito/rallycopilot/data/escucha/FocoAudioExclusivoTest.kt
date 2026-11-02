package app.trombito.rallycopilot.data.escucha

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class FocoAudioExclusivoTest {
    private val contexto = ApplicationProvider.getApplicationContext<Context>()
    private val audio = contexto.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Test
    fun `pide el foco transitorio exclusivo para pausar la musica`() {
        val foco = FocoAudioExclusivo(contexto)

        assertTrue(foco.pedir())

        val peticion = shadowOf(audio).lastAudioFocusRequest
        assertNotNull(peticion)
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE, peticion.audioFocusRequest.focusGain)
    }

    @Test
    fun `soltar abandona el foco una sola vez`() {
        val foco = FocoAudioExclusivo(contexto)
        foco.pedir()

        foco.soltar()
        foco.soltar()

        assertNotNull(shadowOf(audio).lastAbandonedAudioFocusRequest)
        assertTrue(foco.pedir())
    }

    @Test
    fun `si el sistema lo deniega pedir devuelve false`() {
        shadowOf(audio).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        assertFalse(FocoAudioExclusivo(contexto).pedir())
    }
}
