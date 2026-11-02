package app.trombito.rallycopilot.data.escucha

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuertaVozTest {
    private fun trozo(
        amplitud: Short,
        n: Int = 1_600,
    ) = ShortArray(n) { amplitud }

    @Test
    fun `el silencio no pasa`() {
        val puerta = PuertaVoz()

        repeat(20) { assertFalse(puerta.abierta(trozo(50), 1_600)) }
    }

    @Test
    fun `la voz pasa y sigue pasando un rato despues`() {
        val puerta = PuertaVoz(colaMuestras = 3_200)

        assertTrue(puerta.abierta(trozo(3_000), 1_600))
        assertTrue(puerta.abierta(trozo(50), 1_600))
        assertFalse(puerta.abierta(trozo(50), 1_600))
    }

    @Test
    fun `un ruido constante se aprende y deja de abrir la puerta`() {
        val puerta = PuertaVoz()
        repeat(60) { puerta.abierta(trozo(300), 1_600) }

        assertFalse(puerta.abierta(trozo(500), 1_600))
        assertTrue(puerta.abierta(trozo(4_000), 1_600))
    }
}
