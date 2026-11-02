package app.trombito.rallycopilot.motor.tramos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CortesTramoTest {
    private val cortes = listOf(250.0, 500.0, 750.0)

    @Test
    fun `mover un corte lo deja donde se arrastra si cabe`() {
        assertEquals(listOf(250.0, 620.0, 750.0), CortesTramo.mover(cortes, 1, 620.0, 990.0))
    }

    @Test
    fun `un corte no pasa de sus vecinos ni de la salida o la meta`() {
        assertEquals(listOf(250.0, 700.0, 750.0), CortesTramo.mover(cortes, 1, 900.0, 990.0))
        assertEquals(listOf(250.0, 300.0, 750.0), CortesTramo.mover(cortes, 1, 10.0, 990.0))
        assertEquals(listOf(50.0, 500.0, 750.0), CortesTramo.mover(cortes, 0, -30.0, 990.0))
        assertEquals(listOf(250.0, 500.0, 940.0), CortesTramo.mover(cortes, 2, 5000.0, 990.0))
    }

    @Test
    fun `si no cabe ningún valor la lista queda igual`() {
        assertEquals(listOf(10.0, 40.0), CortesTramo.mover(listOf(10.0, 40.0), 0, 20.0, 90.0))
    }

    @Test
    fun `microsectores por kilómetro`() {
        assertEquals(emptyList<Double>(), CortesTramo.microsectoresPorKm(3000.0, 0))
        assertEquals(listOf(1000.0, 2000.0), CortesTramo.microsectoresPorKm(3000.0, 1))
        assertEquals(listOf(500.0, 1000.0, 1500.0, 2000.0, 2500.0), CortesTramo.microsectoresPorKm(3000.0, 2))
        assertEquals(11, CortesTramo.microsectoresPorKm(3000.0, 4).size)
        assertThrows(IllegalArgumentException::class.java) { CortesTramo.microsectoresPorKm(3000.0, 5) }
    }

    @Test
    fun `porKmDe recupera el ajuste de los microsectores existentes`() {
        for (n in 0..4) {
            assertEquals(n, CortesTramo.porKmDe(6000.0, CortesTramo.microsectoresPorKm(6000.0, n)))
        }
    }
}
