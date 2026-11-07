package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RutaMedidaTest {
    // Un tramo al norte (~111 m) y luego otro al este (~86 m), en Mallorca.
    private val a = PuntoGeo(39.600, 2.900)
    private val b = PuntoGeo(39.601, 2.900)
    private val c = PuntoGeo(39.601, 2.901)
    private val ruta = RutaMedida(listOf(a, b, c))

    @Test
    fun `la longitud es la suma haversine de los tramos`() {
        assertEquals(a.distanciaA(b) + b.distanciaA(c), ruta.longitudM, 1e-9)
    }

    @Test
    fun `proyectar un punto sobre el trazo da su posicion y la distancia lateral`() {
        val p = ruta.proyectar(PuntoGeo(39.6005, 2.9001))
        assertEquals(a.distanciaA(b) / 2, p.posicionM, 1.0)
        assertEquals(0.0001 * 111_195.0 * Math.cos(Math.toRadians(39.6)), p.distanciaM, 0.5)
    }

    @Test
    fun `mas alla de los extremos se satura`() {
        assertEquals(0.0, ruta.proyectar(PuntoGeo(39.59, 2.9)).posicionM, 1e-6)
        assertEquals(ruta.longitudM, ruta.proyectar(PuntoGeo(39.601, 2.95)).posicionM, 1e-6)
    }

    @Test
    fun `punto y rumbo a una distancia dada`() {
        assertEquals(0.0, ruta.rumboEn(10.0), 1e-6)
        assertEquals(90.0, ruta.rumboEn(ruta.longitudM - 10.0), 1e-6)
        assertEquals(a, ruta.puntoEn(-5.0))
        assertEquals(c.lon, ruta.puntoEn(1e9).lon, 1e-9)
        assertEquals(b.lat, ruta.puntoEn(a.distanciaA(b)).lat, 1e-6)
    }

    @Test
    fun `necesita al menos dos puntos`() {
        assertThrows(IllegalArgumentException::class.java) { RutaMedida(listOf(a)) }
    }
}
