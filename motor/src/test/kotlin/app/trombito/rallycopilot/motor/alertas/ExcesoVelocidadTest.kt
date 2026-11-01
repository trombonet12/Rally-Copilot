package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.voz.TipoFrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcesoVelocidadTest {
    private val exceso = ExcesoVelocidad()

    private fun paso(
        v: Double,
        limite: Int?,
        ms: Long,
    ) = exceso.actualizar(v, limite, ms)

    @Test
    fun `sin limite no hay rojo ni exceso`() {
        val e = paso(200.0, null, 0)
        assertFalse(e.rojo)
        assertFalse(e.exceso)
        assertNull(e.frase)
    }

    @Test
    fun `rojo solo por encima del limite exacto`() {
        assertFalse(paso(79.0, 80, 0).rojo)
        assertFalse(paso(80.0, 80, 1000).rojo)
        assertTrue(paso(80.5, 80, 2000).rojo)
        assertTrue(paso(81.0, 80, 3000).rojo)
    }

    @Test
    fun `entre el limite y el diez por ciento hay rojo pero ni exceso ni voz`() {
        val e = paso(85.0, 80, 0)
        assertTrue(e.rojo)
        assertFalse(e.exceso)
        assertNull(e.frase)
    }

    @Test
    fun `el diez por ciento exacto no es exceso y un poco mas si`() {
        assertFalse(paso(88.0, 80, 0).exceso)
        assertNull(exceso.actualizar(88.0, 80, 1000).frase)
        assertTrue(paso(88.1, 80, 2000).exceso)
        // Otros limites donde 1,1 · limite no es exacto en coma flotante.
        assertFalse(ExcesoVelocidad().actualizar(33.0, 30, 0).exceso)
        assertFalse(ExcesoVelocidad().actualizar(99.0, 90, 0).exceso)
        assertFalse(ExcesoVelocidad().actualizar(110.0, 100, 0).exceso)
        assertFalse(ExcesoVelocidad().actualizar(55.0, 50, 0).exceso)
        assertTrue(ExcesoVelocidad().actualizar(55.1, 50, 0).exceso)
    }

    @Test
    fun `el exceso trae la frase con el limite`() {
        val e = paso(95.0, 80, 0)
        val frase = e.frase!!
        assertEquals("exceso de velocidad, límite ochenta", frase.texto)
        assertEquals("exceso-1", frase.id)
        assertEquals(TipoFrase.ALERTA, frase.tipo)
        assertNull(frase.puntoM)
    }

    @Test
    fun `la frase sigue pendiente con el mismo id mientras dure el exceso y se retira al bajar`() {
        assertEquals("exceso-1", paso(95.0, 80, 0).frase!!.id)
        assertEquals("exceso-1", paso(96.0, 80, 1000).frase!!.id)
        assertNull(paso(86.0, 80, 2000).frase)
    }

    @Test
    fun `una vez por tramo de mismo limite aunque el exceso se corte un momento`() {
        paso(95.0, 80, 0)
        paso(85.0, 80, 1000) // rojo, sin exceso
        assertNull(paso(95.0, 80, 2000).frase)
        assertNull(paso(100.0, 80, 3000).frase)
    }

    @Test
    fun `se rearma tras 10 s o mas por debajo del limite`() {
        paso(95.0, 80, 0)
        for (ms in 1000L..11_000L step 1000) assertFalse(paso(70.0, 80, ms).rojo)
        val e = paso(95.0, 80, 12_000)
        assertEquals("exceso-2", e.frase!!.id)
    }

    @Test
    fun `9,9 s por debajo del limite no bastan`() {
        paso(95.0, 80, 0)
        paso(70.0, 80, 1000)
        paso(70.0, 80, 10_999)
        assertNull(paso(95.0, 80, 11_000).frase)
    }

    @Test
    fun `justo 10 s por debajo rearma en esa misma muestra`() {
        paso(95.0, 80, 0)
        paso(70.0, 80, 1000)
        paso(70.0, 80, 11_000)
        assertEquals("exceso-2", paso(95.0, 80, 11_500).frase!!.id)
    }

    @Test
    fun `subir al rojo sin exceso corta el tiempo por debajo del limite`() {
        paso(95.0, 80, 0)
        paso(70.0, 80, 1000)
        paso(82.0, 80, 6000) // vuelve al rojo: el conteo de 10 s empieza de nuevo
        paso(70.0, 80, 7000)
        paso(70.0, 80, 12_000)
        assertNull(paso(95.0, 80, 12_500).frase)
    }

    @Test
    fun `cambiar de limite rearma`() {
        assertEquals("límite ochenta", paso(95.0, 80, 0).frase!!.texto.substringAfter(", "))
        val nuevo = paso(60.0, 50, 1000).frase!!
        assertEquals("exceso de velocidad, límite cincuenta", nuevo.texto)
        assertEquals("exceso-2", nuevo.id)
        assertEquals("exceso-3", paso(95.0, 80, 2000).frase!!.id)
    }

    @Test
    fun `un hueco sin limite no rearma ni inventa otro aviso`() {
        paso(95.0, 80, 0)
        val hueco = paso(95.0, null, 1000)
        assertFalse(hueco.rojo)
        assertNull(hueco.frase)
        // Al volver el mismo limite sigue el mismo aviso (el planificador ya lo dio por dicho si sonó).
        assertEquals("exceso-1", paso(95.0, 80, 2000).frase!!.id)
    }

    @Test
    fun `velocidades raras no avisan ni rompen`() {
        assertFalse(paso(Double.NaN, 80, 0).rojo)
        assertFalse(paso(-5.0, 80, 1000).rojo)
        assertFalse(paso(Double.POSITIVE_INFINITY, 80, 2000).exceso)
    }
}
