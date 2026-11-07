package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** Ordinal de la salida contando directamente sobre [RecorridoRotonda], en rotondas de 1 a 4 salidas. */
class RecorridoRotondaTest {
    private val metrosPorGradoLat = 111_194.9
    private val metrosPorGradoLon = metrosPorGradoLat * cos(Math.toRadians(39.6))
    private val radio = 30.0

    private fun p(
        este: Double,
        norte: Double,
    ) = PuntoGeo(39.6 + norte / metrosPorGradoLat, 2.9 + este / metrosPorGradoLon)

    /** Nodo k del anillo (0 = este), cada 90 grados en sentido antihorario: 0 este, 1 norte, 2 oeste, 3 sur. */
    private fun nodo(k: Int) = p(radio * cos(Math.toRadians(90.0 * k)), radio * sin(Math.toRadians(90.0 * k)))

    private fun lejos(k: Int) = p(100.0 * cos(Math.toRadians(90.0 * k)), 100.0 * sin(Math.toRadians(90.0 * k)))

    private fun via(
        id: Long,
        sentido: SentidoUnico,
        junction: String?,
        puntos: List<PuntoGeo>,
    ) = Via(id, "residential", null, null, null, sentido, null, null, puntos, junction = junction)

    private fun rotonda(
        brazos: List<Int>,
        sentido: SentidoUnico = SentidoUnico.NO,
    ): RecorridoRotonda {
        val anillo = via(100, sentido, "roundabout", (0..4).map { nodo(it % 4) })
        val ramales = brazos.map { via(200L + it, SentidoUnico.NO, null, listOf(nodo(it), lejos(it))) }
        return RecorridoRotonda(GrafoVias(listOf(anillo) + ramales))
    }

    @Test
    fun `rotonda de una sola salida la cuenta como primera`() {
        // Un solo ramal en el nodo 3 (sur): entrar y salir por el mismo ramal da la vuelta entera = 1.ª salida.
        val r = rotonda(listOf(3))
        assertEquals(1, r.ordinal(nodo(3), nodo(3), 5.0))
    }

    @Test
    fun `rotonda de dos salidas`() {
        val r = rotonda(listOf(3, 1))
        assertEquals(1, r.ordinal(nodo(3), nodo(1), 5.0))
        assertEquals(2, r.ordinal(nodo(3), nodo(3), 5.0))
        assertEquals(1, r.ordinal(nodo(1), nodo(3), 5.0))
    }

    @Test
    fun `rotonda de tres salidas`() {
        val r = rotonda(listOf(3, 0, 1))
        assertEquals(1, r.ordinal(nodo(3), nodo(0), 5.0))
        assertEquals(2, r.ordinal(nodo(3), nodo(1), 5.0))
        assertEquals(3, r.ordinal(nodo(3), nodo(3), 5.0))
    }

    @Test
    fun `rotonda de cuatro salidas en sentido de dibujo y al reves`() {
        val normal = rotonda(listOf(3, 0, 1, 2))
        assertEquals(listOf(1, 2, 3, 4), listOf(0, 1, 2, 3).map { normal.ordinal(nodo(3), nodo(it), 5.0) })
        // Con oneway=-1 se circula en el sentido contrario: desde el sur van el oeste, el norte, el este y el sur.
        val alReves = rotonda(listOf(3, 0, 1, 2), SentidoUnico.ATRAS)
        assertEquals(listOf(1, 2, 3, 4), listOf(2, 1, 0, 3).map { alReves.ordinal(nodo(3), nodo(it), 5.0) })
    }

    @Test
    fun `fuera de tolerancia o sin anillo no hay ordinal`() {
        val r = rotonda(listOf(3, 0, 1, 2))
        assertNull(r.ordinal(p(400.0, 400.0), nodo(0), 5.0))
        assertNull(r.ordinal(nodo(3), p(400.0, 400.0), 5.0))
        val sinAnillo = RecorridoRotonda(GrafoVias(listOf(via(1, SentidoUnico.NO, null, listOf(nodo(3), lejos(3))))))
        assertNull(sinAnillo.ordinal(nodo(3), nodo(0), 5.0))
    }
}
