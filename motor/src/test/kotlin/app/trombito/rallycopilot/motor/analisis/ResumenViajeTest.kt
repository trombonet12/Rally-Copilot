package app.trombito.rallycopilot.motor.analisis

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos

class ResumenViajeTest {
    private val latBase = 39.6
    private val lonBase = 2.9
    private val metrosPorGradoLat = 111_195.0
    private val metrosPorGradoLon = metrosPorGradoLat * cos(Math.toRadians(latBase))

    private fun punto(x: Double) = PuntoGeo(latBase, lonBase + x / metrosPorGradoLon)

    private fun rectaVia(
        id: Long,
        desdeX: Double,
        hastaX: Double,
        ref: String? = "Ma-$id",
        nombre: String? = null,
    ) = Via(
        id,
        "secondary",
        null,
        null,
        null,
        SentidoUnico.NO,
        ref,
        nombre,
        (0..((hastaX - desdeX) / 100.0).toInt()).map { punto(desdeX + it * 100.0) },
    )

    private val viaA = rectaVia(1, 0.0, 1_000.0)
    private val viaB = rectaVia(2, 1_000.0, 2_000.0)
    private val indice = IndiceVias(listOf(viaA, viaB))
    private val coincidenciaDe = VelocidadVias.CoincidenciaDe { p, r -> indice.cercana(p, r) }

    /** Recorrido por el eje x a [kmh] constante de [desdeX] a [hastaX], un punto por segundo desde [t0Ms]. */
    private fun conducir(
        desdeX: Double,
        hastaX: Double,
        kmh: Double = 60.0,
        t0Ms: Long = 0L,
        conVelocidad: Boolean = false,
    ): List<PuntoTiempo> {
        val ms = kmh / 3.6
        val n = (abs(hastaX - desdeX) / ms).toInt()
        return (0..n).map {
            PuntoTiempo(punto(desdeX + ms * it), t0Ms + it * 1_000L, if (conVelocidad) ms else null)
        }
    }

    private fun resumen(
        puntos: List<PuntoTiempo>,
        previstoS: Long? = null,
        historico: Map<Long, VelocidadVia> = emptyMap(),
        tramos: List<TramoHecho> = emptyList(),
    ) = ResumenViaje.de(puntos, previstoS, coincidenciaDe, historico, tramos)

    private fun historicoA(kmh: Double) =
        VelocidadVias
            .resumir(listOf(conducir(0.0, 1_000.0, kmh)), coincidenciaDe)
            .associateBy { it.via.id }

    @Test
    fun `distancia tiempo y media de un recorrido constante`() {
        val r = resumen(conducir(0.0, 1_000.0, 60.0))
        assertEquals(1_000.0, r.distanciaM, 20.0)
        assertTrue(r.tiempoTotalS in 59L..61L)
        assertEquals(r.tiempoTotalS, r.tiempoEnMarchaS)
        assertEquals(60.0, r.mediaEnMarchaKmh!!, 0.5)
        assertEquals(60.0, r.velocidadMaxKmh, 0.5)
    }

    @Test
    fun `una parada cuenta en el tiempo total pero no en marcha ni en la media`() {
        val ida = conducir(0.0, 500.0, 60.0)
        val parado = (1..30).map { PuntoTiempo(ida.last().punto, ida.last().tiempoMs + it * 1_000L) }
        val xParada = 29 * 60.0 / 3.6
        val vuelta = conducir(xParada, xParada + 500.0, 60.0, t0Ms = parado.last().tiempoMs)
        val r = resumen(ida + parado + vuelta.drop(1))
        assertTrue(r.tiempoTotalS - r.tiempoEnMarchaS in 29L..31L)
        assertEquals(60.0, r.mediaEnMarchaKmh!!, 1.0)
    }

    @Test
    fun `un pico aislado de GPS no cuenta como velocidad maxima`() {
        val base = conducir(0.0, 1_000.0, 60.0, conVelocidad = true)
        val conPico = base.mapIndexed { i, p -> if (i == 30) p.copy(velocidadMs = 55.0) else p }
        val r = resumen(conPico)
        assertTrue("max=${r.velocidadMaxKmh}", r.velocidadMaxKmh < 65.0)
    }

    @Test
    fun `una velocidad imposible se descarta aunque sea sostenida`() {
        val base = conducir(0.0, 1_000.0, 60.0, conVelocidad = true)
        val loco = base.map { it.copy(velocidadMs = 100.0) }
        assertEquals(0.0, resumen(loco).velocidadMaxKmh, 0.0)
    }

    @Test
    fun `la velocidad maxima real se mantiene`() {
        val lento = conducir(0.0, 400.0, 40.0, conVelocidad = true)
        val rapido = conducir(400.0, 1_400.0, 100.0, t0Ms = lento.last().tiempoMs, conVelocidad = true)
        assertEquals(100.0, resumen(lento + rapido.drop(1)).velocidadMaxKmh, 1.0)
    }

    @Test
    fun `la llegada compara lo real con lo previsto`() {
        val r = resumen(conducir(0.0, 1_000.0, 60.0), previstoS = 50L)
        assertEquals(50L, r.llegada.previstoS)
        assertEquals(r.tiempoTotalS - 50L, r.llegada.desviacionS)
        val sin = resumen(conducir(0.0, 1_000.0, 60.0))
        assertNull(sin.llegada.previstoS)
        assertNull(sin.llegada.desviacionS)
    }

    @Test
    fun `los segmentos del mapa llevan la velocidad de cada trozo y son contiguos`() {
        val lento = conducir(0.0, 500.0, 40.0)
        val rapido = conducir(500.0, 1_500.0, 80.0, t0Ms = lento.last().tiempoMs)
        val r = resumen(lento + rapido.drop(1))
        assertTrue(r.segmentos.size >= 8)
        assertEquals(40.0, r.segmentos.first().velocidadKmh, 2.0)
        assertEquals(80.0, r.segmentos.last().velocidadKmh, 2.0)
        r.segmentos.zipWithNext().forEach { (a, b) -> assertEquals(a.hastaM, b.desdeM, 1e-6) }
        assertTrue(r.segmentos.all { it.puntos.size >= 2 })
        assertEquals(r.segmentos.map { it.velocidadKmh }.min(), r.escala.cortesKmh.first(), 1e-9)
        assertEquals(r.segmentos.map { it.velocidadKmh }.max(), r.escala.cortesKmh.last(), 1e-9)
    }

    @Test
    fun `un salto de GPS corta los segmentos y no suma distancia`() {
        val a = conducir(0.0, 300.0, 60.0)
        val b = conducir(1_500.0, 1_800.0, 60.0, t0Ms = a.last().tiempoMs + 1_000L)
        val r = resumen(a + b)
        assertEquals(600.0, r.distanciaM, 25.0)
        r.segmentos.forEach { s -> assertTrue(s.puntos.zipWithNext().all { (p, q) -> p.distanciaA(q) < 100.0 }) }
    }

    @Test
    fun `las vias salen ordenadas por metros con su media`() {
        val ida = conducir(0.0, 1_000.0, 60.0)
        val resto = conducir(1_000.0, 1_500.0, 30.0, t0Ms = ida.last().tiempoMs)
        val r = resumen(ida + resto.drop(1))
        assertEquals(listOf("Ma-1", "Ma-2"), r.vias.map { it.etiqueta })
        assertTrue(r.vias[0].metros > r.vias[1].metros)
        assertEquals(60.0, r.vias[0].velMediaKmh!!, 1.0)
        assertEquals(30.0, r.vias[1].velMediaKmh!!, 1.0)
        assertNull(r.vias[0].velMediaHistoricaKmh)
        assertNull(r.vias[0].diferenciaPct)
    }

    @Test
    fun `las vias con la misma ref se juntan en una carretera`() {
        val partes = IndiceVias(listOf(rectaVia(1, 0.0, 1_000.0, "Ma-10"), rectaVia(2, 1_000.0, 2_000.0, "ma-10")))
        val r =
            ResumenViaje.de(
                conducir(0.0, 1_900.0, 60.0),
                null,
                { p, rumbo -> partes.cercana(p, rumbo) },
            )
        assertEquals(1, r.vias.size)
        assertEquals(1_900.0, r.vias.single().metros, 60.0)
    }

    @Test
    fun `sin ref se agrupa por nombre y una via anonima no sale`() {
        val a = rectaVia(1, 0.0, 1_000.0, ref = null, nombre = "Carrer Major")
        val b = rectaVia(2, 1_000.0, 2_000.0, ref = null, nombre = null)
        val idx = IndiceVias(listOf(a, b))
        val r = ResumenViaje.de(conducir(0.0, 1_900.0, 60.0), null, { p, rumbo -> idx.cercana(p, rumbo) })
        assertEquals(listOf("Carrer Major"), r.vias.map { it.etiqueta })
    }

    @Test
    fun `la diferencia con el historico es el porcentaje mas rapido o mas lento`() {
        val r = resumen(conducir(0.0, 1_000.0, 60.0), historico = historicoA(50.0))
        val via = r.vias.single()
        assertEquals(50.0, via.velMediaHistoricaKmh!!, 0.5)
        assertEquals(20.0, via.diferenciaPct!!, 1.0)
        val mas = resumen(conducir(0.0, 1_000.0, 40.0), historico = historicoA(50.0))
        assertEquals(-20.0, mas.vias.single().diferenciaPct!!, 1.0)
    }

    @Test
    fun `los tramos van con los records primero y luego por mejora`() {
        val tramos =
            listOf(
                TramoHecho("C", 90_000, 3_000, false),
                TramoHecho("D", 90_000, null, false),
                TramoHecho("B", 80_000, -500, false),
                TramoHecho("A", 70_000, -2_000, true),
            )
        val r = resumen(conducir(0.0, 1_000.0), tramos = tramos)
        assertEquals(listOf("A", "B", "C", "D"), r.tramos.map { it.nombre })
        assertEquals(1, r.records)
        assertEquals(
            2,
            resumen(conducir(0.0, 1_000.0), tramos = tramos.map { it.copy(esRecord = true) }.take(2)).records,
        )
    }

    @Test
    fun `conclusiones de records llegada y via con las frases esperadas`() {
        val tramos = listOf(TramoHecho("A", 1, -1, true), TramoHecho("B", 1, -1, true))
        val r =
            resumen(
                conducir(0.0, 1_000.0, 60.0),
                previstoS = 300L,
                historico = historicoA(56.6),
                tramos = tramos,
            )
        assertEquals(
            listOf("2 récords nuevos", "Llegaste 4 min antes de lo previsto", "Ma-1: 6 % más rápido que tu media"),
            r.conclusiones,
        )
    }

    @Test
    fun `un solo record nombra el tramo y llegar tarde lo dice`() {
        val r =
            resumen(
                conducir(0.0, 1_000.0, 60.0),
                previstoS = 0L,
                tramos = listOf(TramoHecho("Coll", 1, -1, true)),
            )
        assertEquals("Récord nuevo en Coll", r.conclusiones[0])
        assertEquals("Llegaste 1 min más tarde de lo previsto", r.conclusiones[1])
    }

    @Test
    fun `como mucho tres conclusiones`() {
        val dos = IndiceVias(listOf(viaA, viaB))
        val h = VelocidadVias.resumir(listOf(conducir(0.0, 2_000.0, 40.0)), { p, r -> dos.cercana(p, r) })
        val r =
            ResumenViaje.de(
                conducir(0.0, 1_990.0, 80.0),
                previstoS = 10L,
                coincidenciaDe = { p, rumbo -> dos.cercana(p, rumbo) },
                historicoVias = h.associateBy { it.via.id },
                tramos = listOf(TramoHecho("A", 1, -1, true)),
            )
        assertEquals(3, r.conclusiones.size)
    }

    @Test
    fun `sin previsto ni historico ni tramos queda una frase de recorrido`() {
        val r = resumen(conducir(0.0, 1_000.0, 60.0))
        assertEquals(1, r.conclusiones.size)
        assertTrue(r.conclusiones.single().startsWith("Recorrido de 1,0 km a 60 km/h"))
    }

    @Test
    fun `una diferencia pequena o una via corta no dan conclusion de via`() {
        val pequena = resumen(conducir(0.0, 1_000.0, 60.0), historico = historicoA(59.0))
        assertTrue(pequena.conclusiones.none { it.contains("que tu media") })
        val corta = rectaVia(1, 0.0, 300.0)
        val idx = IndiceVias(listOf(corta))
        val h = VelocidadVias.resumir(listOf(conducir(0.0, 300.0, 30.0)), { p, r -> idx.cercana(p, r) })
        val r =
            ResumenViaje.de(
                conducir(0.0, 300.0, 60.0),
                null,
                { p, rumbo -> idx.cercana(p, rumbo) },
                h.associateBy { it.via.id },
            )
        assertNotNull(r.vias.single().diferenciaPct)
        assertTrue(r.conclusiones.none { it.contains("que tu media") })
    }

    @Test
    fun `sin puntos el resumen esta vacio y no falla`() {
        for (puntos in listOf(emptyList(), conducir(0.0, 10.0).take(1))) {
            val r = resumen(puntos, previstoS = 100L)
            assertEquals(0.0, r.distanciaM, 0.0)
            assertEquals(0L, r.tiempoTotalS)
            assertNull(r.mediaEnMarchaKmh)
            assertEquals(0.0, r.velocidadMaxKmh, 0.0)
            assertTrue(r.segmentos.isEmpty())
            assertTrue(r.vias.isEmpty())
            assertEquals(-100L, r.llegada.desviacionS)
        }
    }

    @Test
    fun `es determinista`() {
        val puntos = conducir(0.0, 1_500.0, 70.0)
        assertEquals(resumen(puntos, 10L), resumen(puntos, 10L))
    }
}
