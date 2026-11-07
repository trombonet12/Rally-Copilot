package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Test

class GeometriaViaTest {
    private val a = PuntoGeo(39.600, 2.900)
    private val b = PuntoGeo(39.601, 2.900)
    private val c = PuntoGeo(39.601, 2.901)

    @Test
    fun `el progreso de un punto sobre el primer tramo es la distancia hasta su proyeccion`() {
        assertEquals(a.distanciaA(b) / 2, GeometriaVia.progresoM(listOf(a, b, c), PuntoGeo(39.6005, 2.9)), 1.0)
    }

    @Test
    fun `el progreso de un punto sobre el segundo tramo suma el primero`() {
        val esperado = a.distanciaA(b) + b.distanciaA(c) / 2
        assertEquals(esperado, GeometriaVia.progresoM(listOf(a, b, c), PuntoGeo(39.601, 2.9005)), 1.0)
    }

    @Test
    fun `antes del inicio el progreso es cero y una polilinea sin tramos tambien`() {
        assertEquals(0.0, GeometriaVia.progresoM(listOf(a, b), PuntoGeo(39.59, 2.9)), 1e-6)
        assertEquals(0.0, GeometriaVia.progresoM(listOf(a), PuntoGeo(39.6, 2.9)), 0.0)
    }
}
