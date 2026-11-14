package app.trombito.rallycopilot.motor.ruta

import org.junit.Assert.assertEquals
import org.junit.Test

class FactorPersonalTest {
    private fun viajes(vararg razones: Double) = razones.map { ViajeHistorico(realS = it * 1000.0, previstoS = 1000.0) }

    @Test
    fun `con menos de 3 viajes usa el defecto`() {
        assertEquals(1.12, PerfilRitmo.factorDesdeHistorico(emptyList()), 1e-9)
        assertEquals(1.12, PerfilRitmo.factorDesdeHistorico(viajes(1.3, 1.3)), 1e-9)
    }

    @Test
    fun `es la mediana de real sobre previsto`() {
        assertEquals(1.2, PerfilRitmo.factorDesdeHistorico(viajes(1.3, 1.1, 1.2)), 1e-9)
        // Un viaje raro no mueve la mediana.
        assertEquals(1.2, PerfilRitmo.factorDesdeHistorico(viajes(1.1, 1.2, 1.2, 1.3, 3.0)), 1e-9)
    }

    @Test
    fun `con un numero par promedia los dos centrales`() {
        assertEquals(1.25, PerfilRitmo.factorDesdeHistorico(viajes(1.1, 1.2, 1.3, 1.5)), 1e-9)
    }

    @Test
    fun `se acota a 1,0 y 1,4`() {
        assertEquals(1.0, PerfilRitmo.factorDesdeHistorico(viajes(0.8, 0.9, 0.95)), 1e-9)
        assertEquals(1.4, PerfilRitmo.factorDesdeHistorico(viajes(2.0, 2.0, 2.0)), 1e-9)
    }

    @Test
    fun `los viajes sin previsto o sin tiempo real no cuentan`() {
        val validos = viajes(1.1, 1.2)
        val ruido = listOf(ViajeHistorico(900.0, null), ViajeHistorico(900.0, 0.0), ViajeHistorico(0.0, 900.0))
        assertEquals(1.12, PerfilRitmo.factorDesdeHistorico(validos + ruido), 1e-9)
        assertEquals(1.3, PerfilRitmo.factorDesdeHistorico(viajes(1.3, 1.3, 1.3) + ruido), 1e-9)
    }
}
