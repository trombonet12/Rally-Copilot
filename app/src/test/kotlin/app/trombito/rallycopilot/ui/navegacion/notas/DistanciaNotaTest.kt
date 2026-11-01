package app.trombito.rallycopilot.ui.navegacion.notas

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanciaNotaTest {
    @Test
    fun `por debajo de 200 m se redondea de 10 en 10`() {
        assertEquals(0, distanciaRedondeadaM(4.0))
        assertEquals(10, distanciaRedondeadaM(5.0))
        assertEquals(90, distanciaRedondeadaM(94.0))
        assertEquals(120, distanciaRedondeadaM(123.0))
        assertEquals(190, distanciaRedondeadaM(194.0))
    }

    @Test
    fun `desde 200 m se redondea de 50 en 50`() {
        assertEquals(200, distanciaRedondeadaM(195.0))
        assertEquals(200, distanciaRedondeadaM(199.0))
        assertEquals(200, distanciaRedondeadaM(224.0))
        assertEquals(250, distanciaRedondeadaM(225.0))
        assertEquals(250, distanciaRedondeadaM(260.0))
        assertEquals(800, distanciaRedondeadaM(780.0))
    }

    @Test
    fun `una distancia negativa se queda en cero`() {
        assertEquals(0, distanciaRedondeadaM(-3.0))
    }
}
