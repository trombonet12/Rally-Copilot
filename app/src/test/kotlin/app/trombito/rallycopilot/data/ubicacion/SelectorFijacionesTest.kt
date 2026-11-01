package app.trombito.rallycopilot.data.ubicacion

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectorFijacionesTest {
    private fun fija(segundo: Double) = Fijacion(PuntoGeo(39.6, 2.9), (segundo * 1000).toLong(), null, null, 20.0)

    private val selector = SelectorFijaciones()

    @Test
    fun `en interior sin gps el proveedor rapido pasa`() {
        assertTrue(selector.aceptar(fija(1.0), OrigenFijacion.RAPIDO))
        assertTrue(selector.aceptar(fija(2.0), OrigenFijacion.RAPIDO))
    }

    @Test
    fun `el gps siempre pasa`() {
        assertTrue(selector.aceptar(fija(1.0), OrigenFijacion.GPS))
        assertTrue(selector.aceptar(fija(1.5), OrigenFijacion.GPS))
    }

    @Test
    fun `con gps de hace menos de 3 s se ignora el rapido`() {
        selector.aceptar(fija(10.0), OrigenFijacion.GPS)

        assertFalse(selector.aceptar(fija(10.5), OrigenFijacion.RAPIDO))
        assertFalse(selector.aceptar(fija(12.9), OrigenFijacion.RAPIDO))
    }

    @Test
    fun `si el gps se pierde 3 s vuelve el rapido`() {
        selector.aceptar(fija(10.0), OrigenFijacion.GPS)

        assertFalse(selector.aceptar(fija(12.0), OrigenFijacion.RAPIDO))
        assertTrue(selector.aceptar(fija(13.0), OrigenFijacion.RAPIDO))
        assertTrue(selector.aceptar(fija(14.0), OrigenFijacion.RAPIDO))
    }

    @Test
    fun `cuando el gps vuelve recupera el mando`() {
        selector.aceptar(fija(10.0), OrigenFijacion.GPS)
        assertTrue(selector.aceptar(fija(14.0), OrigenFijacion.RAPIDO))

        selector.aceptar(fija(15.0), OrigenFijacion.GPS)

        assertFalse(selector.aceptar(fija(16.0), OrigenFijacion.RAPIDO))
    }

    @Test
    fun `un rapido anterior al ultimo gps se ignora`() {
        selector.aceptar(fija(20.0), OrigenFijacion.GPS)

        assertFalse(selector.aceptar(fija(5.0), OrigenFijacion.RAPIDO))
    }
}
