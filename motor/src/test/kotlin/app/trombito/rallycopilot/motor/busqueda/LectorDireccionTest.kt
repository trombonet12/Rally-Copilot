package app.trombito.rallycopilot.motor.busqueda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LectorDireccionTest {
    @Test
    fun `direccion de google con portal, piso, barrio, codigo postal y region`() {
        val d = LectorDireccion.leer("Carrer de Ramon Berenguer III, 5, Bajos, Norte, 07002 Palma, Balearic Islands")

        assertEquals("Carrer de Ramon Berenguer III", d.calle)
        assertEquals("Palma", d.municipio)
    }

    @Test
    fun `quita el codigo postal del municipio y descarta region y pais`() {
        val d = LectorDireccion.leer("Carrer de Colom, 5, 07001 Palma, Illes Balears, España")

        assertEquals("Carrer de Colom", d.calle)
        assertEquals("Palma", d.municipio)
    }

    @Test
    fun `palma de mallorca es palma`() {
        assertEquals("Palma", LectorDireccion.leer("Avinguda Argentina, Palma de Mallorca").municipio)
        val d = LectorDireccion.leer("Avenida Argentina, 07013 Palma de Mallorca, Mallorca, Spain")
        assertEquals("Palma", d.municipio)
    }

    @Test
    fun `municipios con grafia castellana o catalana`() {
        assertEquals("Pollença", LectorDireccion.leer("Carrer Major, 3, 07460 Pollença, Illes Balears").municipio)
        assertEquals("Pollença", LectorDireccion.leer("Calle Mayor, 3, Pollensa").municipio)
        assertEquals("Sóller", LectorDireccion.leer("Carrer Major, 3, 2ºA, 07100 Soller, Mallorca").municipio)
        assertEquals("Sant Joan", LectorDireccion.leer("Calle Mayor, San Juan").municipio)
        assertEquals("sa Pobla", LectorDireccion.leer("Carrer Major, 07420 Sa Pobla").municipio)
    }

    @Test
    fun `sin municipio conocido devuelve solo la calle`() {
        val d = LectorDireccion.leer("Carrer Colom, 5, Son Espases, Balearic Islands")

        assertEquals("Carrer Colom", d.calle)
        assertNull(d.municipio)
    }

    @Test
    fun `el primer segmento es la calle aunque sea un lugar`() {
        val d = LectorDireccion.leer("Hospital Universitari Son Espases, Carretera de Valldemossa, 79, 07120 Palma")

        assertEquals("Hospital Universitari Son Espases", d.calle)
        assertEquals("Palma", d.municipio)
    }

    @Test
    fun `sin comas no se parte`() {
        val d = LectorDireccion.leer("  Jaume III 12  ")

        assertEquals("Jaume III 12", d.calle)
        assertNull(d.municipio)
    }

    @Test
    fun `segmentos vacios y texto vacio`() {
        assertEquals("Carrer Colom", LectorDireccion.leer(",, Carrer Colom, , Palma ,").calle)
        assertEquals("Palma", LectorDireccion.leer(",, Carrer Colom, , Palma ,").municipio)
        assertEquals("", LectorDireccion.leer("  ").calle)
        assertNull(LectorDireccion.leer("  ").municipio)
    }

    @Test
    fun `el municipio solo cuenta si el segmento es exactamente un municipio`() {
        assertNull(LectorDireccion.leer("Carrer Colom, Palma Arena").municipio)
        assertNull(LectorDireccion.leer("Carrer Colom, 07002").municipio)
    }
}
