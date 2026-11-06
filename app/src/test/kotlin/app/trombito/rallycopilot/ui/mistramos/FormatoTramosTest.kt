package app.trombito.rallycopilot.ui.mistramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class FormatoTramosTest {
    @Test
    fun `distancias en metros y kilometros con coma`() {
        assertEquals("850 m", formatearDistanciaTramo(850.0))
        assertEquals("12,4 km", formatearDistanciaTramo(12_400.0))
        assertEquals("1,0 km", formatearDistanciaTramo(1_000.0))
        assertEquals("0 m", formatearDistanciaTramo(-5.0))
    }

    @Test
    fun `kilometros con una decimal y velocidad entera`() {
        assertEquals("123,4 km", formatearKilometros(123.44))
        assertEquals("54 km/h", formatearVelocidadKmh(54.28))
    }

    @Test
    fun `fecha con mes en espanol y hora de la zona`() {
        val ms = 1_793_555_000_000L
        assertEquals("1 nov 2026 · 17:43", formatearFechaHora(ms, ZoneOffset.UTC))
        assertEquals("1 nov", formatearFechaCorta(ms, ZoneOffset.UTC))
        assertEquals("1 nov 2026 · 18:43", formatearFechaHora(ms, ZoneOffset.ofHours(1)))
    }

    @Test
    fun `la proyeccion centra la traza, deja norte arriba y no deforma`() {
        val puntos = listOf(PuntoGeo(39.80, 2.80), PuntoGeo(39.90, 2.80), PuntoGeo(39.90, 2.90))
        val p = proyectarTraza(puntos, 400f, 400f, 20f)
        assertEquals(3, p.size)
        // El punto más al norte tiene la y menor.
        assertTrue(p[1].y < p[0].y)
        assertEquals(p[1].y, p[2].y, 0.01f)
        // Todo cabe dentro del margen.
        p.forEach {
            assertTrue(it.x in 20f..380f)
            assertTrue(it.y in 20f..380f)
        }
        // La traza es más alta que ancha en metros (0,1° lat ≈ 11,1 km, 0,1° lon ≈ 8,5 km): ocupa todo el alto.
        assertEquals(20f, p[1].y, 0.01f)
        assertEquals(380f, p[0].y, 0.01f)
        assertTrue(p[2].x - p[0].x < 360f)
    }

    @Test
    fun `un punto solo o repetido queda en el centro`() {
        val unico = proyectarTraza(listOf(PuntoGeo(39.8, 2.8)), 200f, 100f, 10f)
        assertEquals(100f, unico.single().x, 0.01f)
        assertEquals(50f, unico.single().y, 0.01f)
        assertTrue(proyectarTraza(emptyList(), 200f, 100f, 10f).isEmpty())
    }

    @Test
    fun `aligerar conserva extremos y respeta el maximo`() {
        val lista = (0..1000).toList()
        val corta = aligerar(lista, 100)
        assertEquals(100, corta.size)
        assertEquals(0, corta.first())
        assertEquals(1000, corta.last())
        assertEquals(lista.take(5), aligerar(lista.take(5), 100))
    }
}
