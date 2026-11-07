package app.trombito.rallycopilot.motor.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProyeccionPlanaTest {
    private val origen = PuntoGeo(39.6, 2.9)
    private val plano = ProyeccionPlana(origen)

    @Test
    fun `el origen es el cero`() {
        assertEquals(0.0, plano.x(origen), 1e-9)
        assertEquals(0.0, plano.y(origen), 1e-9)
    }

    @Test
    fun `un grado al norte son 111 km y al este se reduce con el coseno de la latitud`() {
        assertEquals(111_195.0, plano.y(PuntoGeo(40.6, 2.9)), 5.0)
        assertEquals(111_195.0 * Math.cos(Math.toRadians(39.6)), plano.x(PuntoGeo(39.6, 3.9)), 5.0)
    }

    @Test
    fun `aPunto invierte x e y`() {
        val p = PuntoGeo(39.71, 3.02)
        val vuelta = plano.aPunto(plano.x(p), plano.y(p))
        assertEquals(p.lat, vuelta.lat, 1e-9)
        assertEquals(p.lon, vuelta.lon, 1e-9)
    }

    @Test
    fun `la distancia plana coincide con la haversine en tramos cortos`() {
        val b = PuntoGeo(39.605, 2.905)
        assertEquals(origen.distanciaA(b), plano.distancia(origen, b), 0.5)
    }

    @Test
    fun `deTraza usa el primer punto de origen y la latitud media`() {
        val traza = listOf(PuntoGeo(39.5, 2.6), PuntoGeo(39.7, 2.6))
        val p = ProyeccionPlana.deTraza(traza)
        assertEquals(traza.first(), p.origen)
        assertEquals(0.0, p.y(traza.first()), 1e-9)
        assertEquals(traza.first().distanciaA(traza.last()), p.y(traza.last()), 50.0)
    }

    @Test
    fun `distancia a un segmento perpendicular a su mitad`() {
        assertEquals(5.0, distanciaASegmento(5.0, 5.0, 0.0, 0.0, 10.0, 0.0), 1e-9)
    }

    @Test
    fun `distancia a un segmento mas alla de los extremos es la del extremo`() {
        assertEquals(5.0, distanciaASegmento(-3.0, 4.0, 0.0, 0.0, 10.0, 0.0), 1e-9)
        assertEquals(5.0, distanciaASegmento(13.0, 4.0, 0.0, 0.0, 10.0, 0.0), 1e-9)
    }

    @Test
    fun `un segmento degenerado vale la distancia al punto`() {
        assertEquals(5.0, distanciaASegmento(3.0, 4.0, 0.0, 0.0, 0.0, 0.0), 1e-9)
    }

    @Test
    fun `un punto sobre el segmento esta a cero`() {
        assertEquals(0.0, distanciaASegmento(4.0, 0.0, 0.0, 0.0, 10.0, 0.0), 1e-9)
        assertEquals(0.0, distanciaASegmento(0.0, 0.0, 0.0, 0.0, 0.0, 0.0), 1e-9)
    }

    @Test
    fun `fraccion en segmento acota a 0 y 1`() {
        assertEquals(0.0, fraccionEnSegmento(-5.0, 1.0, 0.0, 0.0, 10.0, 0.0), 1e-12)
        assertEquals(0.5, fraccionEnSegmento(5.0, 1.0, 0.0, 0.0, 10.0, 0.0), 1e-12)
        assertEquals(1.0, fraccionEnSegmento(50.0, 1.0, 0.0, 0.0, 10.0, 0.0), 1e-12)
        assertEquals(0.0, fraccionEnSegmento(1.0, 1.0, 2.0, 2.0, 2.0, 2.0), 1e-12)
    }

    @Test
    fun `distancia a una polilinea toma el segmento mas cercano`() {
        // Línea en L: 0,001 grados al norte y luego al este (~111 m y ~86 m).
        val a = PuntoGeo(39.6, 2.9)
        val b = PuntoGeo(39.601, 2.9)
        val c = PuntoGeo(39.601, 2.901)
        val cerca = PuntoGeo(39.6005, 2.9001)
        val d = plano.distanciaAPolilinea(cerca, listOf(a, b, c))
        assertEquals(0.0001 * 111_195.0 * Math.cos(Math.toRadians(39.6)), d, 0.5)
    }

    @Test
    fun `distancia a una polilinea de un punto es la del punto y vacia es infinita`() {
        val otro = PuntoGeo(39.6, 2.901)
        assertEquals(origen.distanciaA(otro), plano.distanciaAPolilinea(origen, listOf(otro)), 0.5)
        assertTrue(plano.distanciaAPolilinea(origen, emptyList()).isInfinite())
    }

    @Test
    fun `puntos coincidentes dan cero`() {
        assertEquals(0.0, plano.distanciaAPolilinea(origen, listOf(origen, origen)), 1e-9)
    }
}
