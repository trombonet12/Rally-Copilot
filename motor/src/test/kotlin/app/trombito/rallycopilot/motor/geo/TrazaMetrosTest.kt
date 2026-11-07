package app.trombito.rallycopilot.motor.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrazaMetrosTest {
    private val traza = List(21) { PuntoGeo(39.6 + it * 0.00085, 2.9) }
    private val metros = TrazaMetros(traza)

    @Test
    fun `los metros se convierten en posicion y la posicion en metros`() {
        val medio = metros.puntoEn(metros.longitudM / 2)
        val proyeccion = checkNotNull(metros.proyectar(PuntoGeo(medio.lat, medio.lon + 0.00003)))

        assertEquals(metros.longitudM / 2, proyeccion.metros, 1.0)
        assertTrue("a unos 2,6 m del trazo: ${proyeccion.distanciaM}", proyeccion.distanciaM in 2.0..3.5)
        assertEquals(traza.first(), metros.puntoEn(-50.0))
        assertEquals(traza.last(), metros.puntoEn(1e9))
        assertNull(TrazaMetros(listOf(traza.first())).proyectar(medio))
    }

    @Test
    fun `una proyeccion mas alla de los extremos se queda en el extremo`() {
        val antes = checkNotNull(metros.proyectar(PuntoGeo(traza.first().lat - 0.01, traza.first().lon)))
        val despues = checkNotNull(metros.proyectar(PuntoGeo(traza.last().lat + 0.01, traza.last().lon)))

        assertEquals(0.0, antes.metros, 1e-6)
        assertEquals(metros.longitudM, despues.metros, 1e-6)
        assertTrue(antes.distanciaM > 1_000.0)
    }
}
