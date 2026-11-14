package app.trombito.rallycopilot.data.ubicacion

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import org.junit.Assert.assertEquals
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

    private fun conPrecision(
        segundo: Double,
        precisionM: Double?,
        velocidadMs: Double? = 14.0,
    ) = Fijacion(PuntoGeo(39.6, 2.9), (segundo * 1000).toLong(), velocidadMs, 90.0, precisionM)

    @Test
    fun `con el gps mudo el fused de alta precision sale a 1 Hz`() {
        val aceptadas = (1..60).count { selector.aceptar(conPrecision(it.toDouble(), 6.0), OrigenFijacion.RAPIDO) }

        assertEquals(60, aceptadas)
    }

    @Test
    fun `un fused bueno con precision y velocidad gana a un gps fresco pero impreciso`() {
        selector.aceptar(conPrecision(10.0, 40.0), OrigenFijacion.GPS)

        assertTrue(selector.aceptar(conPrecision(10.5, 6.0), OrigenFijacion.RAPIDO))
    }

    @Test
    fun `un fused de mas de 15 m o sin velocidad no desplaza a un gps fresco`() {
        selector.aceptar(conPrecision(10.0, 40.0), OrigenFijacion.GPS)

        assertFalse(selector.aceptar(conPrecision(10.5, 16.0), OrigenFijacion.RAPIDO))
        assertFalse(selector.aceptar(conPrecision(10.6, 6.0, velocidadMs = null), OrigenFijacion.RAPIDO))
    }

    @Test
    fun `con gps bueno manda el gps aunque el fused sea parecido`() {
        val salida = mutableListOf<OrigenFijacion>()
        for (s in 1..10) {
            if (selector.aceptar(conPrecision(s + 0.0, 4.0), OrigenFijacion.GPS)) salida += OrigenFijacion.GPS
            if (selector.aceptar(conPrecision(s + 0.5, 3.0), OrigenFijacion.RAPIDO)) salida += OrigenFijacion.RAPIDO
        }

        assertEquals(List(10) { OrigenFijacion.GPS }, salida)
    }

    @Test
    fun `no alterna a cada fijacion cuando las dos fuentes son parecidas`() {
        val salida = mutableListOf<OrigenFijacion>()
        for (s in 1..10) {
            if (selector.aceptar(conPrecision(s + 0.0, 9.0 - (s % 2)), OrigenFijacion.GPS)) salida += OrigenFijacion.GPS
            if (selector.aceptar(conPrecision(s + 0.5, 8.0 + (s % 2)), OrigenFijacion.RAPIDO)) {
                salida +=
                    OrigenFijacion.RAPIDO
            }
        }

        assertEquals(10, salida.size)
        assertEquals(1, salida.distinct().size)
    }

    @Test
    fun `un fused claramente mejor toma el mando y lo conserva`() {
        selector.aceptar(conPrecision(10.0, 25.0), OrigenFijacion.GPS)

        assertTrue(selector.aceptar(conPrecision(10.5, 5.0), OrigenFijacion.RAPIDO))
        assertFalse(selector.aceptar(conPrecision(11.0, 4.0), OrigenFijacion.GPS))
        assertTrue(selector.aceptar(conPrecision(11.5, 5.0), OrigenFijacion.RAPIDO))
    }

    @Test
    fun `nunca retrocede en el tiempo con una mezcla de fuentes`() {
        assertTrue(selector.aceptar(conPrecision(10.0, 4.0), OrigenFijacion.GPS))

        assertFalse(selector.aceptar(conPrecision(9.0, 4.0), OrigenFijacion.GPS))
        assertFalse(selector.aceptar(conPrecision(9.5, 4.0), OrigenFijacion.RAPIDO))
        assertTrue(selector.aceptar(conPrecision(10.5, 4.0), OrigenFijacion.GPS))
        assertFalse(selector.aceptar(conPrecision(10.5, 4.0), OrigenFijacion.RAPIDO))
    }
}
