package app.trombito.rallycopilot.ui.navegacion.notas

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.motor.notas.Direccion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** UX-14: la distancia a la siguiente nota lleva unidad y va atenuada; la curva no. */
@RunWith(RobolectricTestRunner::class)
class TextoNotaTest {
    private val recursos = ApplicationProvider.getApplicationContext<Context>().resources
    private val color = Color(0xFFFFC94A)

    @Test
    fun `la distancia a la siguiente nota lleva unidad`() {
        val texto = textoNota(recursos, deNota(nota(6, siguienteM = 100)), false, color)
        assertEquals("DERECHA 6 ·\u00a0100\u00a0m", texto.text)
    }

    @Test
    fun `solo esa distancia va atenuada, la curva conserva el color`() {
        val texto = textoNota(recursos, deNota(nota(4, siguienteM = 50)), false, color)
        assertEquals(1, texto.spanStyles.size)
        val span = texto.spanStyles.single()
        assertEquals("·\u00a050\u00a0m", texto.text.substring(span.start, span.end))
        val atenuado = span.item.color
        assertTrue("más transparente que el color de la nota: $atenuado", atenuado.alpha < color.alpha)
        assertEquals(color.red, atenuado.red, 0.01f)
    }

    @Test
    fun `sin distancia a la siguiente no hay nada atenuado`() {
        val texto = textoNota(recursos, deNota(nota(3)), false, color)
        assertEquals("DERECHA 3", texto.text)
        assertTrue(texto.spanStyles.isEmpty())
    }

    @Test
    fun `abreviado conserva la unidad y el atenuado`() {
        val elemento = deNota(nota(2, direccion = Direccion.IZQUIERDA, siguienteM = 30))
        val texto = textoNota(recursos, elemento, false, color, abreviar = true)
        assertTrue(texto.text.startsWith("IZQ 2"))
        assertTrue(texto.text.endsWith("30\u00a0m"))
        assertEquals(1, texto.spanStyles.size)
    }
}
