package app.trombito.rallycopilot.ui.alertas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FormatoAlertasTest {
    private val recursos = ApplicationProvider.getApplicationContext<Context>().resources

    private fun hace(
        minutos: Long,
        extraMs: Long = 0L,
    ) = textoAntiguedad(recursos, minutos * 60_000L + extraMs)

    @Test
    fun `menos de una hora se da en minutos`() {
        assertEquals("hace 31 min", hace(31))
        assertEquals("hace 59 min", hace(59, 59_000L))
    }

    @Test
    fun `de una hora a un dia se da en horas redondeadas hacia abajo`() {
        assertEquals("hace 1 h", hace(60))
        assertEquals("hace 2 h", hace(2 * 60 + 40))
        assertEquals("hace 23 h", hace(23 * 60 + 59))
    }

    @Test
    fun `desde un dia se da en dias`() {
        assertEquals("hace 1 d", hace(24 * 60))
        assertEquals("hace 3 d", hace(3 * 24 * 60 + 600))
    }

    @Test
    fun `nunca dice cero minutos`() {
        assertEquals("hace 1 min", textoAntiguedad(recursos, 5_000L))
    }
}
