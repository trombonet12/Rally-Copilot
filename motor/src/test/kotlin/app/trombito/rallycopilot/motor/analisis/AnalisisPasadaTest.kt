package app.trombito.rallycopilot.motor.analisis

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.TrazasDePrueba
import app.trombito.rallycopilot.motor.tramos.Conduccion
import app.trombito.rallycopilot.motor.tramos.Cronometro
import app.trombito.rallycopilot.motor.tramos.EventoCrono
import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.motor.tramos.TramoDesdeRecorrido
import app.trombito.rallycopilot.motor.tramos.assertCerca
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Análisis de pasadas sobre la traza real de Sa Calobra (docs/rutas/) a velocidades conocidas. */
class AnalisisPasadaTest {
    private val calobra: List<PuntoGeo> = TrazasDePrueba.cargarGpx("ma-2141-sa-calobra.gpx")
    private val tramo: Tramo = TramoDesdeRecorrido.crear("calobra", calobra)!!

    private class Vuelta(
        val pasada: Pasada,
        val eventos: List<EventoCrono>,
    )

    private fun cronometrar(fijaciones: List<Fijacion>): Vuelta {
        var ahora = 0L
        val crono = Cronometro(tramo, reloj = { ahora })
        val eventos =
            fijaciones.flatMap {
                ahora = it.tiempoMs
                crono.alFijar(it)
            }
        val pasada = crono.pasada()
        assertNotNull("la pasada debía terminar", pasada)
        return Vuelta(pasada!!, eventos)
    }

    private fun constante(kmh: Double) = Conduccion.muestrear(calobra, kmh)

    /** Conduce [primeraKmh] hasta [cambioM] y [segundaKmh] después, con [paradaMs] parado en el cambio. */
    private fun variable(
        primeraKmh: Double,
        segundaKmh: Double,
        cambioM: Double,
        paradaMs: Long = 0L,
    ): List<Fijacion> {
        val primera = Conduccion.muestrear(calobra, primeraKmh, hastaM = cambioM)
        val fin = primera.last().tiempoMs
        val parado = (1..(paradaMs / 1000L).toInt()).map { Conduccion.fijacion(primera.last().punto, fin + it * 1000L) }
        val reanuda = fin + paradaMs
        return primera + parado + Conduccion.muestrear(calobra, segundaKmh, t0Ms = reanuda, desdeM = cambioM).drop(1)
    }

    private fun analizar(
        fijaciones: List<Fijacion>,
        comparada: Pasada? = null,
    ): Pair<Vuelta, AnalisisPasada> {
        val vuelta = cronometrar(fijaciones)
        return vuelta to AnalisisPasada.de(tramo, fijaciones, vuelta.pasada, comparada)
    }

    @Test
    fun `el total y los cortes coinciden con el cronometraje en vivo a 0,1 s`() {
        val (vuelta, analisis) = analizar(constante(60.0))
        assertCerca(vuelta.pasada.totalMs, analisis.totalMs, 100.0)
        val meta = vuelta.eventos.filterIsInstance<EventoCrono.Meta>().single()
        assertCerca(meta.tMs, analisis.sectores.last().acumuladoMs, 100.0)

        val cortes = vuelta.eventos.filterIsInstance<EventoCrono.CorteSector>()
        assertEquals(tramo.sectores.size, cortes.size)
        cortes.forEach { assertCerca(it.tMs, analisis.sectores[it.numero - 1].acumuladoMs, 100.0) }
        val micros = vuelta.eventos.filterIsInstance<EventoCrono.CorteMicro>()
        assertEquals(tramo.microsectores.size, micros.size)
        micros.forEach { assertCerca(it.tMs, analisis.microsectores[it.numero - 1].acumuladoMs, 100.0) }
        assertEquals(tramo.sectores.size + 1, analisis.sectores.size)
        assertEquals(tramo.microsectores.size + 1, analisis.microsectores.size)
        assertCerca(analisis.totalMs, analisis.sectores.sumOf { it.tiempoMs }, 5.0)
    }

    @Test
    fun `los tiempos de la repeticion llegan a la meta con el mismo total`() {
        val (vuelta, analisis) = analizar(constante(50.0))
        val repeticion = analisis.repeticion
        assertEquals(vuelta.pasada.totalMs, repeticion.duracionMs)
        assertCerca(0, repeticion.posiciones.first().tMs, 0.0)
        assertCerca(vuelta.pasada.totalMs, repeticion.posiciones.last().tMs, 0.0)
        assertCerca(tramo.metaM, repeticion.posiciones.last().distanciaM, 1.0)
    }

    @Test
    fun `la rapida contra la lenta gana tiempo en cada sector y microsector y los deltas suman`() {
        val lenta = cronometrar(constante(40.0)).pasada
        val (_, rapida) = analizar(constante(60.0), lenta)

        assertTrue(rapida.totalMs < lenta.totalMs)
        assertCerca(lenta.totalMs - rapida.totalMs, -rapida.deltaTotalMs!!, 100.0)
        (rapida.sectores + rapida.microsectores).forEach {
            assertTrue("parcial ${it.numero}: ${it.diferenciaMs}", it.diferenciaMs!! < 0L)
            assertTrue("acumulado ${it.numero}: ${it.deltaAcumuladoMs}", it.deltaAcumuladoMs!! < 0L)
            assertTrue(it.comparadaMs!! > it.tiempoMs)
        }
        assertCerca(rapida.deltaTotalMs!!, rapida.sectores.sumOf { it.diferenciaMs!! }, 5.0)
        assertCerca(rapida.deltaTotalMs!!, rapida.sectores.last().deltaAcumuladoMs!!, 5.0)
        // La serie de delta empieza en 0, acaba en el delta total y baja de forma monótona (siempre más rápida).
        assertCerca(0, rapida.delta.first().valor, 1.0)
        assertCerca(rapida.deltaTotalMs!!, rapida.delta.last().valor, 1.0)
        assertTrue(rapida.delta.zipWithNext().all { (a, b) -> b.valor <= a.valor + 1.0 })
    }

    @Test
    fun `sin pasada comparada no hay deltas`() {
        val (_, analisis) = analizar(constante(60.0))
        assertNull(analisis.deltaTotalMs)
        assertTrue(analisis.delta.isEmpty())
        (analisis.sectores + analisis.microsectores).forEach {
            assertNull(it.diferenciaMs)
            assertNull(it.comparadaMs)
            assertNull(it.deltaAcumuladoMs)
        }
    }

    @Test
    fun `la velocidad media y maxima por sector reflejan los 60 km por hora`() {
        val (_, analisis) = analizar(constante(60.0))
        analisis.sectores.forEach {
            assertCerca(60.0, it.velMediaKmh, 1.5)
            assertTrue("máx ${it.velMaxKmh}", it.velMaxKmh >= it.velMediaKmh && it.velMaxKmh < 66.0)
        }
        // Con dos velocidades, el último tramo del recorrido es el rápido.
        val mixta = analizar(variable(30.0, 90.0, tramo.metaM / 2)).second
        assertTrue(mixta.sectores.first().velMediaKmh < 40.0)
        assertTrue(mixta.sectores.last().velMediaKmh > 80.0)
        assertTrue(mixta.sectores.last().velMaxKmh >= 88.0)
    }

    @Test
    fun `la serie de velocidad se muestrea cada 25 m y es estable a velocidad constante`() {
        val (_, analisis) = analizar(constante(60.0))
        val serie = analisis.velocidad
        assertCerca(0, serie.first().distanciaM, 0.0)
        assertCerca(tramo.metaM, serie.last().distanciaM, 0.0)
        serie.dropLast(1).zipWithNext().forEach { (a, b) -> assertCerca(25.0, b.distanciaM - a.distanciaM, 1e-6) }
        assertTrue(serie.last().distanciaM - serie[serie.size - 2].distanciaM <= 25.0 + 1e-6)
        // La traza simplificada recorta curvas: la velocidad local sobre ella varía algo (≤ 10 %), la media no.
        serie.forEach { assertCerca(60.0, it.valor, 6.0) }
        assertCerca(60.0, serie.map { it.valor }.average(), 1.5)
    }

    @Test
    fun `una parada se ve como un valle de velocidad y suma al tiempo`() {
        val sin = analizar(variable(60.0, 60.0, 1500.0)).first.pasada
        val (con, analisis) = analizar(variable(60.0, 60.0, 1500.0, paradaMs = 10_000L))
        assertCerca(sin.totalMs + 10_000L, con.pasada.totalMs, 100.0)
        val valle = analisis.velocidad.filter { it.distanciaM in 1450.0..1550.0 }.minOf { it.valor }
        assertTrue("valle $valle", valle < 20.0)
        assertCerca(analisis.totalMs, analisis.sectores.sumOf { it.tiempoMs }, 5.0)
    }

    @Test
    fun `los segmentos del mapa cubren la traza y la escala por cuantiles ordena lento a rapido`() {
        val (_, analisis) = analizar(variable(30.0, 90.0, tramo.metaM / 2))
        val segmentos = analisis.segmentos
        assertEquals(analisis.velocidad.size - 1, segmentos.size)
        assertCerca(0, segmentos.first().desdeM, 0.0)
        assertCerca(tramo.metaM, segmentos.last().hastaM, 0.0)
        assertTrue(segmentos.all { it.puntos.size >= 2 })
        segmentos.zipWithNext().forEach { (a, b) -> assertEquals(a.puntos.last(), b.puntos.first()) }

        val mitad = tramo.metaM / 2
        val lentos = segmentos.filter { it.hastaM < mitad - 50.0 }
        val rapidos = segmentos.filter { it.desdeM > mitad + 50.0 }
        val escala = analisis.escala
        assertEquals(EscalaVelocidad.PROBABILIDADES.size, escala.cortesKmh.size)
        assertTrue(escala.cortesKmh.zipWithNext().all { (a, b) -> a <= b })
        assertTrue(lentos.all { escala.fraccion(it.velocidadKmh) < 0.5 })
        assertTrue(rapidos.all { escala.fraccion(it.velocidadKmh) > 0.45 })
        assertTrue(lentos.map { escala.fraccion(it.velocidadKmh) }.average() < 0.3)
        assertTrue(rapidos.map { escala.fraccion(it.velocidadKmh) }.average() > 0.7)
        segmentos.forEach { assertTrue(escala.fraccion(it.velocidadKmh) in 0.0..1.0) }
    }

    @Test
    fun `la escala por cuantiles no se aplasta con un valor extremo`() {
        val escala = EscalaVelocidad.de(List(99) { 50.0 + it % 10 } + 300.0)
        // Sin cuantiles el 59 km/h quedaría en ~0,04 de una escala 50..300; con cuantiles queda en lo alto.
        assertTrue(escala.fraccion(58.0) > 0.7)
        assertCerca(0.5, EscalaVelocidad.de(listOf(40.0, 40.0)).fraccion(40.0), 0.0)
        assertCerca(0.0, escala.fraccion(10.0), 0.0)
        assertCerca(1.0, escala.fraccion(500.0), 0.0)
    }

    @Test
    fun `la repeticion interpola la posicion en el tiempo`() {
        val fijaciones = constante(60.0)
        val (vuelta, analisis) = analizar(fijaciones)
        val repeticion = analisis.repeticion
        val mitad = repeticion.en(vuelta.pasada.totalMs / 2)!!
        assertCerca(tramo.metaM / 2, mitad.distanciaM, tramo.metaM * 0.02)
        // Entre dos fijaciones (a 500 ms): a medio camino entre ambas.
        val a = fijaciones[10]
        val b = fijaciones[11]
        val medio = repeticion.en((a.tiempoMs + b.tiempoMs) / 2 - (fijaciones[0].tiempoMs))!!
        assertCerca((a.punto.lat + b.punto.lat) / 2, medio.punto.lat, 1e-5)
        assertCerca((a.punto.lon + b.punto.lon) / 2, medio.punto.lon, 1e-5)
        // Acotada a los extremos.
        assertEquals(repeticion.posiciones.first().punto, repeticion.en(-5_000L)!!.punto)
        assertEquals(repeticion.posiciones.last().punto, repeticion.en(vuelta.pasada.totalMs + 9_000L)!!.punto)
        assertTrue(repeticion.posiciones.zipWithNext().all { (p, q) -> p.tMs <= q.tMs && p.distanciaM <= q.distanciaM })
    }

    @Test
    fun `con fijaciones que empiezan antes de la salida los tiempos siguen coincidiendo`() {
        val antes = Conduccion.muestrear(calobra, 40.0, t0Ms = 5_000L)
        val (vuelta, analisis) = analizar(antes)
        assertCerca(vuelta.pasada.totalMs, analisis.totalMs, 100.0)
        assertCerca(
            vuelta.pasada.totalMs,
            analisis.repeticion.posiciones
                .last()
                .tMs,
            0.0,
        )
        assertCerca(
            0,
            analisis.repeticion.posiciones
                .first()
                .tMs,
            0.0,
        )
    }

    @Test
    fun `sin puntos la repeticion sale de las marcas de la pasada`() {
        val pasada = cronometrar(constante(60.0)).pasada
        val analisis = AnalisisPasada.de(tramo, emptyList(), pasada, null)
        assertEquals(pasada.totalMs, analisis.repeticion.duracionMs)
        assertTrue(analisis.repeticion.posiciones.size >= 2)
        assertNotNull(analisis.repeticion.en(1_000L))
        analisis.sectores.forEach { assertCerca(60.0, it.velMediaKmh, 1.5) }
    }

    @Test
    fun `las posiciones de la repeticion parten de la salida y acaban en la meta`() {
        val (_, analisis) = analizar(constante(60.0))
        val primera =
            analisis.repeticion.posiciones
                .first()
                .punto
        val ultima =
            analisis.repeticion.posiciones
                .last()
                .punto
        assertTrue(primera.distanciaA(tramo.traza.first()) < 60.0)
        assertTrue(ultima.distanciaA(tramo.traza.last()) < 60.0)
    }
}
