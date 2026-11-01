package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.notas.TrazasDePrueba
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Criterios de aceptación de H6 con la traza real de Sa Calobra (docs/rutas/ma-2141-sa-calobra.gpx). */
class TramosCriteriosTest {
    private val calobra: List<PuntoGeo> = TrazasDePrueba.cargarGpx("ma-2141-sa-calobra.gpx")
    private val tramo = TramoDesdeRecorrido.crear("calobra", calobra)!!

    private fun pasada(
        tramo: Tramo,
        fijaciones: List<Fijacion>,
    ): Pasada {
        var ahora = 0L
        val crono = Cronometro(tramo, reloj = { ahora })
        fijaciones.forEach {
            ahora = it.tiempoMs
            crono.alFijar(it)
        }
        return assertNotNull_(crono.pasada())
    }

    private fun <T> assertNotNull_(valor: T?): T {
        assertNotNull("la pasada debía terminar", valor)
        return valor!!
    }

    @Test
    fun `la traza de Calobra es un tramo valido`() {
        assertTrue(tramo.longitudM > 3000.0)
        assertEquals(4, tramo.sectores.size + 1)
        assertEquals(
            tramo.microsectores.size,
            (tramo.longitudM / 1000.0).toInt() - if (tramo.longitudM % 1000.0 == 0.0) 1 else 0,
        )
    }

    @Test
    fun `la misma traza a dos velocidades da delta negativo en la rapida sector a sector`() {
        val lenta = pasada(tramo, Conduccion.muestrear(calobra, 40.0))
        val rapida = pasada(tramo, Conduccion.muestrear(calobra, 60.0))
        assertTrue(rapida.totalMs < lenta.totalMs)

        val sectores = Delta.parciales(tramo.sectores, tramo.metaM, rapida, lenta)
        assertEquals(4, sectores.size)
        sectores.forEach {
            assertTrue("sector ${it.numero}: ${it.diferenciaMs}", it.diferenciaMs < 0)
            assertTrue("acumulado ${it.numero}: ${it.deltaAcumuladoMs}", it.deltaAcumuladoMs < 0)
        }
        val micros = Delta.parciales(tramo.microsectores, tramo.metaM, rapida, lenta)
        assertEquals(tramo.microsectores.size + 1, micros.size)
        micros.forEach { assertTrue("micro ${it.numero}: ${it.diferenciaMs}", it.diferenciaMs < 0) }
        assertTrue(Delta.enDistancia(rapida, lenta, tramo.metaM)!! < 0)
        // Los tiempos medidos son los esperados a velocidad constante (±1 s por la curva simplificada).
        assertEquals((calobra.longitudMetros() - Tramo.META_ANTES_M) / (40.0 / 3.6), lenta.totalMs / 1000.0, 2.0)
        assertEquals((calobra.longitudMetros() - Tramo.META_ANTES_M) / (60.0 / 3.6), rapida.totalMs / 1000.0, 2.0)
    }

    @Test
    fun `una traza que entra a mitad del tramo guardado lo recorre entero y se compara`() {
        val total = calobra.longitudMetros()
        val recorrido = Conduccion.subtraza(calobra, 2000.0, total - 1500.0)
        val guardado = TramoDesdeRecorrido.crear("mitad", recorrido)!!
        val mejor = pasada(guardado, Conduccion.muestrear(guardado.traza, 40.0))

        val detector = DetectorTramo(listOf(guardado), reloj = { 0L })
        val eventos = Conduccion.muestrear(calobra, 60.0).flatMap { detector.alFijar(it) }

        val entrada = eventos.filterIsInstance<EventoTramo.Entrada>().single()
        assertEquals(2000.0 / (60.0 / 3.6), entrada.tInicioMs / 1000.0, 1.0)
        val completado = eventos.filterIsInstance<EventoTramo.Completado>().single().pasada
        val esperadoMs = (recorrido.longitudMetros() - Tramo.META_ANTES_M) / (60.0 / 3.6) * 1000.0
        assertEquals(esperadoMs, completado.totalMs.toDouble(), 1000.0)
        // Se compara con la mejor guardada: más rápida en todos los sectores.
        val sectores = Delta.parciales(guardado.sectores, guardado.metaM, completado, mejor)
        assertEquals(4, sectores.size)
        sectores.forEach { assertTrue("sector ${it.numero}: ${it.diferenciaMs}", it.diferenciaMs < 0) }
        assertTrue(Delta.enDistancia(completado, mejor, guardado.metaM)!! < 0)
    }
}
