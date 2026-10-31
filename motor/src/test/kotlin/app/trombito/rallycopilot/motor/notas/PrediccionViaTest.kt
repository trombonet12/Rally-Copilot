package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.vias.GrafoVias
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class PrediccionViaTest {
    private val metrosPorGradoLat = 111_194.9
    private val metrosPorGradoLon = metrosPorGradoLat * cos(Math.toRadians(39.6))

    private fun p(
        este: Double,
        norte: Double,
    ) = PuntoGeo(39.6 + norte / metrosPorGradoLat, 2.9 + este / metrosPorGradoLon)

    private fun via(
        id: Long,
        ref: String?,
        vararg puntos: PuntoGeo,
        sentido: SentidoUnico = SentidoUnico.NO,
    ) = Via(id, "secondary", "50", null, null, sentido, ref, null, puntos.toList())

    private fun predecir(
        vias: List<Via>,
        desde: Via,
        posicionM: Double,
        adelante: Boolean = true,
        maxM: Double = 2000.0,
    ) = PrediccionVia(GrafoVias(vias)).predecir(desde, posicionM, adelante, maxM)

    private fun assertCerca(
        esperado: PuntoGeo,
        real: PuntoGeo,
    ) = assertTrue("$esperado != $real", esperado.distanciaA(real) < 0.5)

    @Test
    fun `en una T sigue el ref aunque el otro ramal vaya recto`() {
        val entrada = via(1, "Ma-1", p(-1000.0, 0.0), p(0.0, 0.0))
        val sigueRef = via(2, "Ma-1", p(0.0, 0.0), p(900.0, 520.0))
        val recto = via(3, "Ma-2", p(0.0, 0.0), p(1000.0, 0.0))
        val r = predecir(listOf(entrada, sigueRef, recto), entrada, 500.0)
        assertCerca(p(900.0, 520.0), r.puntos.last())
        assertFalse(r.cortadaEnCruceDudoso)
        assertEquals(listOf("Ma-1"), r.refs)
    }

    @Test
    fun `una Y simetrica sin ref corta en el cruce`() {
        val entrada = via(1, null, p(-1000.0, 0.0), p(0.0, 0.0))
        val izquierda = via(2, null, p(0.0, 0.0), p(1000.0, 466.0))
        val derecha = via(3, null, p(0.0, 0.0), p(1000.0, -466.0))
        val r = predecir(listOf(entrada, izquierda, derecha), entrada, 200.0)
        assertTrue(r.cortadaEnCruceDudoso)
        assertCerca(p(0.0, 0.0), r.puntos.last())
        assertEquals(emptyList<String>(), r.refs)
    }

    @Test
    fun `con ref distinto en las dos ramas de la Y tambien corta`() {
        val entrada = via(1, "Ma-1", p(-1000.0, 0.0), p(0.0, 0.0))
        val izquierda = via(2, "Ma-2", p(0.0, 0.0), p(1000.0, 466.0))
        val derecha = via(3, "Ma-3", p(0.0, 0.0), p(1000.0, -466.0))
        assertTrue(predecir(listOf(entrada, izquierda, derecha), entrada, 200.0).cortadaEnCruceDudoso)
    }

    @Test
    fun `en una curva con un ramal a 90 grados sigue por el menor giro`() {
        val entrada = via(1, null, p(-1000.0, 0.0), p(0.0, 0.0))
        val curva = via(2, null, p(0.0, 0.0), p(985.0, 174.0))
        val ramal = via(3, null, p(0.0, 0.0), p(0.0, -800.0))
        val r = predecir(listOf(entrada, curva, ramal), entrada, 500.0)
        assertCerca(p(985.0, 174.0), r.puntos.last())
        assertFalse(r.cortadaEnCruceDudoso)
    }

    @Test
    fun `no toma una via de sentido unico en contra`() {
        val entrada = via(1, null, p(-1000.0, 0.0), p(0.0, 0.0))
        // Recta, pero solo se circula hacia el nodo: prohibida desde él.
        val prohibida = via(2, null, p(0.0, 0.0), p(1000.0, 0.0), sentido = SentidoUnico.ATRAS)
        val permitida = via(3, null, p(0.0, 0.0), p(700.0, 700.0))
        val r = predecir(listOf(entrada, prohibida, permitida), entrada, 500.0)
        assertCerca(p(700.0, 700.0), r.puntos.last())
        assertFalse(r.cortadaEnCruceDudoso)
    }

    @Test
    fun `una unica salida permitida no es cruce dudoso aunque la otra sea simetrica`() {
        val entrada = via(1, null, p(-1000.0, 0.0), p(0.0, 0.0))
        val prohibida = via(2, null, p(0.0, 0.0), p(1000.0, 466.0), sentido = SentidoUnico.ATRAS)
        val permitida = via(3, null, p(0.0, 0.0), p(1000.0, -466.0))
        val r = predecir(listOf(entrada, prohibida, permitida), entrada, 500.0)
        assertCerca(p(1000.0, -466.0), r.puntos.last())
        assertFalse(r.cortadaEnCruceDudoso)
    }

    @Test
    fun `al acabar la via sin continuacion para sin marcar duda`() {
        val unica = via(1, "Ma-1", p(0.0, 0.0), p(1000.0, 0.0))
        val r = predecir(listOf(unica), unica, 100.0)
        assertCerca(p(100.0, 0.0), r.puntos.first())
        assertCerca(p(1000.0, 0.0), r.puntos.last())
        assertEquals(900.0, r.puntos.longitudMetros(), 1.0)
        assertFalse(r.cortadaEnCruceDudoso)
    }

    @Test
    fun `en sentido contrario recorre la via hacia su primer punto`() {
        val unica = via(1, null, p(0.0, 0.0), p(500.0, 0.0), p(1000.0, 0.0))
        val r = predecir(listOf(unica), unica, 700.0, adelante = false)
        assertCerca(p(700.0, 0.0), r.puntos.first())
        assertCerca(p(0.0, 0.0), r.puntos.last())
        assertEquals(700.0, r.puntos.longitudMetros(), 1.0)
    }

    @Test
    fun `se detiene a los metros maximos atravesando varias vias`() {
        val a = via(1, "Ma-1", p(0.0, 0.0), p(1000.0, 0.0))
        val b = via(2, "Ma-1", p(1000.0, 0.0), p(2000.0, 0.0))
        val c = via(3, "Ma-1", p(2000.0, 0.0), p(5000.0, 0.0))
        val r = predecir(listOf(a, b, c), a, 500.0)
        assertEquals(2000.0, r.puntos.longitudMetros(), 1.0)
        assertCerca(p(2500.0, 0.0), r.puntos.last())
        assertEquals(listOf("Ma-1"), r.refs)
        val corta = predecir(listOf(a, b, c), a, 500.0, maxM = 300.0)
        assertEquals(300.0, corta.puntos.longitudMetros(), 1.0)
    }

    @Test
    fun `no da la vuelta por una via que vuelve al punto anterior`() {
        val ida = via(1, null, p(0.0, 0.0), p(1000.0, 0.0))
        val vuelta = via(2, null, p(1000.0, 0.0), p(0.0, 0.0))
        val r = predecir(listOf(ida, vuelta), ida, 100.0)
        assertCerca(p(1000.0, 0.0), r.puntos.last())
        assertEquals(900.0, r.puntos.longitudMetros(), 1.0)
        assertFalse(r.cortadaEnCruceDudoso)
    }

    @Test
    fun `cruza un nodo interior de la via recto y sin duda si el ramal es perpendicular`() {
        val principal = via(1, null, p(0.0, 0.0), p(500.0, 0.0), p(1500.0, 0.0))
        val lateral = via(2, null, p(500.0, 0.0), p(500.0, 600.0))
        val r = predecir(listOf(principal, lateral), principal, 100.0)
        assertCerca(p(1500.0, 0.0), r.puntos.last())
        assertFalse(r.cortadaEnCruceDudoso)
    }

    @Test
    fun `es determinista`() {
        val entrada = via(1, null, p(-1000.0, 0.0), p(0.0, 0.0))
        val a = via(2, null, p(0.0, 0.0), p(1000.0, 466.0))
        val b = via(3, null, p(0.0, 0.0), p(1000.0, -466.0))
        val vias = listOf(entrada, a, b)
        assertEquals(predecir(vias, entrada, 100.0), predecir(vias, entrada, 100.0))
    }
}
