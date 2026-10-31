package app.trombito.rallycopilot.ui.maniobras

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatoDistanciaManiobraTest {
    @Test
    fun `por debajo de 200 m se redondea a 10 m`() {
        assertEquals("0 m", formatearDistanciaManiobra(-4.0))
        assertEquals("0 m", formatearDistanciaManiobra(4.0))
        assertEquals("80 m", formatearDistanciaManiobra(83.0))
        assertEquals("190 m", formatearDistanciaManiobra(188.0))
    }

    @Test
    fun `de 200 m a 1 km se redondea a 50 m`() {
        assertEquals("200 m", formatearDistanciaManiobra(212.0))
        assertEquals("350 m", formatearDistanciaManiobra(352.0))
        assertEquals("350 m", formatearDistanciaManiobra(330.0))
        assertEquals("950 m", formatearDistanciaManiobra(960.0))
    }

    @Test
    fun `desde un kilometro se da en km con una decimal y coma`() {
        assertEquals("1,0 km", formatearDistanciaManiobra(980.0))
        assertEquals("1,0 km", formatearDistanciaManiobra(1000.0))
        assertEquals("1,2 km", formatearDistanciaManiobra(1234.0))
        assertEquals("12,5 km", formatearDistanciaManiobra(12_500.0))
    }
}
