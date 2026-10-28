package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

/** Fixtures en metros (este, norte) sobre un origen cercano a Palma. */
class IndiceViasTest {
    private val latOrigen = 39.6
    private val lonOrigen = 2.9
    private val metrosPorGradoLat = 111_194.9
    private val metrosPorGradoLon = metrosPorGradoLat * cos(Math.toRadians(latOrigen))

    private fun p(
        este: Double,
        norte: Double,
    ) = PuntoGeo(latOrigen + norte / metrosPorGradoLat, lonOrigen + este / metrosPorGradoLon)

    private fun via(
        id: Long,
        vararg puntos: PuntoGeo,
    ) = Via(id, "secondary", "50", null, null, SentidoUnico.NO, null, null, puntos.toList())

    private val oesteEste = via(1, p(-100.0, 0.0), p(100.0, 0.0))
    private val paralela = via(2, p(-100.0, 10.0), p(100.0, 10.0))
    private val nortePorElCruce = via(3, p(0.0, 0.0), p(0.0, 100.0))

    @Test
    fun `entre dos vias paralelas a 10 m elige la mas cercana`() {
        val indice = IndiceVias(listOf(oesteEste, paralela))
        val a = indice.cercana(p(0.0, 3.0), 90.0)
        assertEquals(1L, a?.via?.id)
        assertEquals(3.0, a!!.distanciaMetros, 0.1)
        val b = indice.cercana(p(0.0, 7.0), 90.0)
        assertEquals(2L, b?.via?.id)
        assertEquals(3.0, b!!.distanciaMetros, 0.1)
        assertEquals(2L, indice.cercana(p(0.0, 7.0), null)?.via?.id)
    }

    @Test
    fun `en un cruce en T el rumbo decide aunque la otra via quede mas cerca`() {
        val indice = IndiceVias(listOf(oesteEste, nortePorElCruce))
        val punto = p(2.0, 3.0) // 2 m de la vertical, 3 m de la horizontal
        assertEquals(3L, indice.cercana(punto, null)?.via?.id)
        assertEquals(1L, indice.cercana(punto, 90.0)?.via?.id)
        assertEquals(1L, indice.cercana(punto, 270.0)?.via?.id)
        assertEquals(3L, indice.cercana(punto, 0.0)?.via?.id)
        assertEquals(3L, indice.cercana(punto, 180.0)?.via?.id)
    }

    @Test
    fun `adelante indica si la marcha sigue el sentido de dibujo`() {
        val indice = IndiceVias(listOf(oesteEste))
        assertTrue(indice.cercana(p(0.0, 1.0), 90.0)!!.adelante)
        assertFalse(indice.cercana(p(0.0, 1.0), 270.0)!!.adelante)
        assertTrue(indice.cercana(p(0.0, 1.0), 80.0)!!.adelante)
        assertFalse(indice.cercana(p(0.0, 1.0), 300.0)!!.adelante)
        assertTrue(indice.cercana(p(0.0, 1.0), 450.0)!!.adelante) // 450 = 90 más una vuelta
        assertTrue(indice.cercana(p(0.0, 1.0), null)!!.adelante)
    }

    @Test
    fun `fuera del radio no hay coincidencia`() {
        val indice = IndiceVias(listOf(oesteEste))
        assertNull(indice.cercana(p(0.0, 30.0), 90.0))
        assertNull(indice.cercana(p(0.0, 26.0), null))
        assertEquals(1L, indice.cercana(p(0.0, 24.0), null)?.via?.id)
        assertEquals(1L, indice.cercana(p(0.0, 30.0), null, radioMetros = 40.0)?.via?.id)
        assertNull(indice.cercana(p(500.0, 0.0), null))
        assertNull(IndiceVias(emptyList()).cercana(p(0.0, 0.0), null))
    }

    @Test
    fun `mide la distancia al segmento y no a la recta ni a los vertices`() {
        val indice = IndiceVias(listOf(oesteEste))
        // Pasado el extremo: la distancia es al vértice (100,0).
        assertEquals(5.0, indice.cercana(p(103.0, 4.0), null)!!.distanciaMetros, 0.1)
        // Un segmento largo cruza muchas celdas de 100 m.
        val larga = via(9, p(0.0, 0.0), p(1000.0, 800.0))
        val c = IndiceVias(listOf(larga)).cercana(p(500.0, 400.0), null)
        assertNotNull(c)
        assertEquals(0.0, c!!.distanciaMetros, 0.1)
    }

    @Test
    fun `un segmento de longitud cero no rompe el indice`() {
        val degenerada = via(7, p(0.0, 0.0), p(0.0, 0.0))
        assertEquals(7L, IndiceVias(listOf(degenerada)).cercana(p(0.0, 2.0), 45.0)?.via?.id)
    }

    @Test
    fun `rendimiento con 40000 vias sinteticas y 1000 consultas bajo un segundo`() {
        val vias =
            (0 until 40_000).map { i ->
                val lat = 39.3 + (i / 200) * 0.003
                val lon = 2.3 + (i % 200) * 0.004
                Via(
                    i.toLong(),
                    "tertiary",
                    "50",
                    null,
                    null,
                    SentidoUnico.NO,
                    null,
                    null,
                    (0 until 10).map { PuntoGeo(lat + it * 0.00005 * (it % 2), lon + it * 0.0003) },
                )
            }
        val indice = IndiceVias(vias)
        val consultas =
            (0 until 1000).map { k ->
                val v = vias[k * 40]
                PuntoGeo(v.puntos[3].lat + 0.00002, v.puntos[3].lon + 0.00002)
            }
        val inicio = System.nanoTime()
        val aciertos = consultas.count { indice.cercana(it, 90.0) != null }
        val ms = (System.nanoTime() - inicio) / 1_000_000
        assertEquals(1000, aciertos)
        assertTrue("1000 consultas tardaron $ms ms", ms < 1000)
    }
}
