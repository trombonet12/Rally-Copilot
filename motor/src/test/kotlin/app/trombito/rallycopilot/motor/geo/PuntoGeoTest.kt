package app.trombito.rallycopilot.motor.geo

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

class PuntoGeoTest {
    private val catedralPalma = PuntoGeo(39.5675, 2.6485)
    private val puertoSoller = PuntoGeo(39.7967, 2.6936)

    @Test
    fun `la distancia entre la catedral de Palma y el puerto de Soller ronda 25,8 km`() {
        assertEquals(25_770.0, catedralPalma.distanciaA(puertoSoller), 150.0)
    }

    @Test
    fun `la distancia es simetrica y cero consigo mismo`() {
        assertEquals(catedralPalma.distanciaA(puertoSoller), puertoSoller.distanciaA(catedralPalma), 1e-6)
        assertEquals(0.0, catedralPalma.distanciaA(catedralPalma), 1e-9)
    }

    @Test
    fun `un grado de latitud mide unos 111 km`() {
        assertEquals(111_195.0, PuntoGeo(39.0, 3.0).distanciaA(PuntoGeo(40.0, 3.0)), 10.0)
    }

    @Test
    fun `la longitud de una polilinea suma sus tramos`() {
        val ruta = listOf(catedralPalma, puertoSoller, catedralPalma)
        assertEquals(2 * catedralPalma.distanciaA(puertoSoller), ruta.longitudMetros(), 1e-6)
        assertEquals(0.0, listOf(catedralPalma).longitudMetros(), 0.0)
    }

    @Test
    fun `rechaza coordenadas fuera de rango`() {
        assertFailsWith<IllegalArgumentException> { PuntoGeo(91.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { PuntoGeo(0.0, 181.0) }
    }
}
