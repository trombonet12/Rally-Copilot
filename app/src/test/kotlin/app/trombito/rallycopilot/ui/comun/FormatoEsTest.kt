package app.trombito.rallycopilot.ui.comun

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatoEsTest {
    @Test
    fun `tiempo usa coma decimal y trunca las decimas`() {
        assertEquals("01:23,4", FormatoEs.tiempo(83_499))
        assertEquals("00:00,0", FormatoEs.tiempo(0))
        assertEquals("00:09,9", FormatoEs.tiempo(9_999))
    }

    @Test
    fun `tiempo sin decimas y pasada la hora`() {
        assertEquals("01:23", FormatoEs.tiempo(83_499, conDecimas = false))
        assertEquals("1:01:01,5", FormatoEs.tiempo(3_661_500))
        assertEquals("00:00,0", FormatoEs.tiempo(-5))
    }

    @Test
    fun `kilometros con una decima y coma`() {
        assertEquals("31,9 km", FormatoEs.kilometros(31.92))
        assertEquals("31,9 km", FormatoEs.kilometrosDesdeMetros(31_920.0))
        assertEquals("0,0 km", FormatoEs.kilometros(-1.0))
    }

    @Test
    fun `distancia pasa de metros a kilometros en el kilometro`() {
        assertEquals("850 m", FormatoEs.distancia(850.4))
        assertEquals("1,0 km", FormatoEs.distancia(1_000.0))
        assertEquals("12,4 km", FormatoEs.distancia(12_400.0))
    }

    @Test
    fun `gigabytes y tamano`() {
        val gb = 1024L * 1024 * 1024
        assertEquals("1,6 GB", FormatoEs.gigabytes((1.6 * gb).toLong()))
        assertEquals("1,6 GB", FormatoEs.tamano((1.6 * gb).toLong()))
        assertEquals("850 MB", FormatoEs.tamano(850L * 1024 * 1024))
    }

    @Test
    fun `velocidad y decimal`() {
        assertEquals("54 km/h", FormatoEs.velocidad(53.6))
        assertEquals("1,5", FormatoEs.decimal(1.5))
        assertEquals("2,25", FormatoEs.decimal(2.25, 2))
    }
}
