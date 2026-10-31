package app.trombito.rallycopilot.motor.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class AcumuladorRecorridoTest {
    private val metrosPorGradoLat = 111_194.9

    private fun p(
        este: Double,
        norte: Double,
    ) = PuntoGeo(
        39.6 + norte / metrosPorGradoLat,
        2.9 + este / (metrosPorGradoLat * cos(Math.toRadians(39.6))),
    )

    @Test
    fun `el primer punto se graba y no suma`() {
        val acumulador = AcumuladorRecorrido()
        assertTrue(acumulador.anadir(p(0.0, 0.0)))
        assertEquals(0.0, acumulador.distanciaM, 0.0)
    }

    @Test
    fun `un paso de al menos 5 m se graba y suma`() {
        val acumulador = AcumuladorRecorrido()
        acumulador.anadir(p(0.0, 0.0))
        assertTrue(acumulador.anadir(p(0.0, 10.0)))
        assertTrue(acumulador.anadir(p(0.0, 15.0)))
        assertEquals(15.0, acumulador.distanciaM, 0.1)
    }

    @Test
    fun `un paso menor de 5 m se descarta y se mide desde el ultimo grabado`() {
        val acumulador = AcumuladorRecorrido()
        acumulador.anadir(p(0.0, 0.0))
        assertFalse(acumulador.anadir(p(0.0, 2.0)))
        assertFalse(acumulador.anadir(p(0.0, 4.0)))
        // 6 m desde el último grabado (origen), aunque solo 2 m desde el descartado anterior.
        assertTrue(acumulador.anadir(p(0.0, 6.0)))
        assertEquals(6.0, acumulador.distanciaM, 0.1)
    }

    @Test
    fun `sesenta fijaciones paradas con ruido de 3 m graban un punto y 0 m`() {
        val acumulador = AcumuladorRecorrido()
        // El primer punto fija la referencia; el resto salta ±3 m a su alrededor (siempre a < 5 m de ella).
        val ruido = listOf(0.0 to 0.0, 3.0 to 0.0, -3.0 to 0.0, 0.0 to 3.0, 0.0 to -3.0, 2.0 to -2.0)
        val grabados = List(60) { i -> acumulador.anadir(p(ruido[i % ruido.size].first, ruido[i % ruido.size].second)) }
        assertEquals(1, grabados.count { it })
        assertEquals(0.0, acumulador.distanciaM, 0.0)
    }

    @Test
    fun `la regla sobre una lista da lo mismo que el acumulador`() {
        val puntos = listOf(0.0, 3.0, 10.0, 12.0, 20.0, 21.0, 21.0, 30.0).map { p(0.0, it) }
        val acumulador = AcumuladorRecorrido()
        puntos.forEach { acumulador.anadir(it) }
        assertEquals(acumulador.distanciaM, puntos.distanciaRecorrido(), 0.0)
        assertEquals(30.0, puntos.distanciaRecorrido(), 0.1)
    }

    @Test
    fun `aplicar la regla a puntos ya filtrados no cambia la distancia`() {
        val bruto = List(40) { p(0.0, it * 2.0) }
        val acumulador = AcumuladorRecorrido()
        val grabados = bruto.filter { acumulador.anadir(it) }
        assertEquals(acumulador.distanciaM, grabados.distanciaRecorrido(), 1e-9)
        assertEquals(grabados.longitudMetros(), grabados.distanciaRecorrido(), 1e-9)
    }

    @Test
    fun `una lista vacia o de un punto mide 0 m`() {
        assertEquals(0.0, emptyList<PuntoGeo>().distanciaRecorrido(), 0.0)
        assertEquals(0.0, listOf(p(0.0, 0.0)).distanciaRecorrido(), 0.0)
    }
}
