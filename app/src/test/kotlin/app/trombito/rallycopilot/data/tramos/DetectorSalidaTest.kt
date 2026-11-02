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
    ) = Fijacion(al(metros), t, kmh?.let { it / 3.6 }, null, 5.0)

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
    fun `mas de 5 km por hora sostenidos 2 segundos son la salida, desde que empezo la racha`() {
        val d = DetectorSalida()
        assertNull(d.alFijar(fijacion(0, 0.0)))
        assertNull(d.alFijar(fijacion(10_000, 6.0)))
        assertNull(d.alFijar(fijacion(11_000, 6.0)))
        assertEquals(10_000L, d.alFijar(fijacion(12_000, 6.0)))
        // Ya no cambia.
        assertEquals(10_000L, d.alFijar(fijacion(13_000, 40.0)))
    }

    @Test
    fun `exactamente 5 km por hora no cuenta y un parón corta la racha`() {
        val d = DetectorSalida()
        assertNull(d.alFijar(fijacion(0, 5.0)))
        assertNull(d.alFijar(fijacion(2_000, 5.0)))
        assertNull(d.alFijar(fijacion(3_000, 8.0)))
        assertNull(d.alFijar(fijacion(4_000, 0.0)))
        assertNull(d.alFijar(fijacion(5_000, 8.0)))
        assertEquals(5_000L, d.alFijar(fijacion(7_000, 8.0)))
    }

    @Test
    fun `mas de 20 metros desde la primera posicion son la salida aunque la velocidad no se sepa`() {
        val d = DetectorSalida()
        assertNull(d.alFijar(fijacion(0, null)))
        assertNull(d.alFijar(fijacion(1_000, null, metros = 15.0)))
        assertEquals(2_000L, d.alFijar(fijacion(2_000, null, metros = 25.0)))
    }

    @Test
    fun `el ruido del GPS dentro de 20 metros no arranca el cronometro`() {
        val d = DetectorSalida()
        listOf(0.0, 8.0, -6.0, 12.0, -9.0, 15.0).forEachIndexed { i, m ->
            assertNull(d.alFijar(fijacion(i * 1000L, 1.0, metros = m)))
        }
    }

    private companion object {
        const val METROS_POR_GRADO = 111_194.9
    }
}
