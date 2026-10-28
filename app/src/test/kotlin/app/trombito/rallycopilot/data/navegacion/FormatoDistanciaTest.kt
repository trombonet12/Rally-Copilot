package app.trombito.rallycopilot.data.navegacion

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatoDistanciaTest {
    @Test
    fun `menos de un kilometro se redondea a 50 m`() {
        assertEquals("0 m", formatearDistancia(-3.0))
        assertEquals("300 m", formatearDistancia(312.0))
        assertEquals("350 m", formatearDistancia(330.0))
    }

    @Test
    fun `desde un kilometro se da en km con coma decimal`() {
        assertEquals("1,0 km", formatearDistancia(980.0))
        assertEquals("1,2 km", formatearDistancia(1234.0))
        assertEquals("12,5 km", formatearDistancia(12_500.0))
    }
}
