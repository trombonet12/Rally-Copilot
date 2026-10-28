package app.trombito.rallycopilot.motor.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.random.Random

class SimplificacionTest {
    // A 39,5 N: 0,0001 grados de latitud ~ 11,1 m; 0,0001 grados de longitud ~ 8,6 m.
    private fun alNorte(
        pasos: Int,
        lonDesplazada: Double = 0.0,
    ) = PuntoGeo(39.5 + 0.0001 * pasos, 2.6 + lonDesplazada)

    @Test
    fun `una linea recta se queda en dos puntos`() {
        val recta = (0..20).map { alNorte(it) }
        assertEquals(listOf(recta.first(), recta.last()), recta.simplificar(1.0))
    }

    @Test
    fun `tolerancia minima solo quita los colineales`() {
        val recta = (0..5).map { alNorte(it) }
        assertEquals(2, recta.simplificar(0.01).size)
        val conVertice = recta + alNorte(5, 0.0001)
        assertEquals(listOf(recta.first(), recta.last(), conVertice.last()), conVertice.simplificar(0.01))
    }

    @Test
    fun `tolerancia cero es valida y conserva los vertices`() {
        val zigzag = (0..4).map { alNorte(it, if (it % 2 == 0) 0.0 else 0.0003) }
        assertEquals(zigzag, zigzag.simplificar(0.0))
    }

    @Test
    fun `un zigzag por encima de la tolerancia se conserva`() {
        // Desvios de +-0,0003 grados de longitud, unos 26 m.
        val zigzag = (0..10).map { alNorte(it, if (it % 2 == 0) 0.0 else 0.0003) }
        assertEquals(zigzag, zigzag.simplificar(10.0))
    }

    @Test
    fun `el mismo zigzag por debajo de la tolerancia se alisa`() {
        val zigzag = (0..10).map { alNorte(it, if (it % 2 == 0) 0.0 else 0.0003) }
        assertEquals(listOf(zigzag.first(), zigzag.last()), zigzag.simplificar(100.0))
    }

    @Test
    fun `el ruido pequeno desaparece pero la esquina se conserva`() {
        // Norte 10 pasos con ruido de ~1,7 m y luego este 10 pasos: la esquina esta en el paso 10.
        val norte = (0..10).map { alNorte(it, if (it % 2 == 0) 0.0 else 0.00002) }
        val esquina = norte.last().copy(lon = 2.6)
        val este = (1..10).map { PuntoGeo(esquina.lat, 2.6 + 0.0001 * it) }
        val resultado = (norte.dropLast(1) + esquina + este).simplificar(5.0)
        assertEquals(listOf(norte.first(), esquina, este.last()), resultado)
    }

    @Test
    fun `conserva primero y ultimo, el orden y los objetos con su altitud`() {
        val puntos =
            (0..8).map {
                PuntoGeo(39.5 + 0.0001 * it, 2.6 + if (it % 3 == 0) 0.0004 else 0.0, alt = 100.0 + it)
            }
        val resultado = puntos.simplificar(10.0)
        assertSame(puntos.first(), resultado.first())
        assertSame(puntos.last(), resultado.last())
        val indices = resultado.map { r -> puntos.indexOfFirst { it === r } }
        assertEquals(indices.sorted(), indices)
        assertTrue(indices.all { it >= 0 })
    }

    @Test
    fun `listas de menos de tres puntos se devuelven iguales`() {
        assertEquals(emptyList<PuntoGeo>(), emptyList<PuntoGeo>().simplificar(5.0))
        assertEquals(listOf(alNorte(0)), listOf(alNorte(0)).simplificar(5.0))
        assertEquals(listOf(alNorte(0), alNorte(1)), listOf(alNorte(0), alNorte(1)).simplificar(5.0))
    }

    @Test
    fun `un circuito cerrado conserva su forma`() {
        val a = PuntoGeo(39.5, 2.6)
        val b = PuntoGeo(39.5, 2.601)
        val c = PuntoGeo(39.501, 2.601)
        val cerrado = listOf(a, b, c, a)
        assertEquals(cerrado, cerrado.simplificar(5.0))
    }

    @Test
    fun `tolerancia negativa o no finita es un error`() {
        val puntos = (0..3).map { alNorte(it) }
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { tolerancia ->
            val fallo = runCatching { puntos.simplificar(tolerancia) }.exceptionOrNull()
            assertTrue("tolerancia $tolerancia", fallo is IllegalArgumentException)
        }
    }

    @Test
    fun `una traza muy larga no desborda la pila`() {
        val larga = (0..20_000).map { alNorte(it, if (it % 2 == 0) 0.0 else 0.0003) }
        val resultado = larga.simplificar(10.0)
        assertSame(larga.first(), resultado.first())
        assertSame(larga.last(), resultado.last())
    }

    @Test
    fun `ningun punto descartado queda mas lejos de la tolerancia`() {
        val azar = Random(42)
        var lat = 39.6
        var lon = 2.7
        val traza =
            (0 until 600).map {
                lat += 0.00005 + azar.nextDouble(-0.00004, 0.00006)
                lon += azar.nextDouble(-0.00005, 0.00007)
                PuntoGeo(lat, lon)
            }
        for (tolerancia in listOf(2.0, 10.0, 40.0)) {
            val simple = traza.simplificar(tolerancia)
            assertTrue("no simplifica con $tolerancia m", simple.size < traza.size)
            traza.forEach { p ->
                val d = simple.zipWithNext().minOf { (a, b) -> distanciaASegmentoM(p, a, b) }
                assertTrue("a $d m con tolerancia $tolerancia", d <= tolerancia + 0.01)
            }
        }
    }

    /** Referencia independiente: proyeccion plana centrada en el segmento. */
    private fun distanciaASegmentoM(
        p: PuntoGeo,
        a: PuntoGeo,
        b: PuntoGeo,
    ): Double {
        val k = cos(Math.toRadians((a.lat + b.lat) / 2)) * PuntoGeo.RADIO_TIERRA_M
        val m = PuntoGeo.RADIO_TIERRA_M
        val bx = Math.toRadians(b.lon - a.lon) * k
        val by = Math.toRadians(b.lat - a.lat) * m
        val px = Math.toRadians(p.lon - a.lon) * k
        val py = Math.toRadians(p.lat - a.lat) * m
        val l2 = bx * bx + by * by
        val t = if (l2 == 0.0) 0.0 else ((px * bx + py * by) / l2).coerceIn(0.0, 1.0)
        return hypot(px - t * bx, py - t * by)
    }
}
