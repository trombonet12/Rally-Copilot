package app.trombito.rallycopilot.ui.navegacion.notas

import app.trombito.rallycopilot.motor.notas.Direccion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** La tarjeta solo escribe la curva: la distancia a la siguiente nota ya solo se canta por voz. */
class TextoNotaTest {
    @Test
    fun `la distancia a la siguiente nota no se escribe`() {
        val texto = textoNota(deNota(nota(6, siguienteM = 100)), false)
        assertEquals("DERECHA 6", texto.text)
        assertTrue(texto.spanStyles.isEmpty())
    }

    @Test
    fun `sin distancia a la siguiente el texto es el mismo`() {
        assertEquals("DERECHA 3", textoNota(deNota(nota(3)), false).text)
    }

    @Test
    fun `abreviado conserva solo la curva`() {
        val elemento = deNota(nota(2, direccion = Direccion.IZQUIERDA, siguienteM = 30))
        assertEquals("IZQ 2", textoNota(elemento, false, abreviar = true).text)
    }
}
