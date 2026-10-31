package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class GrafoViasTest {
    private val metrosPorGradoLat = 111_194.9
    private val metrosPorGradoLon = metrosPorGradoLat * cos(Math.toRadians(39.6))

    private fun p(
        este: Double,
        norte: Double,
    ) = PuntoGeo(39.6 + norte / metrosPorGradoLat, 2.9 + este / metrosPorGradoLon)

    private fun via(
        id: Long,
        clase: String,
        vararg puntos: PuntoGeo,
    ) = Via(id, clase, null, null, null, SentidoUnico.NO, null, null, puntos.toList())

    @Test
    fun `agrupa por nodo las vias que comparten coordenada`() {
        val a = via(1, "secondary", p(0.0, 0.0), p(100.0, 0.0), p(200.0, 0.0))
        val b = via(2, "secondary", p(100.0, 0.0), p(100.0, 100.0))
        val grafo = GrafoVias(listOf(a, b))
        val enNodo = grafo.aristas(p(100.0, 0.0))
        assertEquals(setOf(1L to 1, 2L to 0), enNodo.map { it.via.id to it.indice }.toSet())
        assertEquals(1, grafo.aristas(p(0.0, 0.0)).size)
        assertTrue(grafo.aristas(p(5000.0, 5000.0)).isEmpty())
    }

    @Test
    fun `construye 33000 vias en una cuadricula en menos de un segundo`() {
        val lado = 129
        val vias = ArrayList<Via>()
        var id = 0L
        for (i in 0 until lado) {
            for (j in 0 until lado - 1) {
                vias.add(via(id++, "residential", p(j * 100.0, i * 100.0), p((j + 1) * 100.0, i * 100.0)))
                vias.add(via(id++, "residential", p(i * 100.0, j * 100.0), p(i * 100.0, (j + 1) * 100.0)))
            }
        }
        assertTrue(vias.size >= 33_000)
        val inicio = System.nanoTime()
        val grafo = GrafoVias(vias)
        val ms = (System.nanoTime() - inicio) / 1_000_000
        assertTrue("construcción: $ms ms", ms < 1000)
        assertEquals(4, grafo.aristas(p(5 * 100.0, 5 * 100.0)).size)
        assertEquals(2, grafo.aristas(p(0.0, 0.0)).size)
    }

    private fun principal(
        clase: String,
        vararg puntos: PuntoGeo,
    ) = via(1, clase, *puntos)

    @Test
    fun `cruces sobre una polilinea segun el rango de las ramas`() {
        val principal =
            principal("secondary", p(-1000.0, 0.0), p(-500.0, 0.0), p(0.0, 0.0), p(500.0, 0.0), p(1000.0, 0.0))
        val servicio = via(2, "service", p(-500.0, 0.0), p(-500.0, 200.0))
        val terciaria = via(3, "tertiary", p(0.0, 0.0), p(0.0, -300.0))
        val secundaria = via(4, "secondary", p(500.0, 0.0), p(500.0, 300.0))
        val grafo = GrafoVias(listOf(principal, servicio, terciaria, secundaria))
        val polilinea = listOf(p(-1000.0, 0.0), p(-250.0, 0.0), p(1000.0, 0.0))
        val cruces = grafo.cruces(polilinea)
        assertEquals(2, cruces.size)
        assertEquals(1000.0, cruces[0], 1.0)
        assertEquals(1500.0, cruces[1], 1.0)
    }

    @Test
    fun `una rama residential o unclassified ya cuenta como cruce`() {
        val principal = principal("secondary", p(-500.0, 0.0), p(0.0, 0.0), p(500.0, 0.0))
        val residencial = via(2, "residential", p(0.0, 0.0), p(0.0, 200.0))
        val cruces = GrafoVias(listOf(principal, residencial)).cruces(listOf(p(-500.0, 0.0), p(500.0, 0.0)))
        assertEquals(listOf(500.0), cruces.map { Math.round(it).toDouble() })
    }

    @Test
    fun `un ramal de servicio solo es cruce si la ruta gira en el nodo`() {
        val principal = principal("secondary", p(-500.0, 0.0), p(0.0, 0.0), p(500.0, 0.0))
        val servicio = via(2, "service", p(0.0, 0.0), p(0.0, 200.0))
        val grafo = GrafoVias(listOf(principal, servicio))
        assertTrue(grafo.cruces(listOf(p(-500.0, 0.0), p(500.0, 0.0))).isEmpty())
        // Gira 90 grados hacia el ramal.
        assertEquals(
            listOf(500.0),
            grafo.cruces(listOf(p(-500.0, 0.0), p(0.0, 0.0), p(0.0, 200.0))).map {
                Math.round(it).toDouble()
            },
        )
        // Un giro de 20 grados no basta.
        val suave = listOf(p(-500.0, 0.0), p(0.0, 0.0), p(500.0, 182.0))
        assertTrue(grafo.cruces(suave).isEmpty())
        // Uno de 45 grados si.
        assertEquals(1, grafo.cruces(listOf(p(-500.0, 0.0), p(0.0, 0.0), p(500.0, 500.0))).size)
    }

    @Test
    fun `los cruces a menos de 50 m se reducen a uno`() {
        val principal =
            principal("secondary", p(-500.0, 0.0), p(100.0, 0.0), p(130.0, 0.0), p(300.0, 0.0), p(600.0, 0.0))
        val a = via(2, "tertiary", p(100.0, 0.0), p(100.0, 200.0))
        val b = via(3, "tertiary", p(130.0, 0.0), p(130.0, 200.0))
        val c = via(4, "tertiary", p(300.0, 0.0), p(300.0, 200.0))
        val cruces = GrafoVias(listOf(principal, a, b, c)).cruces(listOf(p(-500.0, 0.0), p(600.0, 0.0)))
        assertEquals(listOf(600.0, 800.0), cruces.map { Math.round(it).toDouble() })
    }

    @Test
    fun `en una via menor cualquier ramal cuenta como cruce`() {
        val principal = principal("residential", p(-500.0, 0.0), p(0.0, 0.0), p(500.0, 0.0))
        val ramal = via(2, "residential", p(0.0, 0.0), p(0.0, 200.0))
        val cruces = GrafoVias(listOf(principal, ramal)).cruces(listOf(p(-500.0, 0.0), p(500.0, 0.0)))
        assertEquals(listOf(500.0), cruces.map { Math.round(it).toDouble() })
    }

    @Test
    fun `dos vias que se encadenan sin ramal no son un cruce`() {
        val a = via(1, "primary", p(-500.0, 0.0), p(0.0, 0.0))
        val b = via(2, "primary", p(0.0, 0.0), p(500.0, 0.0))
        assertTrue(GrafoVias(listOf(a, b)).cruces(listOf(p(-500.0, 0.0), p(500.0, 0.0))).isEmpty())
    }

    @Test
    fun `un ramal que acaba en el nodo de dos vias encadenadas si es cruce`() {
        val a = via(1, "primary", p(-500.0, 0.0), p(0.0, 0.0))
        val b = via(2, "primary", p(0.0, 0.0), p(500.0, 0.0))
        val c = via(3, "primary", p(0.0, 0.0), p(0.0, 400.0))
        val cruces = GrafoVias(listOf(a, b, c)).cruces(listOf(p(-500.0, 0.0), p(500.0, 0.0)))
        assertEquals(1, cruces.size)
        assertEquals(500.0, cruces[0], 1.0)
    }

    @Test
    fun `respeta la tolerancia y no duplica un cruce visto desde dos segmentos`() {
        val principal = principal("secondary", p(-500.0, 0.0), p(0.0, 0.0), p(500.0, 0.0))
        val ramal = via(2, "secondary", p(0.0, 0.0), p(0.0, 200.0))
        val grafo = GrafoVias(listOf(principal, ramal))
        // Con un vértice justo en el nodo, ambos segmentos lo ven.
        assertEquals(1, grafo.cruces(listOf(p(-500.0, 0.0), p(0.0, 0.0), p(500.0, 0.0))).size)
        // A 10 m de distancia queda fuera con la tolerancia por defecto y dentro con 15 m.
        val desviada = listOf(p(-500.0, 10.0), p(500.0, 10.0))
        assertTrue(grafo.cruces(desviada).isEmpty())
        assertEquals(1, grafo.cruces(desviada, toleranciaM = 15.0).size)
    }
}
