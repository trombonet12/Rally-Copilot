package app.trombito.rallycopilot.ui.mapa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelCompactoTest {
    @Test
    fun `sin planificar nunca es compacto`() {
        assertFalse(panelCompacto(previo = false, velocidadKmh = 0, planificando = false))
        assertFalse(panelCompacto(previo = true, velocidadKmh = 0, planificando = false))
    }

    @Test
    fun `parado planificando se encoge por debajo de 5 km por hora`() {
        assertTrue(panelCompacto(previo = false, velocidadKmh = 0, planificando = true))
        assertTrue(panelCompacto(previo = false, velocidadKmh = 4, planificando = true))
        assertFalse(panelCompacto(previo = false, velocidadKmh = 5, planificando = true))
    }

    @Test
    fun `sin velocidad cuenta como parado`() {
        assertTrue(panelCompacto(previo = false, velocidadKmh = null, planificando = true))
    }

    @Test
    fun `entre 5 y 10 km por hora se queda como estaba`() {
        assertTrue(panelCompacto(previo = true, velocidadKmh = 7, planificando = true))
        assertFalse(panelCompacto(previo = false, velocidadKmh = 7, planificando = true))
        assertTrue(panelCompacto(previo = true, velocidadKmh = 10, planificando = true))
    }

    @Test
    fun `al pasar de 10 km por hora se amplia`() {
        assertFalse(panelCompacto(previo = true, velocidadKmh = 11, planificando = true))
    }

    @Test
    fun `una secuencia de velocidades no parpadea`() {
        var compacto = false
        val vistos =
            listOf(0, 3, 6, 8, 12, 9, 7, 4, 2).map { v ->
                compacto = panelCompacto(compacto, v, planificando = true)
                compacto
            }
        assertEquals(listOf(true, true, true, true, false, false, false, true, true), vistos)
    }

    @Test
    fun `elige primero un lugar con nombre y luego una via`() {
        val via = CandidatoNombre("Carrer Colom", esPunto = false)
        val bar = CandidatoNombre("Bar Cort", esPunto = true)
        assertEquals("Bar Cort", elegirNombre(listOf(via, bar)))
        assertEquals("Carrer Colom", elegirNombre(listOf(CandidatoNombre(null, true), via)))
        assertEquals("Carrer Colom", elegirNombre(listOf(CandidatoNombre("  ", true), via)))
    }

    @Test
    fun `sin nada con nombre no hay nombre`() {
        assertNull(elegirNombre(emptyList()))
        assertNull(elegirNombre(listOf(CandidatoNombre(null, false), CandidatoNombre("", true))))
    }
}
