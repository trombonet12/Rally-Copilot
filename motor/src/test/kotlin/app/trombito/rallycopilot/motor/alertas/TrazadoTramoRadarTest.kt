package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.alertas.GeoPruebas.punto
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.vias.GrafoVias
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrazadoTramoRadarTest {
    private fun via(
        id: Long,
        vararg puntos: PuntoGeo,
    ) = Via(id, "secondary", null, null, null, SentidoUnico.NO, null, null, puntos.toList())

    private fun trazar(
        inicio: PuntoGeo,
        fin: PuntoGeo,
        vararg vias: Via,
    ): List<PuntoGeo> = TrazadoTramoRadar.de(inicio, fin, IndiceVias(vias.toList()), GrafoVias(vias.toList()))

    @Test
    fun `sin vias cerca el trazado es la recta de dos puntos`() {
        val inicio = punto(0.0)
        val fin = punto(1000.0)
        assertEquals(listOf(inicio, fin), trazar(inicio, fin))
    }

    @Test
    fun `en una sola via sigue su geometria entre las dos proyecciones`() {
        // Una L: 1000 m al norte y luego 1000 m al este.
        val esquina = punto(1000.0)
        val v = via(1, punto(0.0), esquina, punto(1000.0, 1000.0))
        val traza = trazar(punto(200.0, 5.0), punto(1000.0, 800.0), v)
        // Pasa por la esquina: la recta no.
        assertTrue(traza.any { it.distanciaA(esquina) < 1.0 })
        assertEquals(1600.0, traza.longitudMetros(), 25.0)
        assertEquals(punto(200.0, 5.0), traza.first())
        assertEquals(punto(1000.0, 800.0), traza.last())
    }

    @Test
    fun `si el tramo se recorre contra el dibujo de la via sale en el sentido de la marcha`() {
        val v = via(1, punto(0.0), punto(2000.0))
        val traza = trazar(punto(1500.0), punto(500.0), v)
        assertTrue(traza.zipWithNext().all { (p, q) -> q.lat <= p.lat })
        assertEquals(1000.0, traza.longitudMetros(), 5.0)
    }

    @Test
    fun `encadena vias conectadas hasta la del final`() {
        val a = via(1, punto(0.0), punto(1000.0))
        val b = via(2, punto(1000.0), punto(1000.0, 500.0), punto(1000.0, 1000.0))
        val c = via(3, punto(1000.0, 1000.0), punto(2000.0, 1000.0))
        val traza = trazar(punto(200.0), punto(1700.0, 1002.0), a, b, c)
        assertTrue(traza.any { it.distanciaA(punto(1000.0)) < 1.0 })
        assertTrue(traza.any { it.distanciaA(punto(1000.0, 1000.0)) < 1.0 })
        // 800 al norte + 1000 al este + 700 al norte.
        assertEquals(2500.0, traza.longitudMetros(), 30.0)
    }

    @Test
    fun `si las vias no se conectan vuelve a la recta`() {
        val a = via(1, punto(0.0), punto(1000.0))
        val c = via(3, punto(1500.0), punto(2500.0))
        val inicio = punto(200.0)
        val fin = punto(2000.0)
        assertEquals(listOf(inicio, fin), trazar(inicio, fin, a, c))
    }

    @Test
    fun `un recorrido mucho mas largo que la recta se descarta`() {
        // La via da un rodeo enorme de 6 km entre dos puntos a 500 m en recta.
        val v = via(1, punto(0.0), punto(0.0, 3000.0), punto(500.0, 3000.0), punto(500.0, 0.0))
        val inicio = punto(0.0, 2.0)
        val fin = punto(500.0, 2.0)
        assertEquals(listOf(inicio, fin), trazar(inicio, fin, v))
    }
}
