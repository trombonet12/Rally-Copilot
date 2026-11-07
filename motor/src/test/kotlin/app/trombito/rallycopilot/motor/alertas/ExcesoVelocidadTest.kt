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

    /** Una fijación por segundo con [v] entre [desdeMs] y [hastaMs] (ambos incluidos); devuelve la última. */
    private fun sostener(
        v: Double,
        limite: Int,
        desdeMs: Long,
        hastaMs: Long,
    ): EstadoExceso {
        var estado = paso(v, limite, desdeMs)
        for (ms in (desdeMs + 1000)..hastaMs step 1000) estado = paso(v, limite, ms)
        return estado
    }

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
        val e = sostener(85.0, 80, 0, 30_000)
        assertTrue(e.rojo)
        assertFalse(e.exceso)
        assertNull(e.frase)
    }

    @Test
    fun `el diez por ciento exacto no es exceso y un poco mas si`() {
        assertFalse(paso(88.0, 80, 0).exceso)
        assertNull(sostener(88.0, 80, 1000, 20_000).frase)
        assertTrue(paso(88.1, 80, 21_000).exceso)
        // Otros limites donde 1,1 · limite no es exacto en coma flotante.
        assertFalse(ExcesoVelocidad().actualizar(33.0, 30, 0).exceso)
        assertFalse(ExcesoVelocidad().actualizar(99.0, 90, 0).exceso)
        assertFalse(ExcesoVelocidad().actualizar(110.0, 100, 0).exceso)
        assertFalse(ExcesoVelocidad().actualizar(55.0, 50, 0).exceso)
        assertTrue(ExcesoVelocidad().actualizar(55.1, 50, 0).exceso)
    }

    @Test
    fun `el exceso sostenido trae la frase corta con el limite`() {
        val frase = sostener(95.0, 80, 0, 4000).frase!!
        assertEquals("límite ochenta", frase.texto)
        assertEquals("exceso-1", frase.id)
        assertEquals(TipoFrase.AVISO_VELOCIDAD, frase.tipo)
        assertNull(frase.puntoM)
    }

    @Test
    fun `no avisa hasta 4 s seguidos de exceso`() {
        assertNull(sostener(95.0, 80, 0, 3999).frase)
        assertNull(paso(95.0, 80, 3999).frase)
        assertEquals("exceso-1", paso(95.0, 80, 4000).frase!!.id)
    }

    @Test
    fun `un pico de exceso que se corta no avisa y la cuenta empieza de nuevo`() {
        paso(95.0, 80, 0)
        paso(95.0, 80, 2000)
        paso(80.0, 80, 3000) // se corta
        assertNull(paso(95.0, 80, 4000).frase)
        assertNull(paso(95.0, 80, 7999).frase)
        assertEquals("exceso-1", paso(95.0, 80, 8000).frase!!.id)
    }

    @Test
    fun `la frase sigue pendiente con el mismo id mientras dure el exceso y se retira al bajar`() {
        sostener(95.0, 80, 0, 4000)
        assertEquals("exceso-1", paso(96.0, 80, 5000).frase!!.id)
        assertNull(paso(86.0, 80, 6000).frase)
    }

    @Test
    fun `un solo aviso por episodio aunque el exceso dure minutos`() {
        sostener(95.0, 80, 0, 4000)
        val e = sostener(95.0, 80, 5000, 300_000)
        assertEquals("exceso-1", e.frase!!.id)
    }

    @Test
    fun `tras avisar no vuelve a avisar hasta pasados 90 s`() {
        sostener(95.0, 80, 0, 4000) // aviso a los 4 s
        paso(70.0, 80, 5000) // fin del episodio
        // Nuevo exceso sostenido, pero a menos de 90 s del aviso.
        assertNull(sostener(95.0, 80, 6000, 60_000).frase)
        paso(70.0, 80, 61_000)
        assertNull(sostener(95.0, 80, 62_000, 93_999).frase)
        paso(70.0, 80, 94_000)
        assertEquals("exceso-2", sostener(95.0, 80, 95_000, 99_000).frase!!.id)
    }

    @Test
    fun `exactamente 90 s despues si puede avisar`() {
        sostener(95.0, 80, 0, 4000)
        paso(70.0, 80, 5000)
        assertNull(sostener(95.0, 80, 86_000, 93_999).frase) // aviso previo en 4000: 90 000 -> 94 000
        assertEquals("exceso-2", paso(95.0, 80, 94_000).frase!!.id)
    }

    @Test
    fun `maximo dos avisos por via seguida`() {
        sostener(95.0, 80, 0, 4000)
        paso(70.0, 80, 5000)
        assertEquals("exceso-2", sostener(95.0, 80, 100_000, 104_000).frase!!.id)
        paso(70.0, 80, 105_000)
        // Tercer exceso sostenido en la misma via, con la separacion de sobra: se calla.
        assertNull(sostener(95.0, 80, 300_000, 320_000).frase)
    }

    @Test
    fun `al cambiar de limite sube el contador por via`() {
        sostener(95.0, 80, 0, 4000)
        paso(70.0, 80, 5000)
        sostener(95.0, 80, 100_000, 104_000)
        paso(70.0, 80, 105_000)
        // Otra via (limite 90) mas tarde: el contador vuelve a cero (sin gracia, el limite sube).
        paso(60.0, 90, 200_000)
        assertEquals("exceso-3", sostener(105.0, 90, 201_000, 205_000).frase!!.id)
    }

    @Test
    fun `tras pasar a un limite menor hay 10 s de gracia sin avisar`() {
        paso(60.0, 90, 0)
        // Entra en 50 yendo a 70: exceso, pero con 10 s de gracia mas 4 s de sostenido.
        for (ms in 1000L..14_999L step 1000) assertNull("en $ms", paso(70.0, 50, ms).frase)
        assertEquals("exceso-1", paso(70.0, 50, 15_000).frase!!.id)
    }

    @Test
    fun `la gracia exacta de 10 s termina y empieza a contar el exceso sostenido`() {
        paso(70.0, 90, 0)
        paso(70.0, 50, 1000) // gracia hasta 11 000
        assertNull(paso(70.0, 50, 10_999).frase)
        assertNull(paso(70.0, 50, 11_000).frase) // empieza la cuenta de 4 s
        assertNull(paso(70.0, 50, 14_999).frase)
        assertEquals("exceso-1", paso(70.0, 50, 15_000).frase!!.id)
    }

    @Test
    fun `subir de limite no da gracia`() {
        paso(40.0, 50, 0)
        assertEquals("exceso-1", sostener(110.0, 90, 1000, 5000).frase!!.id)
    }

    @Test
    fun `el primer limite que se conoce no da gracia`() {
        assertEquals("exceso-1", sostener(95.0, 80, 0, 4000).frase!!.id)
    }

    @Test
    fun `cambiar de limite retira el aviso pendiente y empieza una cuenta nueva`() {
        sostener(95.0, 80, 0, 4000)
        val nuevo = paso(95.0, 90, 5000) // sube el limite: ya no hay exceso (95 < 99)
        assertNull(nuevo.frase)
        // Al volver a 80 hay gracia (baja de limite).
        assertNull(paso(95.0, 80, 6000).frase)
    }

    @Test
    fun `un hueco sin limite corta el episodio sin tocar el limite`() {
        sostener(95.0, 80, 0, 4000)
        val hueco = paso(95.0, null, 5000)
        assertFalse(hueco.rojo)
        assertNull(hueco.frase)
        // Al volver el mismo limite la cuenta de exceso sostenido empieza de nuevo y la separacion de 90 s manda.
        assertNull(sostener(95.0, 80, 6000, 12_000).frase)
    }

    @Test
    fun `velocidades raras no avisan ni rompen`() {
        assertFalse(paso(Double.NaN, 80, 0).rojo)
        assertFalse(paso(-5.0, 80, 1000).rojo)
        assertFalse(paso(Double.POSITIVE_INFINITY, 80, 2000).exceso)
    }
}
