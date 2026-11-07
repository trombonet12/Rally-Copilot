package app.trombito.rallycopilot.data.escucha

import org.junit.Assert.assertEquals
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

    @Test
    fun `no abre mientras el copiloto habla aunque haya voz fuerte`() {
        val puerta = PuertaVoz()

        repeat(10) { assertFalse(puerta.abierta(trozo(4_000), 1_600, hablando = true)) }
    }

    @Test
    fun `al empezar a hablar cierra la puerta que estaba abierta`() {
        val puerta = PuertaVoz(colaMuestras = 16_000, silencioTrasHablar = 0)
        assertTrue(puerta.abierta(trozo(3_000), 1_600))

        assertFalse(puerta.abierta(trozo(50), 1_600, hablando = true))
        assertFalse(puerta.abierta(trozo(50), 1_600))
    }

    @Test
    fun `tras hablar sigue cerrada el rato del eco y luego vuelve a abrir`() {
        val puerta = PuertaVoz(silencioTrasHablar = 3_200)
        puerta.abierta(trozo(50), 1_600, hablando = true)

        assertFalse(puerta.abierta(trozo(4_000), 1_600))
        assertFalse(puerta.abierta(trozo(4_000), 1_600))
        assertTrue(puerta.abierta(trozo(4_000), 1_600))
    }

    @Test
    fun `histeresis una vez abierta aguanta un nivel por debajo del umbral de apertura`() {
        val puerta = PuertaVoz(colaMuestras = 1_600)
        // 300 < 400 (umbral de apertura) pero > 240 (60 % del umbral): solo mantiene la puerta si ya estaba abierta.
        puerta.abierta(trozo(50), 1_600)
        assertFalse(puerta.abierta(trozo(300), 1_600))
        assertTrue(puerta.abierta(trozo(3_000), 1_600))
        assertTrue(puerta.abierta(trozo(300), 1_600))
        assertTrue(puerta.abierta(trozo(300), 1_600))
    }

    @Test
    fun `cuenta el porcentaje de bloques que pasan al reconocedor`() {
        val puerta = PuertaVoz(colaMuestras = 1_600)
        assertEquals(0.0, puerta.porcentajeAbierta(), 0.0)

        repeat(3) { puerta.abierta(trozo(50), 1_600) }
        puerta.abierta(trozo(3_000), 1_600)

        assertEquals(4L, puerta.bloques)
        assertEquals(1L, puerta.decodificados)
        assertEquals(25.0, puerta.porcentajeAbierta(), 0.001)
    }
}
