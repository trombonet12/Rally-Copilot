package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.voz.TipoFrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ventanas de aviso en los bordes de velocidad (m/s): los topes, los redondeos a cientos y los valores no físicos. */
class VentanasAvisoTest {
    @Test
    fun `avisoM acota a 300-800 m y redondea a cientos`() {
        assertEquals(300.0, VentanasAviso.avisoM(0.0), 0.0)
        assertEquals(300.0, VentanasAviso.avisoM(-10.0), 0.0) // negativo = parado
        assertEquals(300.0, VentanasAviso.avisoM(19.9), 0.0) // 298,5 m -> mínimo 300
        assertEquals(300.0, VentanasAviso.avisoM(20.0), 0.0) // 300 m exactos
        assertEquals(500.0, VentanasAviso.avisoM(30.0), 0.0) // 450 m -> 500 (redondeo al alza en 450)
        assertEquals(600.0, VentanasAviso.avisoM(38.0), 0.0) // 570 m
        assertEquals(800.0, VentanasAviso.avisoM(53.3), 0.0) // 799,5 m
        assertEquals(800.0, VentanasAviso.avisoM(53.4), 0.0) // 801 m -> tope
        assertEquals(800.0, VentanasAviso.avisoM(200.0), 0.0)
    }

    @Test
    fun `avisoRadarM tiene un minimo de 200 m y el mismo tope de 800 m`() {
        assertEquals(200.0, VentanasAviso.avisoRadarM(0.0), 0.0)
        assertEquals(200.0, VentanasAviso.avisoRadarM(-1.0), 0.0)
        assertEquals(200.0, VentanasAviso.avisoRadarM(13.3), 0.0) // 199,5 m
        assertEquals(200.0, VentanasAviso.avisoRadarM(14.0), 0.0) // 210 m -> 200
        assertEquals(200.0, VentanasAviso.avisoRadarM(16.0), 0.0) // 240 m -> 200
        assertEquals(300.0, VentanasAviso.avisoRadarM(17.0), 0.0) // 255 m -> 300
        assertEquals(800.0, VentanasAviso.avisoRadarM(100.0), 0.0)
    }

    @Test
    fun `entradaM es v por 6 s acotado a 80-250 m y sin redondear`() {
        assertEquals(80.0, VentanasAviso.entradaM(0.0), 0.0)
        assertEquals(80.0, VentanasAviso.entradaM(-5.0), 0.0)
        assertEquals(80.0, VentanasAviso.entradaM(13.0), 0.0) // 78 m -> mínimo
        assertEquals(90.0, VentanasAviso.entradaM(15.0), 1e-9)
        assertEquals(168.0, VentanasAviso.entradaM(28.0), 1e-9) // sin redondear a cientos
        assertEquals(250.0, VentanasAviso.entradaM(41.7), 0.0) // 250,2 m -> tope
        assertEquals(250.0, VentanasAviso.entradaM(90.0), 0.0)
    }

    @Test
    fun `aDistancia redondea a cientos y devuelve null por debajo de 50 m`() {
        assertNull(VentanasAviso.aDistancia(0.0))
        assertNull(VentanasAviso.aDistancia(49.9))
        assertEquals("a cien", VentanasAviso.aDistancia(50.0))
        assertEquals("a cien", VentanasAviso.aDistancia(149.0))
        assertEquals("a doscientos", VentanasAviso.aDistancia(150.0))
        assertEquals("a quinientos", VentanasAviso.aDistancia(510.0))
    }

    @Test
    fun `alerta crea una frase de tipo ALERTA con su punto`() {
        val frase = VentanasAviso.alerta("radar-1", "radar a quinientos", 1234.0)
        assertEquals("radar-1", frase.id)
        assertEquals("radar a quinientos", frase.texto)
        assertEquals(TipoFrase.ALERTA, frase.tipo)
        assertEquals(1234.0, frase.puntoM!!, 0.0)
        assertNull(VentanasAviso.alerta("x", "y", null).puntoM)
    }
}
