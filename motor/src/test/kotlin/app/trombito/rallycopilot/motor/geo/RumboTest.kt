package app.trombito.rallycopilot.motor.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class RumboTest {
    private val base = PuntoGeo(39.6, 2.9)

    @Test
    fun `rumbo entre puntos en los cuatro cardinales`() {
        assertEquals(0.0, Rumbo.entre(base, PuntoGeo(39.7, 2.9)), 0.01)
        assertEquals(90.0, Rumbo.entre(base, PuntoGeo(39.6, 3.0)), 0.1)
        assertEquals(180.0, Rumbo.entre(base, PuntoGeo(39.5, 2.9)), 0.01)
        assertEquals(270.0, Rumbo.entre(base, PuntoGeo(39.6, 2.8)), 0.1)
    }

    @Test
    fun `rumbo entre puntos coincidentes es cero sin fallar`() {
        assertEquals(0.0, Rumbo.entre(base, base), 1e-9)
    }

    @Test
    fun `rumbo de vector`() {
        assertEquals(0.0, Rumbo.deVector(0.0, 1.0), 1e-9)
        assertEquals(90.0, Rumbo.deVector(1.0, 0.0), 1e-9)
        assertEquals(225.0, Rumbo.deVector(-1.0, -1.0), 1e-9)
    }

    @Test
    fun `diferencia entre rumbos va de 0 a 180 y es simetrica`() {
        assertEquals(20.0, Rumbo.diferencia(350.0, 10.0), 1e-9)
        assertEquals(20.0, Rumbo.diferencia(10.0, 350.0), 1e-9)
        assertEquals(180.0, Rumbo.diferencia(0.0, 180.0), 1e-9)
        assertEquals(0.0, Rumbo.diferencia(90.0, 90.0), 1e-9)
        assertEquals(10.0, Rumbo.diferencia(-5.0, 5.0), 1e-9)
    }

    @Test
    fun `normalizar lleva a 0 a 360`() {
        assertEquals(350.0, Rumbo.normalizar(-10.0), 1e-9)
        assertEquals(10.0, Rumbo.normalizar(370.0), 1e-9)
        assertEquals(0.0, Rumbo.normalizar(360.0), 1e-9)
        assertEquals(0.0, Rumbo.normalizar(0.0), 1e-9)
        assertEquals(350.0, Rumbo.normalizar(-730.0), 1e-9)
    }
}
