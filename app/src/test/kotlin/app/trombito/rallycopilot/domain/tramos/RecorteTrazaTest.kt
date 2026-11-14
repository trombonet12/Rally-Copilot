package app.trombito.rallycopilot.domain.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecorteTrazaTest {
    private val traza = listOf(PuntoGeo(39.70, 2.70), PuntoGeo(39.70, 2.72), PuntoGeo(39.72, 2.72))

    @Test
    fun `las distancias son acumuladas y crecientes`() {
        val puntos = RecorteTraza.conDistancias(traza)

        assertEquals(0.0, puntos.first().distanciaM, 0.0)
        assertTrue(puntos.zipWithNext().all { (a, b) -> a.distanciaM < b.distanciaM })
        assertEquals(traza[0].distanciaA(traza[1]) + traza[1].distanciaA(traza[2]), puntos.last().distanciaM, 1e-6)
    }

    @Test
    fun `el trozo se remuestrea desde cero y respeta los extremos`() {
        val total = RecorteTraza.conDistancias(traza).last().distanciaM

        val trozo = RecorteTraza.subpolilinea(traza, 500.0, total - 500.0, pasoM = 100.0)

        assertEquals(0.0, trozo.first().distanciaM, 0.0)
        assertEquals(total - 1_000.0, trozo.last().distanciaM, 1e-6)
        assertTrue(trozo.zipWithNext().all { (a, b) -> b.distanciaM - a.distanciaM <= 100.0 + 1e-6 })
        assertEquals(RecorteTraza.conDistancias(traza)[0].punto.lat, 39.70, 0.0)
    }

    @Test
    fun `un trozo vacio o fuera de la traza no da puntos`() {
        assertTrue(RecorteTraza.subpolilinea(traza, 100.0, 100.0).isEmpty())
        assertTrue(RecorteTraza.subpolilinea(traza, 1e9, 2e9).isEmpty())
        assertTrue(RecorteTraza.subpolilinea(listOf(traza[0]), 0.0, 10.0).isEmpty())
    }
}
