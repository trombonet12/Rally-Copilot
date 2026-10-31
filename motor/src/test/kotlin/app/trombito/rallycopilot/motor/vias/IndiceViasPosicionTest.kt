package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos

/** [Coincidencia.posicionEnViaM]: metros desde el primer punto de la vía hasta la proyección. */
class IndiceViasPosicionTest {
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
    ) = Via(id, "secondary", null, null, null, SentidoUnico.NO, null, null, puntos.toList())

    @Test
    fun `en una recta la posicion es la distancia desde el primer punto`() {
        val indice = IndiceVias(listOf(via(1, p(0.0, 0.0), p(200.0, 0.0))))
        assertEquals(0.0, indice.cercana(p(0.0, 2.0), null)!!.posicionEnViaM, 0.5)
        assertEquals(120.0, indice.cercana(p(120.0, 5.0), 90.0)!!.posicionEnViaM, 0.5)
        assertEquals(200.0, indice.cercana(p(210.0, 0.0), null)!!.posicionEnViaM, 0.5)
    }

    @Test
    fun `en una via con codo acumula los segmentos anteriores`() {
        val indice = IndiceVias(listOf(via(1, p(0.0, 0.0), p(100.0, 0.0), p(100.0, 100.0))))
        assertEquals(150.0, indice.cercana(p(98.0, 50.0), 0.0)!!.posicionEnViaM, 0.5)
    }

    @Test
    fun `la posicion no depende del sentido de la marcha`() {
        val indice = IndiceVias(listOf(via(1, p(0.0, 0.0), p(200.0, 0.0))))
        val ida = indice.cercana(p(80.0, 1.0), 90.0)!!
        val vuelta = indice.cercana(p(80.0, 1.0), 270.0)!!
        assertEquals(80.0, ida.posicionEnViaM, 0.5)
        assertEquals(ida.posicionEnViaM, vuelta.posicionEnViaM, 1e-9)
    }

    @Test
    fun `con varias vias mide sobre la elegida`() {
        val indice = IndiceVias(listOf(via(1, p(-100.0, 0.0), p(100.0, 0.0)), via(2, p(0.0, 50.0), p(0.0, 150.0))))
        val c = indice.cercana(p(1.0, 70.0), 0.0)!!
        assertEquals(2L, c.via.id)
        assertEquals(20.0, c.posicionEnViaM, 0.5)
    }
}
