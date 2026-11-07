package app.trombito.rallycopilot.motor.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatoDistanciaTest {
    @Test
    fun `por debajo de 200 m usa el paso fino`() {
        assertEquals(120.0, FormatoDistancia.redondeada(123.0, 10, 50), 0.0)
        assertEquals(190.0, FormatoDistancia.redondeada(194.0, 10, 50), 0.0)
        assertEquals(10.0, FormatoDistancia.redondeada(5.0, 10, 50), 0.0)
        assertEquals(0.0, FormatoDistancia.redondeada(4.9, 10, 50), 0.0)
    }

    @Test
    fun `desde 200 m usa el paso grueso`() {
        assertEquals(200.0, FormatoDistancia.redondeada(210.0, 10, 50), 0.0)
        assertEquals(350.0, FormatoDistancia.redondeada(348.0, 10, 50), 0.0)
        assertEquals(1000.0, FormatoDistancia.redondeada(980.0, 10, 50), 0.0)
    }

    @Test
    fun `un solo paso se pasa dos veces`() {
        assertEquals(50.0, FormatoDistancia.redondeada(30.0, 50, 50), 0.0)
        assertEquals(0.0, FormatoDistancia.redondeada(24.0, 50, 50), 0.0)
        assertEquals(300.0, FormatoDistancia.redondeada(310.0, 50, 50), 0.0)
        assertEquals(850.0, FormatoDistancia.redondeada(851.0, 10, 10), 0.0)
    }

    @Test
    fun `los negativos cuentan como cero`() {
        assertEquals(0.0, FormatoDistancia.redondeada(-40.0, 10, 50), 0.0)
    }
}
