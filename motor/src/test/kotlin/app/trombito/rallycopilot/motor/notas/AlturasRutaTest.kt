package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.PerfilVia
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

/** Vías falsas rectas de oeste a este con perfil lineal, sobre un origen cercano a Palma. */
class AlturasRutaTest {
    private val latOrigen = 39.6
    private val lonOrigen = 2.9
    private val metrosPorGradoLat = 111_194.9
    private val metrosPorGradoLon = metrosPorGradoLat * cos(Math.toRadians(latOrigen))

    private fun p(
        este: Double,
        norte: Double = 0.0,
    ) = PuntoGeo(latOrigen + norte / metrosPorGradoLat, lonOrigen + este / metrosPorGradoLon)

    private fun via(
        id: Long,
        desde: Double,
        hasta: Double,
    ) = Via(id, "secondary", null, null, null, SentidoUnico.NO, null, null, listOf(p(desde), p(hasta)))

    /** Perfil de una vía de [largoM] m (múltiplo de 5) que va de [h0] a [h1] metros de altura. */
    private fun perfil(
        largoM: Int,
        h0: Double,
        h1: Double,
    ) = PerfilVia(5.0, (0..largoM / 5).map { h0 + (h1 - h0) * it * 5 / largoM })

    private val tol = 0.3

    @Test
    fun `dos vias consecutivas con perfil lineal dan alturas cada 5 m`() {
        val indice = IndiceVias(listOf(via(1, 0.0, 100.0), via(2, 100.0, 200.0)))
        val perfiles = mapOf(1L to perfil(100, 0.0, 5.0), 2L to perfil(100, 5.0, 0.0))
        val ruta = listOf(p(0.0), p(100.0), p(200.0))
        val alturas = AlturasRuta.calcular(ruta, indice, perfiles)
        assertEquals(41, alturas.size)
        for (k in alturas.indices) {
            val s = k * 5.0
            val esperada = if (s <= 100.0) 0.05 * s else 5.0 - 0.05 * (s - 100.0)
            assertEquals("s=$s", esperada, alturas[k], tol)
        }
    }

    @Test
    fun `recorrer la via al reves lee el mismo perfil`() {
        val indice = IndiceVias(listOf(via(1, 0.0, 100.0)))
        val alturas = AlturasRuta.calcular(listOf(p(100.0), p(0.0)), indice, mapOf(1L to perfil(100, 0.0, 5.0)))
        assertEquals(21, alturas.size)
        assertEquals(5.0, alturas.first(), tol)
        assertEquals(2.5, alturas[10], tol)
        assertEquals(0.0, alturas.last(), tol)
    }

    @Test
    fun `la ultima muestra del perfil cae en la longitud real de la via`() {
        // Vía de 102 m: muestras en 0..100 cada 5 m y la final (alt. 5,1) en 102 m.
        val indice = IndiceVias(listOf(via(1, 0.0, 102.0)))
        val perfil = PerfilVia(5.0, (0..20).map { 0.05 * it * 5 } + 5.1)
        val alturas = AlturasRuta.calcular(listOf(p(0.0), p(102.0)), indice, mapOf(1L to perfil))
        assertEquals(5.0, alturas[20], tol)
        assertEquals(5.1, alturas.last(), tol)
    }

    @Test
    fun `las muestras sin via a menos de 25 m se interpolan entre las validas`() {
        val indice = IndiceVias(listOf(via(1, 0.0, 100.0), via(2, 205.0, 305.0)))
        val perfiles = mapOf(1L to perfil(100, 0.0, 5.0), 2L to perfil(100, 10.0, 15.0))
        val alturas = AlturasRuta.calcular(listOf(p(0.0), p(305.0)), indice, perfiles)
        // Válidas hasta s = 125 (altura 5) y desde s = 180 (altura 10): entre medias, recta.
        val s150 = 5.0 + 5.0 * (150.0 - 125.0) / (180.0 - 125.0)
        assertEquals(s150, alturas[30], tol)
        assertTrue(alturas.zipWithNext().all { (a, b) -> b >= a - 1e-9 })
        assertEquals(15.0, alturas.last(), tol)
    }

    @Test
    fun `un tramo de la via sin perfil tambien se interpola`() {
        val indice = IndiceVias(listOf(via(1, 0.0, 100.0), via(2, 100.0, 200.0), via(3, 200.0, 300.0)))
        val perfiles = mapOf(1L to perfil(100, 0.0, 10.0), 3L to perfil(100, 20.0, 30.0))
        val alturas = AlturasRuta.calcular(listOf(p(0.0), p(300.0)), indice, perfiles)
        assertEquals(10.0, alturas[20], tol)
        assertEquals(15.0, alturas[30], 1.0)
        assertEquals(20.0, alturas[40], tol)
        assertEquals(30.0, alturas.last(), tol)
    }

    @Test
    fun `los extremos sin via repiten la valida mas proxima`() {
        val indice = IndiceVias(listOf(via(1, 0.0, 100.0)))
        val alturas = AlturasRuta.calcular(listOf(p(-100.0), p(200.0)), indice, mapOf(1L to perfil(100, 4.0, 6.0)))
        assertEquals(4.0, alturas.first(), tol)
        assertEquals(6.0, alturas.last(), tol)
    }

    @Test
    fun `sin ninguna muestra valida o sin ruta la lista es vacia`() {
        val indice = IndiceVias(listOf(via(1, 0.0, 100.0)))
        val perfiles = mapOf(1L to perfil(100, 0.0, 5.0))
        assertTrue(AlturasRuta.calcular(listOf(p(0.0), p(100.0)), indice, emptyMap()).isEmpty())
        val lejos = listOf(p(0.0, 500.0), p(100.0, 500.0))
        assertTrue(AlturasRuta.calcular(lejos, indice, perfiles).isEmpty())
        assertTrue(AlturasRuta.calcular(listOf(p(0.0)), indice, perfiles).isEmpty())
        assertTrue(AlturasRuta.calcular(emptyList(), indice, perfiles).isEmpty())
    }
}
