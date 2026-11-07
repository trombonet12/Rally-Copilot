package app.trombito.rallycopilot.data.navegacion

import android.app.Application
import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificacionNavegacionTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val notificacion = NotificacionNavegacion(app)

    private fun titulo(n: Notification) = n.extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    @Test
    fun `reutilizar el constructor no arrastra el texto anterior ni repite la accion`() {
        val libre = notificacion.construir(EstadoNavegacion.Libre(desdeMs = 0L, distanciaM = 120.0))
        val error = notificacion.construir(EstadoNavegacion.Error("sin GPS"))
        val libreOtra = notificacion.construir(EstadoNavegacion.Libre(desdeMs = 0L, distanciaM = 1_300.0))

        assertEquals("sin GPS", error.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertNotEquals(titulo(libre), titulo(error))
        assertEquals(titulo(libre), titulo(libreOtra))
        assertEquals(1, libre.actions.size)
        assertEquals(1, libreOtra.actions.size)
    }

    @Test
    fun `los PendingIntent son los mismos en cada actualizacion`() {
        val primera = notificacion.construir(EstadoNavegacion.Libre(0L, 0.0))
        val segunda = notificacion.construir(EstadoNavegacion.Libre(0L, 500.0))

        assertSame(primera.actions[0].actionIntent, segunda.actions[0].actionIntent)
        assertSame(primera.contentIntent, segunda.contentIntent)
    }

    // --- LimitadorNotificacion (REN-5) ------------------------------------------------------------------

    private var ahoraMs = 0L

    private fun limitador() = LimitadorNotificacion(reloj = { ahoraMs }, periodoMs = 5_000)

    @Test
    fun `la primera publicacion es inmediata`() {
        assertEquals(0L, limitador().esperaMs("a"))
    }

    @Test
    fun `un cambio solo de texto espera hasta cumplir el periodo`() {
        val limitador = limitador()
        limitador.publicada("a")

        ahoraMs = 2_000
        assertEquals(3_000L, limitador.esperaMs("a"))
        ahoraMs = 5_000
        assertEquals(0L, limitador.esperaMs("a"))
    }

    @Test
    fun `un cambio de maniobra o de estado se publica ya`() {
        val limitador = limitador()
        limitador.publicada("a")

        ahoraMs = 100
        assertEquals(0L, limitador.esperaMs("b"))
    }

    @Test
    fun `la clave de la notificacion ignora la distancia y distingue la maniobra`() {
        val libreCerca = EstadoNavegacion.Libre(0L, 100.0)
        val libreLejos = EstadoNavegacion.Libre(0L, 9_000.0)

        assertEquals(claveDeNotificacion(libreCerca), claveDeNotificacion(libreLejos))
        assertNotEquals(claveDeNotificacion(libreCerca), claveDeNotificacion(EstadoNavegacion.Error("x")))
    }
}
