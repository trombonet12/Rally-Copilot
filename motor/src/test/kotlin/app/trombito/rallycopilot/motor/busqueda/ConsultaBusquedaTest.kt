package app.trombito.rallycopilot.motor.busqueda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsultaBusquedaTest {
    private fun escritas(texto: String) = ConsultaBusqueda.de(texto).palabras.map { it.escrita }

    private fun alternativas(texto: String) = ConsultaBusqueda.de(texto).palabras.map { it.alternativas }

    @Test
    fun `normaliza y separa en palabras`() {
        assertEquals(listOf("hospital", "son", "espases"), escritas("  Hospital SON Espases "))
        assertEquals(listOf("cala", "or"), escritas("Cala d'Or"))
    }

    @Test
    fun `descarta las palabras vacias`() {
        assertEquals(listOf("carrer", "colom"), escritas("Carrer de la Colom"))
        assertEquals(listOf("colom"), escritas("el colom"))
        assertTrue(ConsultaBusqueda.de("de la").vacia)
        assertTrue(ConsultaBusqueda.de("  ").vacia)
    }

    @Test
    fun `expande sinonimos castellano y catalan en los dos sentidos`() {
        assertEquals(listOf(listOf("calle", "carrer"), listOf("colom")), alternativas("calle colom"))
        assertEquals(listOf("carrer", "calle"), alternativas("carrer").single())
        assertEquals(listOf("castillo", "castell"), alternativas("castillo").single())
        assertEquals(listOf("plaza", "placa"), alternativas("plaza").single())
        assertEquals(listOf("placa", "plaza"), alternativas("Plaça").single())
        assertEquals(listOf("collegi", "colegio"), alternativas("Col·legi").single())
        assertEquals(listOf("estacion", "estacio"), alternativas("estación").single())
        assertEquals(listOf("esglesia", "iglesia"), alternativas("església").single())
        assertEquals(listOf("platja", "playa"), alternativas("platja").single())
    }

    @Test
    fun `una palabra sin sinonimo solo se tiene a si misma`() {
        assertEquals(listOf("colom"), alternativas("Colom").single())
    }

    @Test
    fun `una sola palabra de categoria da modo categoria`() {
        assertEquals("hospital", ConsultaBusqueda.de("hospital").categoria)
        assertEquals("hospital", ConsultaBusqueda.de("Hospitales").categoria)
        assertEquals("gasolinera", ConsultaBusqueda.de("gasolinera").categoria)
        assertEquals("farmacia", ConsultaBusqueda.de("farmacia").categoria)
        assertEquals("supermercado", ConsultaBusqueda.de("supermercado").categoria)
        assertEquals("playa", ConsultaBusqueda.de("platja").categoria)
        assertEquals("castillo", ConsultaBusqueda.de("castell").categoria)
        assertEquals("aparcamiento", ConsultaBusqueda.de("parking").categoria)
        assertEquals("aeropuerto", ConsultaBusqueda.de("aeropuerto").categoria)
        assertEquals("hospital", ConsultaBusqueda.de("el hospital").categoria)
    }

    @Test
    fun `varias palabras o una palabra normal no son categoria`() {
        assertNull(ConsultaBusqueda.de("hospital son espases").categoria)
        assertNull(ConsultaBusqueda.de("castillo bellver").categoria)
        assertNull(ConsultaBusqueda.de("colom").categoria)
        assertNull(ConsultaBusqueda.de("calle").categoria)
        assertNull(ConsultaBusqueda.de("").categoria)
    }

    @Test
    fun `detecta cuando se pide una calle`() {
        assertTrue(ConsultaBusqueda.de("calle colom palma").pideCalle)
        assertTrue(ConsultaBusqueda.de("carrer major").pideCalle)
        assertTrue(ConsultaBusqueda.de("Avenida Argentina").pideCalle)
        assertTrue(ConsultaBusqueda.de("plaça major").pideCalle)
        assertTrue(ConsultaBusqueda.de("paseo del born").pideCalle)
        assertTrue(ConsultaBusqueda.de("camino de muro").pideCalle)
        assertTrue(ConsultaBusqueda.de("carretera valldemossa").pideCalle)
        assertFalse(ConsultaBusqueda.de("hospital son espases").pideCalle)
        assertFalse(ConsultaBusqueda.de("colom palma").pideCalle)
    }
}
