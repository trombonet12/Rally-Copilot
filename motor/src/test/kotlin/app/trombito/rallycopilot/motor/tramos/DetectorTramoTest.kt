package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.notas.TrazasDePrueba
import app.trombito.rallycopilot.motor.tramos.Conduccion.desplazar
import app.trombito.rallycopilot.motor.tramos.Conduccion.fijacion
import app.trombito.rallycopilot.motor.tramos.Conduccion.puntoA
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorTramoTest {
    /** Carretera recta de 6 km hacia el norte; el tramo guardado es el km 2 a 5. */
    private val carretera = TrazasDePrueba.recta(6000.0)
    private val tramo = tramoDe("mitad", Conduccion.subtraza(carretera, 2000.0, 5000.0))
    private var ahora = 0L

    private fun tramoDe(
        id: String,
        traza: List<PuntoGeo>,
    ) = Tramo(id, traza, traza.longitudMetros())

    private fun detector(vararg tramos: Tramo) = DetectorTramo(tramos.toList(), reloj = { ahora })

    private fun correr(
        d: DetectorTramo,
        fijaciones: List<Fijacion>,
    ): List<EventoTramo> =
        fijaciones.flatMap {
            ahora = it.tiempoMs
            d.alFijar(it)
        }

    @Test
    fun `entra en un tramo guardado aunque la conduccion empiece dos kilometros antes`() {
        val d = detector(tramo)
        val eventos = correr(d, Conduccion.muestrear(carretera, 72.0)) // 20 m/s: la salida se cruza a los 100 s
        val entrada = eventos.filterIsInstance<EventoTramo.Entrada>().single()
        assertEquals("mitad", entrada.tramoId)
        assertCerca(100_000, entrada.tInicioMs, 1.0)
        val completado = eventos.filterIsInstance<EventoTramo.Completado>().single()
        assertCerca(149_500, completado.pasada.totalMs, 1.0)
        assertEquals("mitad", completado.pasada.tramoId)
        assertEquals(3, eventos.filterIsInstance<EventoTramo.Corte>().count { it.corte is EventoCrono.CorteSector })
        assertEquals(2, eventos.filterIsInstance<EventoTramo.Corte>().count { it.corte is EventoCrono.CorteMicro })
        assertTrue(d.idsActivos.isEmpty())
    }

    @Test
    fun `si la conduccion empieza en la salida se entra en el primer instante`() {
        val d = detector(tramo)
        val fijaciones = Conduccion.muestrear(carretera, 72.0, t0Ms = 5_000, desdeM = 2000.0, hastaM = 5100.0)
        val eventos = correr(d, fijaciones)
        assertCerca(5_000, eventos.filterIsInstance<EventoTramo.Entrada>().single().tInicioMs, 1.0)
        assertCerca(
            149_500,
            eventos
                .filterIsInstance<EventoTramo.Completado>()
                .single()
                .pasada.totalMs,
            1.0,
        )
    }

    @Test
    fun `un desvio a mitad abandona el tramo sin pasada`() {
        val d = detector(tramo)
        val enTramo = Conduccion.muestrear(carretera, 72.0, hastaM = 3500.0)
        val tDesvio = enTramo.last().tiempoMs
        // Luego se sale hacia el este a 5 m/s: pasa de 50 m a los 10 s y de 5 s fuera a los 15 s.
        val salida = (1..60).map { fijacion(desplazar(puntoA(carretera, 3500.0), 0.0, 5.0 * it), tDesvio + it * 1000L) }
        val eventos = correr(d, enTramo + salida)
        assertEquals(1, eventos.filterIsInstance<EventoTramo.Abandono>().size)
        assertTrue(eventos.none { it is EventoTramo.Completado })
        assertTrue(d.idsActivos.isEmpty())
    }

    @Test
    fun `el desvio exige mas de cinco segundos fuera o 150 metros de golpe`() {
        val enTramo = Conduccion.muestrear(carretera, 72.0, hastaM = 3000.0)
        val t = enTramo.last().tiempoMs

        // 60 m fuera durante 4 s (fijaciones a 1 s): no se abandona y se completa.
        val breve = detector(tramo)
        val cola = Conduccion.muestrear(carretera, 72.0, t0Ms = t + 5_000, desdeM = 3100.0, hastaM = 5100.0)
        val fuera = (1..4).map { fijacion(desplazar(puntoA(carretera, 3000.0 + 20.0 * it), 0.0, 60.0), t + it * 1000L) }
        val eventosBreve = correr(breve, enTramo + fuera + cola)
        assertTrue(eventosBreve.none { it is EventoTramo.Abandono })
        assertEquals(1, eventosBreve.filterIsInstance<EventoTramo.Completado>().size)

        // 200 m fuera de golpe: abandono inmediato.
        val brusco = detector(tramo)
        val lejos = fijacion(desplazar(puntoA(carretera, 3050.0), 0.0, 200.0), t + 1_000)
        val eventosBrusco = correr(brusco, enTramo + lejos)
        assertEquals(1, eventosBrusco.filterIsInstance<EventoTramo.Abandono>().size)
    }

    @Test
    fun `en sentido contrario no entra`() {
        val d = detector(tramo)
        val eventos = correr(d, Conduccion.muestrear(carretera.reversed(), 72.0))
        assertTrue(eventos.isEmpty())
        assertTrue(d.idsActivos.isEmpty())
    }

    @Test
    fun `el rumbo del gps manda sobre la posicion`() {
        val d = detector(tramo)
        val norte = Conduccion.muestrear(carretera, 72.0, hastaM = 2100.0)
        val hacia = norte.map { it.copy(velocidadMs = 20.0, rumboGrados = 90.0) }
        assertTrue(correr(d, hacia).isEmpty())
    }

    @Test
    fun `varios candidatos a la vez y el que no sigue se abandona`() {
        val salida = puntoA(carretera, 2000.0)
        val gira = listOf(salida, desplazar(salida, 1000.0, 0.0), desplazar(salida, 1000.0, 3000.0))
        val d = detector(tramoDe("recto", Conduccion.subtraza(carretera, 2000.0, 5000.0)), tramoDe("gira", gira))
        val eventos = correr(d, Conduccion.muestrear(carretera, 72.0, desdeM = 1500.0, hastaM = 5100.0))
        assertEquals(setOf("recto", "gira"), eventos.filterIsInstance<EventoTramo.Entrada>().map { it.tramoId }.toSet())
        assertEquals(listOf("gira"), eventos.filterIsInstance<EventoTramo.Abandono>().map { it.tramoId })
        assertEquals(listOf("recto"), eventos.filterIsInstance<EventoTramo.Completado>().map { it.tramoId })
    }

    @Test
    fun `el cronometro en vivo del candidato esta disponible`() {
        val d = detector(tramo)
        correr(d, Conduccion.muestrear(carretera, 72.0, hastaM = 3000.0))
        val c = d.cronometro("mitad")!!
        assertEquals(1000.0, c.progresoM, 1.0)
        assertEquals(setOf("mitad"), d.idsActivos)
    }
}
