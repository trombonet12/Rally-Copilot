package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.notas.TrazasDePrueba
import app.trombito.rallycopilot.motor.tramos.Conduccion.desplazar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TramosEnCaminoTest {
    /** Carretera recta de 6 km hacia el norte. */
    private val carretera = TrazasDePrueba.recta(6000.0)

    private fun tramoDe(
        id: String,
        traza: List<PuntoGeo>,
    ) = Tramo(id, traza, traza.longitudMetros())

    private fun trozoDeCarretera(
        id: String,
        desdeM: Double,
        hastaM: Double,
    ) = tramoDe(id, Conduccion.subtraza(carretera, desdeM, hastaM))

    private fun caminoDe(
        desdeM: Double,
        hastaM: Double,
    ) = Conduccion.subtraza(carretera, desdeM, hastaM)

    private fun sobre(
        tramos: List<Tramo>,
        camino: List<PuntoGeo>,
    ) = TramosEnCamino(tramos).sobre(camino)

    @Test
    fun `un tramo que el camino recorre entero da su trozo con extremos y banderas`() {
        val r = sobre(listOf(trozoDeCarretera("A", 1000.0, 3000.0)), carretera).single()
        assertEquals("A", r.tramoId)
        assertCerca(1000, r.desdeM, 12.0)
        assertCerca(3000, r.hastaM, 12.0)
        assertTrue(r.empiezaEnSalida)
        assertTrue(r.acabaEnMeta)
        assertCerca(0, r.tramoDesdeM, 30.0)
        assertCerca(2000, r.tramoHastaM, 30.0)
        assertCerca(0, r.salida.distanciaA(Conduccion.puntoA(carretera, 1000.0)), 12.0)
        assertCerca(0, r.meta.distanciaA(Conduccion.puntoA(carretera, 3000.0)), 12.0)
        assertCerca(0, r.geometria.first().distanciaA(r.salida), 0.5)
        assertCerca(0, r.geometria.last().distanciaA(r.meta), 0.5)
        assertCerca(2000, r.geometria.longitudMetros(), 15.0)
    }

    @Test
    fun `un tramo recorrido en parte se recorta al solape`() {
        val tramo = trozoDeCarretera("A", 0.0, 4000.0)
        val r = sobre(listOf(tramo), caminoDe(3000.0, 6000.0)).single()
        assertCerca(0, r.desdeM, 12.0)
        assertCerca(1000, r.hastaM, 12.0)
        assertFalse(r.empiezaEnSalida)
        assertTrue(r.acabaEnMeta)
        assertCerca(3000, r.tramoDesdeM, 15.0)
        assertCerca(1000, r.longitudM, 15.0)
    }

    @Test
    fun `si el camino sale antes de la meta no marca meta`() {
        val tramo = trozoDeCarretera("A", 0.0, 4000.0)
        val r = sobre(listOf(tramo), caminoDe(500.0, 2500.0)).single()
        assertFalse(r.empiezaEnSalida)
        assertFalse(r.acabaEnMeta)
        assertCerca(2000, r.longitudM, 15.0)
    }

    @Test
    fun `en sentido contrario no cuenta`() {
        val tramo = trozoDeCarretera("A", 1000.0, 4000.0)
        assertTrue(sobre(listOf(tramo), carretera.reversed()).isEmpty())
    }

    @Test
    fun `menos de 300 m de solape no cuenta y con 350 m si`() {
        val tramo = trozoDeCarretera("A", 0.0, 5000.0)
        assertTrue(sobre(listOf(tramo), caminoDe(4750.0, 6000.0)).isEmpty())
        val r = sobre(listOf(tramo), caminoDe(4650.0, 6000.0)).single()
        assertCerca(350, r.longitudM, 15.0)
    }

    @Test
    fun `un tramo corto basta con el 80 por ciento`() {
        val corto = trozoDeCarretera("C", 2000.0, 2200.0)
        assertEquals("C", sobre(listOf(corto), carretera).single().tramoId)
        assertTrue(sobre(listOf(corto), caminoDe(2000.0, 2140.0)).isEmpty())
        assertEquals("C", sobre(listOf(corto), caminoDe(2000.0, 2190.0)).single().tramoId)
    }

    @Test
    fun `a mas de 25 m de la traza no cuenta y a 15 m si`() {
        val lejos = tramoDe("L", carretera.map { desplazar(it, 0.0, 40.0) })
        val cerca = tramoDe("C", carretera.map { desplazar(it, 0.0, 15.0) })
        assertTrue(sobre(listOf(lejos), carretera).isEmpty())
        assertEquals("C", sobre(listOf(cerca), carretera).single().tramoId)
    }

    @Test
    fun `un subtramo con la misma extension que su tramo se queda en el tramo`() {
        val tramo = trozoDeCarretera("tramo", 0.0, 4000.0)
        val sub = trozoDeCarretera("sub", 1000.0, 3000.0)
        val r = sobre(listOf(sub, tramo), caminoDe(1000.0, 3000.0))
        assertEquals(listOf("tramo"), r.map { it.tramoId })
        assertEquals(listOf("tramo"), sobre(listOf(tramo, sub), caminoDe(1000.0, 3000.0)).map { it.tramoId })
    }

    @Test
    fun `un subtramo mas corto que lo recorrido del tramo se mantiene aparte`() {
        val tramo = trozoDeCarretera("tramo", 0.0, 4000.0)
        val sub = trozoDeCarretera("sub", 1000.0, 3000.0)
        val r = sobre(listOf(tramo, sub), caminoDe(0.0, 4000.0))
        assertEquals(listOf("tramo", "sub"), r.map { it.tramoId })
    }

    @Test
    fun `dos trozos equivalentes y del mismo largo dejan el de menor id`() {
        val b = trozoDeCarretera("b", 500.0, 2500.0)
        val a = trozoDeCarretera("a", 500.0, 2500.0)
        assertEquals(listOf("a"), sobre(listOf(b, a), carretera).map { it.tramoId })
    }

    @Test
    fun `sale ordenado por desdeM`() {
        val tarde = trozoDeCarretera("tarde", 4000.0, 5000.0)
        val pronto = trozoDeCarretera("pronto", 500.0, 1500.0)
        val medio = trozoDeCarretera("medio", 2000.0, 3000.0)
        val r = sobre(listOf(tarde, pronto, medio), carretera)
        assertEquals(listOf("pronto", "medio", "tarde"), r.map { it.tramoId })
        assertTrue(r.zipWithNext().all { (a, b) -> a.desdeM < b.desdeM })
    }

    @Test
    fun `un tramo recorrido dos veces por el camino da dos trozos`() {
        val tramo = trozoDeCarretera("A", 1000.0, 2000.0)
        // Ida por la carretera, vuelta por una paralela a 200 m, y otra vez la carretera.
        val ida = caminoDe(0.0, 3000.0)
        val vuelta = ida.reversed().map { desplazar(it, 0.0, 200.0) }
        val camino = ida + vuelta + ida
        val r = sobre(listOf(tramo), camino)
        assertEquals(2, r.size)
        assertTrue(r[1].desdeM > r[0].hastaM + 3000.0)
    }

    @Test
    fun `entre cientos de tramos lejanos solo salen los que pisa el camino`() {
        val lejanos =
            (0 until 400).map { k ->
                tramoDe("L$k", carretera.map { desplazar(it, 0.0, 3000.0 + 10.0 * k) })
            }
        val propio = trozoDeCarretera("propio", 1000.0, 2000.0)
        val r = sobre(lejanos + propio, caminoDe(0.0, 3000.0))
        assertEquals(listOf("propio"), r.map { it.tramoId })
    }

    @Test
    fun `sin tramos o con un camino degenerado no hay nada`() {
        assertTrue(sobre(emptyList(), carretera).isEmpty())
        val tramos = listOf(trozoDeCarretera("A", 0.0, 2000.0))
        assertTrue(sobre(tramos, emptyList()).isEmpty())
        assertTrue(sobre(tramos, listOf(carretera.first())).isEmpty())
        assertTrue(sobre(tramos, listOf(carretera.first(), carretera.first())).isEmpty())
    }

    @Test
    fun `la misma instancia sirve para caminos distintos y es determinista`() {
        val detector = TramosEnCamino(listOf(trozoDeCarretera("A", 1000.0, 2500.0)))
        val primero = detector.sobre(carretera)
        assertEquals(primero, detector.sobre(carretera))
        assertTrue(detector.sobre(caminoDe(3000.0, 6000.0)).isEmpty())
        assertEquals(primero, detector.sobre(carretera))
    }
}
