package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.math.cos

/**
 * Autopista (viaducto, nivel 1) recta de oeste a este con dos calles de nivel 0 que pasan por debajo: una perpendicular
 * y otra a 30°. Sin nodos compartidos con la autopista. El coche va por la autopista y nunca debe saltar a ellas.
 */
class SeguidorViaAutopistaTest {
    private val metrosPorGradoLat = 111_194.9
    private val metrosPorGradoLon = metrosPorGradoLat * cos(Math.toRadians(39.6))

    private fun p(
        este: Double,
        norte: Double,
    ) = PuntoGeo(39.6 + norte / metrosPorGradoLat, 2.9 + este / metrosPorGradoLon)

    private fun via(
        id: Long,
        puente: Boolean,
        sentido: SentidoUnico = SentidoUnico.NO,
        vararg puntos: PuntoGeo,
    ) = Via(
        id,
        if (puente) "motorway" else "residential",
        null,
        null,
        null,
        sentido,
        null,
        null,
        puntos.toList(),
        bridge = if (puente) "yes" else null,
        layer = if (puente) 1 else null,
    )

    private val autopista = via(1, true, SentidoUnico.NO, p(-600.0, 0.0), p(600.0, 0.0))
    private val perpendicular = via(2, false, SentidoUnico.NO, p(0.0, -150.0), p(0.0, 150.0))
    private val oblicua = via(3, false, SentidoUnico.NO, p(-300.0, -173.0), p(300.0, 173.0))
    private val vias = listOf(autopista, perpendicular, oblicua)

    private fun seguidor() = SeguidorVia(IndiceVias(vias), grafo = GrafoVias(vias))

    private fun SeguidorVia.recorrer(
        rumbo: (Int) -> Double?,
        deriva: (Double) -> Double = { 0.0 },
    ): List<Long?> =
        (-100..100 step 10).mapIndexed { i, x ->
            val coincidencia = actualizar(p(x.toDouble(), deriva(x.toDouble())), rumbo(i))
            coincidencia?.via?.id
        }

    private fun assertNuncaCalle(ids: List<Long?>) {
        ids.forEach { assertNotEquals("Saltó a una calle de debajo: $ids", 2L, it) }
        ids.forEach { assertNotEquals("Saltó a una calle de debajo: $ids", 3L, it) }
    }

    @Test
    fun `sin rumbo y con la posicion en planta justo sobre la calle sigue en la autopista`() {
        val seguidor = seguidor()
        assertEquals(1L, seguidor.actualizar(p(-120.0, 0.0), null)?.via?.id)
        assertNuncaCalle(seguidor.recorrer({ null }))
    }

    @Test
    fun `sin rumbo y con el GPS desviado mas de 25 m de la autopista no salta a la calle`() {
        val seguidor = seguidor()
        assertEquals(1L, seguidor.actualizar(p(-120.0, 0.0), null)?.via?.id)
        // Dos fijaciones desviadas 30 m al norte justo al cruzar: solo la calle queda dentro del radio.
        seguidor.actualizar(p(-130.0, 3.0), null)
        assertNuncaCalle(listOf(seguidor.actualizar(p(-10.0, 30.0), null)?.via?.id))
        assertNuncaCalle(listOf(seguidor.actualizar(p(5.0, 30.0), null)?.via?.id))
        assertEquals(1L, seguidor.actualizar(p(30.0, 3.0), null)?.via?.id)
    }

    @Test
    fun `con rumbo ruidoso no salta a ninguna calle`() {
        val seguidor = seguidor()
        assertEquals(1L, seguidor.actualizar(p(-120.0, 0.0), 90.0)?.via?.id)
        val ruidoso = doubleArrayOf(55.0, 120.0, 70.0, 110.0, 50.0, 125.0, 65.0, 115.0, 60.0, 130.0, 75.0)
        assertNuncaCalle(seguidor.recorrer({ ruidoso[it % ruidoso.size] }))
    }

    @Test
    fun `con buen rumbo y deriva de 30 m no salta a ninguna calle`() {
        val seguidor = seguidor()
        assertEquals(1L, seguidor.actualizar(p(-120.0, 0.0), 90.0)?.via?.id)
        assertNuncaCalle(seguidor.recorrer({ 90.0 }, deriva = { x -> if (x in -20.0..20.0) 30.0 else 2.0 }))
    }

    @Test
    fun `una calle perpendicular no cuenta cuando se va rapido y el rumbo es conocido`() {
        val indice = IndiceVias(listOf(autopista, perpendicular))
        // A 30 m al norte de la autopista solo la perpendicular queda en el radio, y no lleva el rumbo (90°).
        assertEquals(null, indice.buscar(p(10.0, 30.0), 90.0, 25.0, null, 45.0))
        // Sin filtro, la perpendicular gana (comportamiento anterior, el de `cercana`).
        val sinFiltro = indice.buscar(p(10.0, 30.0), 90.0, 25.0, null)
        assertEquals(2L, sinFiltro?.coincidencia?.via?.id)
    }

    @Test
    fun `una salida real por un enlace conectado cambia de via`() {
        val enlace = via(5, false, SentidoUnico.ADELANTE, p(0.0, 0.0), p(200.0, -60.0))
        val conEnlace = listOf(via(1, false, SentidoUnico.NO, p(-600.0, 0.0), p(0.0, 0.0), p(600.0, 0.0)), enlace)
        val seguidor = SeguidorVia(IndiceVias(conEnlace), grafo = GrafoVias(conEnlace))
        assertEquals(1L, seguidor.actualizar(p(-50.0, 0.0), 90.0)?.via?.id)
        val ids =
            listOf(25.0 to -7.5, 60.0 to -18.0, 100.0 to -30.0, 140.0 to -42.0).map { (x, y) ->
                seguidor.actualizar(p(x, y), 107.0)?.via?.id
            }
        assertEquals(5L, ids.last())
    }

    @Test
    fun `una salida a una via de otro nivel sin conexion cambia solo tras tres fijaciones seguidas`() {
        // Tras la salida real (la autopista queda a mas de 25 m) la unica via cercana es la calle de nivel 0.
        val salida = via(6, false, SentidoUnico.NO, p(-100.0, 40.0), p(400.0, 40.0))
        val conSalida = listOf(autopista, salida)
        val seguidor = SeguidorVia(IndiceVias(conSalida), grafo = GrafoVias(conSalida))
        assertEquals(1L, seguidor.actualizar(p(-80.0, 0.0), 90.0)?.via?.id)
        assertEquals(1L, seguidor.actualizar(p(-40.0, 38.0), 90.0)?.via?.id)
        assertEquals(1L, seguidor.actualizar(p(0.0, 40.0), 90.0)?.via?.id)
        assertEquals(6L, seguidor.actualizar(p(40.0, 40.0), 90.0)?.via?.id)
    }

    @Test
    fun `un candidato que se pierde a mitad reinicia la cuenta`() {
        val salida = via(6, false, SentidoUnico.NO, p(-100.0, 40.0), p(400.0, 40.0))
        val conSalida = listOf(autopista, salida)
        val seguidor = SeguidorVia(IndiceVias(conSalida), grafo = GrafoVias(conSalida))
        assertEquals(1L, seguidor.actualizar(p(-80.0, 0.0), 90.0)?.via?.id)
        assertEquals(1L, seguidor.actualizar(p(-40.0, 38.0), 90.0)?.via?.id)
        assertEquals(1L, seguidor.actualizar(p(0.0, 40.0), 90.0)?.via?.id)
        // Vuelve a la autopista: la cuenta se reinicia.
        assertEquals(1L, seguidor.actualizar(p(10.0, 2.0), 90.0)?.via?.id)
        assertEquals(1L, seguidor.actualizar(p(40.0, 40.0), 90.0)?.via?.id)
        assertEquals(1L, seguidor.actualizar(p(80.0, 40.0), 90.0)?.via?.id)
    }
}
