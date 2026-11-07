package app.trombito.rallycopilot.motor.velocidad

import org.junit.Assert.assertEquals
import org.junit.Test

class UnidadesTest {
    @Test
    fun `de metros por segundo a kilometros por hora`() {
        assertEquals(36.0, 10.0.msAKmh(), 1e-9)
        assertEquals(0.0, 0.0.msAKmh(), 1e-9)
    }

    @Test
    fun `de kilometros por hora a metros por segundo`() {
        assertEquals(10.0, 36.0.kmhAMs(), 1e-9)
    }

    @Test
    fun `la ida y la vuelta se compensan`() {
        assertEquals(87.3, 87.3.kmhAMs().msAKmh(), 1e-9)
    }

    @Test
    fun `milisegundos por segundo`() {
        assertEquals(1000.0, MS_POR_S, 0.0)
    }
}
