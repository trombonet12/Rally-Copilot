package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos

class SeguidorViaTest {
    private val metrosPorGradoLat = 111_194.9
    private val metrosPorGradoLon = metrosPorGradoLat * cos(Math.toRadians(39.6))

    private fun p(
        este: Double,
        norte: Double,
    ) = PuntoGeo(39.6 + norte / metrosPorGradoLat, 2.9 + este / metrosPorGradoLon)

    private fun via(
        id: Long,
        vararg puntos: PuntoGeo,
    ) = Via(id, "secondary", "50", null, null, SentidoUnico.NO, null, null, puntos.toList())

    private val a = via(1, p(-200.0, 0.0), p(200.0, 0.0))
    private val b = via(2, p(-200.0, 10.0), p(200.0, 10.0))

    @Test
    fun `mantiene la via actual hasta que otra quede margen mas cerca`() {
        val indice = IndiceVias(listOf(a, b))
        val seguidor = SeguidorVia(indice, margenMetros = 5.0)
        assertEquals(1L, seguidor.actualizar(p(0.0, 3.0), 90.0)?.via?.id)
        // B queda 2 m más cerca (4 frente a 6): sin histéresis cambiaría.
        assertEquals(2L, indice.cercana(p(10.0, 6.0), 90.0)?.via?.id)
        assertEquals(1L, seguidor.actualizar(p(10.0, 6.0), 90.0)?.via?.id)
        // B queda 8 m más cerca (1 frente a 9).
        assertEquals(2L, seguidor.actualizar(p(20.0, 9.0), 90.0)?.via?.id)
        // Ahora A tendría que ganar por 5 m para recuperarla: a 4 m de A y 6 m de B, A gana solo por 2: se queda en B.
        assertEquals(2L, seguidor.actualizar(p(30.0, 4.0), 90.0)?.via?.id)
        assertEquals(1L, seguidor.actualizar(p(40.0, 0.5), 90.0)?.via?.id)
    }

    @Test
    fun `al salir del radio olvida la via y la siguiente mas cercana manda`() {
        val seguidor = SeguidorVia(IndiceVias(listOf(a, b)))
        assertEquals(1L, seguidor.actualizar(p(0.0, 3.0), 90.0)?.via?.id)
        assertNull(seguidor.actualizar(p(0.0, 500.0), 90.0))
        assertEquals(2L, seguidor.actualizar(p(0.0, 6.0), 90.0)?.via?.id)
    }

    @Test
    fun `devuelve la distancia a la via mantenida y no a la ganadora`() {
        val seguidor = SeguidorVia(IndiceVias(listOf(a, b)))
        assertEquals(2L, seguidor.actualizar(p(0.0, 6.0), 90.0)?.via?.id)
        val c = seguidor.actualizar(p(0.0, 3.5), 90.0)
        assertEquals(2L, c?.via?.id)
        assertEquals(6.5, c!!.distanciaMetros, 0.1)
    }

    @Test
    fun `en un cruce cambia de via al tomar el nuevo rumbo`() {
        val horizontal = via(1, p(-200.0, 0.0), p(200.0, 0.0))
        val vertical = via(3, p(0.0, 0.0), p(0.0, 200.0))
        val seguidor = SeguidorVia(IndiceVias(listOf(horizontal, vertical)))
        assertEquals(1L, seguidor.actualizar(p(-30.0, 1.0), 90.0)?.via?.id)
        assertEquals(1L, seguidor.actualizar(p(-3.0, 1.0), 90.0)?.via?.id)
        // Ya girado al norte, a 4 m del cruce sobre la vertical.
        assertEquals(3L, seguidor.actualizar(p(0.5, 4.0), 0.0)?.via?.id)
        assertEquals(3L, seguidor.actualizar(p(0.5, 40.0), 0.0)?.via?.id)
    }

    private fun viaNivel(
        id: Long,
        clase: String,
        puente: Boolean,
        vararg puntos: PuntoGeo,
    ) = Via(
        id,
        clase,
        null,
        null,
        null,
        SentidoUnico.NO,
        null,
        null,
        puntos.toList(),
        bridge = if (puente) "yes" else null,
        layer = if (puente) 1 else null,
    )

    // Carretera E-O a y=0 y, casi paralelo (5°), un viaducto de autovía que la cruza por encima en el punto (0, -8).
    private val carretera = viaNivel(10, "secondary", false, p(-300.0, 0.0), p(300.0, 0.0))
    private val viaducto = viaNivel(11, "motorway", true, p(-300.0, -35.0), p(300.0, 19.0))

    @Test
    fun `bajo un puente sin nodo compartido se queda en la carretera aunque el puente puntue mejor`() {
        val vias = listOf(carretera, viaducto)
        val seguidor = SeguidorVia(IndiceVias(vias), grafo = GrafoVias(vias))
        assertEquals(10L, seguidor.actualizar(p(-100.0, 0.0), 90.0)?.via?.id)
        // El GPS cae a 8 m de la carretera y a 0 del viaducto, con casi el mismo rumbo: sin nivel cambiaba de vía.
        assertEquals(10L, seguidor.actualizar(p(0.0, -8.0), 90.0)?.via?.id)
        assertEquals(10L, seguidor.actualizar(p(20.0, -8.0), 90.0)?.via?.id)
    }

    @Test
    fun `el nivel tambien cuenta sin grafo`() {
        val seguidor = SeguidorVia(IndiceVias(listOf(carretera, viaducto)))
        assertEquals(10L, seguidor.actualizar(p(-100.0, 0.0), 90.0)?.via?.id)
        assertEquals(10L, seguidor.actualizar(p(0.0, -8.0), 90.0)?.via?.id)
    }

    @Test
    fun `si se empieza sobre el viaducto no se baja a la carretera`() {
        val vias = listOf(carretera, viaducto)
        val seguidor = SeguidorVia(IndiceVias(vias), grafo = GrafoVias(vias))
        assertEquals(11L, seguidor.actualizar(p(0.0, -8.0), 90.0)?.via?.id)
        assertEquals(11L, seguidor.actualizar(p(30.0, -4.5), 90.0)?.via?.id)
    }

    @Test
    fun `al entrar en el puente por un nodo compartido si cambia de via`() {
        val antes = viaNivel(20, "primary", false, p(-300.0, 0.0), p(0.0, 0.0))
        val puente = viaNivel(21, "primary", true, p(0.0, 0.0), p(300.0, 0.0))
        val otra = viaNivel(22, "tertiary", false, p(-300.0, 9.0), p(300.0, 9.0))
        val vias = listOf(antes, puente, otra)
        val seguidor = SeguidorVia(IndiceVias(vias), grafo = GrafoVias(vias))
        assertEquals(20L, seguidor.actualizar(p(-50.0, 0.0), 90.0)?.via?.id)
        assertEquals(21L, seguidor.actualizar(p(60.0, 0.5), 90.0)?.via?.id)
        assertEquals(21L, seguidor.actualizar(p(120.0, 0.5), 90.0)?.via?.id)
    }

    @Test
    fun `una via del mismo nivel sin nodo compartido tambien pesa mas para cambiar`() {
        val a1 = viaNivel(30, "secondary", false, p(-300.0, 0.0), p(300.0, 0.0))
        val b1 = viaNivel(31, "secondary", false, p(-300.0, 12.0), p(300.0, 12.0))
        val vias = listOf(a1, b1)
        val conGrafo = SeguidorVia(IndiceVias(vias), grafo = GrafoVias(vias))
        val sinGrafo = SeguidorVia(IndiceVias(vias))
        assertEquals(30L, conGrafo.actualizar(p(0.0, 2.0), 90.0)?.via?.id)
        assertEquals(30L, sinGrafo.actualizar(p(0.0, 2.0), 90.0)?.via?.id)
        // A 11 m de A y 1 m de B: 10 m de ventaja. Con la penalización de 15 m por no estar conectadas, se queda.
        assertEquals(30L, conGrafo.actualizar(p(10.0, 11.0), 90.0)?.via?.id)
        assertEquals(31L, sinGrafo.actualizar(p(10.0, 11.0), 90.0)?.via?.id)
    }
}
