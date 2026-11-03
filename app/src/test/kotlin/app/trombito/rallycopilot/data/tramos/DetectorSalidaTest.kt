package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectorSalidaTest {
    private val inicio = PuntoGeo(39.7, 2.9)

    /** ~[metros] metros al norte de [inicio] (1 grado de latitud = 111 195 m). */
    private fun al(metros: Double) = PuntoGeo(inicio.lat + metros / METROS_POR_GRADO, inicio.lon)

    private fun fijacion(
        t: Long,
        kmh: Double?,
        metros: Double = 0.0,
        precisionM: Double? = 5.0,
    ) = Fijacion(al(metros), t, kmh?.let { it / 3.6 }, null, precisionM)

    @Test
    fun `parado no hay salida`() {
        val d = DetectorSalida()
        (0..60).forEach { assertNull(d.alFijar(fijacion(it * 1000L, 0.0))) }
        assertNull(d.salidaMs)
    }

    @Test
    fun `sin velocidad conocida y sin moverse no hay salida`() {
        val d = DetectorSalida()
        (0..10).forEach { assertNull(d.alFijar(fijacion(it * 1000L, null))) }
    }

    @Test
    fun `mas de 15 km por hora sostenidos 3 segundos son la salida, desde que empezo la racha`() {
        val d = DetectorSalida()
        assertNull(d.alFijar(fijacion(0, 0.0)))
        assertNull(d.alFijar(fijacion(10_000, 20.0)))
        assertNull(d.alFijar(fijacion(11_000, 20.0)))
        assertNull(d.alFijar(fijacion(12_000, 20.0)))
        assertEquals(10_000L, d.alFijar(fijacion(13_000, 20.0)))
        // Ya no cambia.
        assertEquals(10_000L, d.alFijar(fijacion(14_000, 40.0)))
    }

    @Test
    fun `15 km por hora o menos no cuenta y un paron corta la racha`() {
        val d = DetectorSalida()
        assertNull(d.alFijar(fijacion(0, 14.9)))
        assertNull(d.alFijar(fijacion(3_000, 14.9)))
        assertNull(d.alFijar(fijacion(4_000, 20.0)))
        assertNull(d.alFijar(fijacion(5_000, 0.0)))
        assertNull(d.alFijar(fijacion(6_000, 20.0)))
        assertNull(d.alFijar(fijacion(8_000, 20.0)))
        assertEquals(6_000L, d.alFijar(fijacion(9_000, 20.0)))
    }

    @Test
    fun `mas de 50 metros desde la primera posicion son la salida aunque la velocidad no se sepa`() {
        val d = DetectorSalida()
        assertNull(d.alFijar(fijacion(0, null)))
        assertNull(d.alFijar(fijacion(1_000, null, metros = 45.0)))
        assertEquals(2_000L, d.alFijar(fijacion(2_000, null, metros = 55.0)))
    }

    @Test
    fun `el ruido del GPS dentro de 50 metros no arranca el cronometro`() {
        val d = DetectorSalida()
        listOf(0.0, 8.0, -6.0, 12.0, -9.0, 30.0, -20.0, 45.0).forEachIndexed { i, m ->
            assertNull(d.alFijar(fijacion(i * 1000L, 1.0, metros = m)))
        }
    }

    @Test
    fun `la deriva del GPS en parado con saltos y 8 km por hora no es salida`() {
        val d = DetectorSalida()
        (0..120).forEach { i ->
            val salto = if (i % 2 == 0) 0.0 else 30.0
            assertNull(d.alFijar(fijacion(i * 1000L, 8.0, metros = salto, precisionM = 15.0)))
        }
        assertNull(d.salidaMs)
    }

    @Test
    fun `con precision peor de 20 metros la velocidad no cuenta`() {
        val d = DetectorSalida()
        (0..20).forEach { assertNull(d.alFijar(fijacion(it * 1000L, 20.0, precisionM = 40.0))) }
        assertNull(d.salidaMs)
    }

    @Test
    fun `sin precision la fijacion no cuenta`() {
        val d = DetectorSalida()
        (0..20).forEach { assertNull(d.alFijar(fijacion(it * 1000L, 20.0, metros = it * 10.0, precisionM = null))) }
        assertNull(d.salidaMs)
    }

    @Test
    fun `una fijacion imprecisa no es el origen y el origen es la primera valida`() {
        val d = DetectorSalida()
        // La imprecisa a 200 m no fija el origen; el origen es la siguiente, en 0 m.
        assertNull(d.alFijar(fijacion(0, 0.0, metros = 200.0, precisionM = 60.0)))
        assertNull(d.alFijar(fijacion(1_000, 0.0, metros = 0.0)))
        assertNull(d.alFijar(fijacion(2_000, 0.0, metros = 40.0)))
        assertEquals(3_000L, d.alFijar(fijacion(3_000, 0.0, metros = 60.0)))
    }

    @Test
    fun `20 km por hora durante 3 segundos con buena precision son la salida en el inicio de la racha`() {
        val d = DetectorSalida()
        assertNull(d.alFijar(fijacion(0, 0.0, precisionM = 10.0)))
        assertNull(d.alFijar(fijacion(1_000, 20.0, precisionM = 10.0)))
        assertNull(d.alFijar(fijacion(2_000, 20.0, precisionM = 10.0)))
        assertNull(d.alFijar(fijacion(3_000, 20.0, precisionM = 10.0)))
        assertEquals(1_000L, d.alFijar(fijacion(4_000, 20.0, precisionM = 10.0)))
    }

    @Test
    fun `60 metros con precision de 10 metros son la salida`() {
        val d = DetectorSalida()
        assertNull(d.alFijar(fijacion(0, 0.0, precisionM = 10.0)))
        assertEquals(1_000L, d.alFijar(fijacion(1_000, 0.0, metros = 60.0, precisionM = 10.0)))
    }

    private companion object {
        const val METROS_POR_GRADO = 111_194.9
    }
}
