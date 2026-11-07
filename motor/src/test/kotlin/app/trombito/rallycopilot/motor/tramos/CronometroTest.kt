package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.notas.TrazasDePrueba
import app.trombito.rallycopilot.motor.tramos.Conduccion.fijacion
import app.trombito.rallycopilot.motor.tramos.Conduccion.puntoA
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CronometroTest {
    private val traza = TrazasDePrueba.recta(4000.0)
    private val tramo = Tramo("recta", traza, traza.longitudMetros())
    private var ahora = 0L
    private val crono = Cronometro(tramo, reloj = { ahora })

    private fun correr(fijaciones: List<Fijacion>): List<EventoCrono> =
        fijaciones.flatMap { f ->
            ahora = f.tiempoMs
            crono.alFijar(f)
        }

    private fun tiempoDeSector(
        eventos: List<EventoCrono>,
        numero: Int,
    ) = eventos.filterIsInstance<EventoCrono.CorteSector>().first { it.numero == numero }.tMs

    @Test
    fun `recorrido completo a velocidad constante da el total y los cortes de sector`() {
        val eventos = correr(Conduccion.muestrear(traza, kmh = 108.0, periodoMs = 3000)) // 30 m/s, 90 m por fijación
        assertEquals(Cronometro.Estado.TERMINADO, crono.estado)
        val meta = eventos.filterIsInstance<EventoCrono.Meta>().single()
        assertCerca(133_000L, meta.tMs, 1.0)
        assertCerca(33_333L, tiempoDeSector(eventos, 1), 1.0)
        assertCerca(66_667L, tiempoDeSector(eventos, 2), 1.0)
        assertCerca(100_000L, tiempoDeSector(eventos, 3), 1.0)
        assertEquals(3, eventos.filterIsInstance<EventoCrono.CorteSector>().size)
        assertEquals(3, eventos.filterIsInstance<EventoCrono.CorteMicro>().size)
        assertEquals(tramo.metaM, meta.distanciaM, 1e-6)
    }

    @Test
    fun `interpola el instante exacto del cruce entre dos fijaciones`() {
        // 0 m a 0 s, 900 m a 45 s, 1100 m a 50 s: el corte de 1000 m cae justo a la mitad del segundo tramo.
        val fijaciones =
            listOf(0.0 to 0L, 900.0 to 45_000L, 1100.0 to 50_000L).map { (d, t) -> fijacion(puntoA(traza, d), t) }
        val eventos = correr(fijaciones)
        assertEquals(47_500L, tiempoDeSector(eventos, 1))
        assertEquals(47_500L, eventos.filterIsInstance<EventoCrono.CorteMicro>().single().tMs)
    }

    @Test
    fun `la salida se interpola entre la ultima fijacion antes y la primera despues`() {
        // A 50 m antes de la salida a 0 s y a 50 m después a 10 s (sobre la prolongación de la recta).
        val antes = TrazasDePrueba.enPlano(listOf(0.0 to -50.0))[0]
        val eventos = correr(listOf(fijacion(antes, 5_000), fijacion(puntoA(traza, 50.0), 15_000)))
        val salida = eventos.filterIsInstance<EventoCrono.Salida>().single()
        assertEquals(10_000L, salida.tInicioMs)
        assertEquals(Cronometro.Estado.EN_CURSO, crono.estado)
    }

    @Test
    fun `si la primera fijacion ya esta en la salida el cronometro arranca en ella`() {
        val eventos = correr(listOf(fijacion(puntoA(traza, 0.0), 7_000)))
        assertEquals(7_000L, eventos.filterIsInstance<EventoCrono.Salida>().single().tInicioMs)
    }

    @Test
    fun `en espera no corre y transcurrido usa el reloj inyectado`() {
        ahora = 9_999
        assertEquals(0L, crono.transcurridoMs())
        correr(listOf(fijacion(puntoA(traza, 0.0), 1_000)))
        ahora = 4_500
        assertEquals(3_500L, crono.transcurridoMs())
    }

    @Test
    fun `tolera ruido lateral y vueltas atras pequenas sin duplicar cortes`() {
        val fijaciones =
            listOf(0.0, 400.0, 995.0, 990.0, 1010.0, 1005.0, 1500.0).mapIndexed { i, d ->
                fijacion(Conduccion.desplazar(puntoA(traza, d), 0.0, if (i % 2 == 0) 4.0 else -4.0), i * 10_000L)
            }
        val eventos = correr(fijaciones)
        assertEquals(1, eventos.filterIsInstance<EventoCrono.CorteSector>().size)
        val t = tiempoDeSector(eventos, 1)
        assertTrue("corte a $t ms", t in 20_000L..50_000L)
        assertTrue(crono.progresoM in 1499.0..1501.0)
    }

    @Test
    fun `parado no cierra nada y el tiempo parado cuenta en el total`() {
        val antes = Conduccion.muestrear(traza, 72.0, hastaM = 2000.0) // 20 m/s -> 100 s
        correr(antes)
        val parado = (1..600).map { fijacion(puntoA(traza, 2000.0), 100_000L + it * 1000L) } // 10 min quieto
        assertTrue(correr(parado).isEmpty())
        assertEquals(Cronometro.Estado.EN_CURSO, crono.estado)
        val despues = Conduccion.muestrear(traza, 72.0, t0Ms = 700_000, desdeM = 2000.0)
        val total = correr(despues).filterIsInstance<EventoCrono.Meta>().single().tMs
        assertCerca(700_000L + 99_500L, total, 1.0)
        assertEquals(total, crono.pasada()?.totalMs)
    }

    @Test
    fun `ignora fijaciones lejos de la traza`() {
        correr(listOf(fijacion(puntoA(traza, 0.0), 0), fijacion(puntoA(traza, 500.0), 25_000)))
        val lejos = Conduccion.desplazar(puntoA(traza, 3000.0), 0.0, 200.0)
        assertTrue(correr(listOf(fijacion(lejos, 30_000))).isEmpty())
        assertTrue(crono.lateralM!! > 100.0)
        assertEquals(500.0, crono.progresoM, 0.5)
    }

    @Test
    fun `sectores y microsectores configurables`() {
        val propio = tramo.copy(sectores = listOf(500.0, 3500.0), microsectores = listOf(250.0, 1000.0, 1000.5))
        val c = Cronometro(propio, reloj = { ahora })
        val eventos = Conduccion.muestrear(traza, 72.0).flatMap { c.alFijar(it) }
        assertEquals(listOf(1, 2), eventos.filterIsInstance<EventoCrono.CorteSector>().map { it.numero })
        assertEquals(listOf(1, 2, 3), eventos.filterIsInstance<EventoCrono.CorteMicro>().map { it.numero })
        assertEquals(25_000L, eventos.filterIsInstance<EventoCrono.CorteSector>()[0].tMs)
    }

    @Test
    fun `la pasada recoge la curva distancia tiempo para interpolar`() {
        correr(Conduccion.muestrear(traza, 72.0))
        val pasada = assertNotNull_(crono.pasada())
        assertEquals(tramo.id, pasada.tramoId)
        assertEquals(0L, pasada.tiempos.first().tMs)
        assertEquals(pasada.totalMs, pasada.tiempos.last().tMs)
        assertEquals(tramo.metaM, pasada.tiempos.last().distanciaM, 1e-6)
        assertEquals(50_000.0, pasada.tiempoEn(1000.0)!!, 1.0)
        assertEquals(125_000.0, pasada.tiempoEn(2500.0)!!, 1.0)
        assertNull(pasada.tiempoEn(4500.0))
    }

    @Test
    fun `la meta esta 10 m antes del final y parar 5 m antes cierra la pasada`() {
        assertEquals(3990.0, tramo.metaM, 1e-6)
        val eventos = correr(Conduccion.muestrear(traza, 72.0, hastaM = 3995.0))
        assertEquals(Cronometro.Estado.TERMINADO, crono.estado)
        assertCerca(199_500L, eventos.filterIsInstance<EventoCrono.Meta>().single().tMs, 1.0)
    }

    @Test
    fun `sin terminar no hay pasada`() {
        correr(Conduccion.muestrear(traza, 72.0, hastaM = 1000.0))
        assertNull(crono.pasada())
    }

    // --- Pérdida de GPS: huecos largos, tiempo que retrocede y fijaciones repetidas ------------------------------

    private fun fijaciones(vararg pares: Pair<Double, Long>) = pares.map { (d, t) -> fijacion(puntoA(traza, d), t) }

    @Test
    fun `un hueco de 20 s que cruza dos sectores interpola cada corte una sola vez y en orden`() {
        // Túnel: 900 m a 18 s y 2100 m a 38 s (60 m/s) sin fijaciones intermedias; cruza los cortes de 1000 y 2000 m.
        val eventos = correr(fijaciones(0.0 to 0L, 900.0 to 18_000L, 2100.0 to 38_000L))
        val sectores = eventos.filterIsInstance<EventoCrono.CorteSector>()
        val micros = eventos.filterIsInstance<EventoCrono.CorteMicro>()
        assertEquals(listOf(1, 2), sectores.map { it.numero })
        assertEquals(listOf(1, 2), micros.map { it.numero })
        assertCerca(19_667L, sectores[0].tMs, 1.0)
        assertCerca(36_333L, sectores[1].tMs, 1.0)
        val cortes = eventos.filter { it !is EventoCrono.Salida }
        assertEquals(listOf(1000L, 1000L, 2000L, 2000L), cortes.map { Math.round(it.distanciaM) })
        // Tras el hueco el resto del recorrido sigue sin repetir cortes ni perder la meta.
        val resto = correr(Conduccion.muestrear(traza, 72.0, t0Ms = 38_000, desdeM = 2100.0))
        assertEquals(listOf(3), resto.filterIsInstance<EventoCrono.CorteSector>().map { it.numero })
        assertEquals(1, resto.filterIsInstance<EventoCrono.Meta>().size)
        assertEquals(Cronometro.Estado.TERMINADO, crono.estado)
    }

    @Test
    fun `un hueco que cruza la meta cierra la pasada con el tiempo interpolado`() {
        // 3500 m a 60 s y 4000 m (pasada la meta, 3990 m) a 70 s: la meta cae a 3990 m, o sea a 69,8 s.
        val eventos = correr(fijaciones(0.0 to 0L, 3500.0 to 60_000L, 4000.0 to 70_000L))
        assertEquals(69_800L, eventos.filterIsInstance<EventoCrono.Meta>().single().tMs)
        assertEquals(1, eventos.filterIsInstance<EventoCrono.CorteSector>().count { it.numero == 3 })
    }

    @Test
    fun `una fijacion con el tiempo hacia atras se ignora y no estropea lo ya registrado`() {
        correr(fijaciones(0.0 to 0L, 500.0 to 25_000L))
        val marcasAntes = crono.marcas()
        // El reloj del GPS salta atrás: 1500 m «a 20 s», anterior a la fijación de 25 s.
        assertTrue(correr(fijaciones(1500.0 to 20_000L)).isEmpty())
        assertEquals(500.0, crono.progresoM, 1e-6)
        assertEquals(marcasAntes, crono.marcas())
        // Al volver el reloj a la normalidad se continúa desde donde estaba.
        val eventos = correr(fijaciones(1500.0 to 60_000L))
        assertEquals(1, eventos.filterIsInstance<EventoCrono.CorteSector>().single().numero)
        assertEquals(42_500L, tiempoDeSector(eventos, 1))
        assertTrue(crono.progresoM in 1499.0..1501.0)
    }

    @Test
    fun `una fijacion duplicada no repite cortes ni marcas`() {
        val cruce = fijaciones(0.0 to 0L, 1100.0 to 50_000L)
        val primeras = correr(cruce)
        assertEquals(1, primeras.filterIsInstance<EventoCrono.CorteSector>().size)
        val marcas = crono.marcas()
        assertTrue(correr(cruce.takeLast(1)).isEmpty())
        assertTrue(correr(cruce.takeLast(1)).isEmpty())
        assertEquals(marcas, crono.marcas())
        assertEquals(Cronometro.Estado.EN_CURSO, crono.estado)
    }

    @Test
    fun `propiedad con huecos y relojes erraticos el total es monotono y los cortes salen ordenados`() {
        for (semilla in 1..40) {
            val azar = Random(semilla)
            val c = Cronometro(tramo, reloj = { ahora })
            var distancia = 0.0
            var t = 0L
            val eventos = mutableListOf<EventoCrono>()
            eventos += c.alFijar(fijacion(puntoA(traza, 0.0), 0L))
            repeat(400) {
                distancia = (distancia + azar.nextDouble(-20.0, 600.0)).coerceIn(0.0, 4000.0)
                t += azar.nextLong(-5_000L, 25_000L) // incluye retrocesos y huecos de más de 20 s
                eventos += c.alFijar(fijacion(puntoA(traza, distancia), t.coerceAtLeast(0L)))
            }
            val ctx = "semilla $semilla"
            val marcas = c.marcas()
            assertTrue(ctx, marcas.zipWithNext().all { (a, b) -> b.distanciaM > a.distanciaM && b.tMs >= a.tMs })
            assertTrue(ctx, eventos.zipWithNext().all { (a, b) -> b.distanciaM >= a.distanciaM && b.tMs >= a.tMs })
            val sectores = eventos.filterIsInstance<EventoCrono.CorteSector>().map { it.numero }
            assertEquals(ctx, sectores.distinct(), sectores)
            assertTrue(ctx, eventos.filterIsInstance<EventoCrono.Meta>().size <= 1)
            c.pasada()?.let { assertEquals(ctx, it.totalMs, marcas.last().tMs) }
        }
    }

    private fun <T> assertNotNull_(valor: T?): T {
        assertNotNull(valor)
        return valor!!
    }
}
