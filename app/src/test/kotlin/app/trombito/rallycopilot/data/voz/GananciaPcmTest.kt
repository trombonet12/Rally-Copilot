package app.trombito.rallycopilot.data.voz

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GananciaPcmTest {
    @Test
    fun `con el 100 por cien devuelve la misma tabla sin copiarla`() {
        val muestras = shortArrayOf(1, -2, 300)
        assertSame(muestras, GananciaPcm.aplicar(muestras, 100))
    }

    @Test
    fun `el 50 por cien reduce a la mitad y el 150 sube la mitad por debajo del codo`() {
        val muestras = shortArrayOf(1_000, -2_000, 0)
        assertArrayEquals(shortArrayOf(500, -1_000, 0), GananciaPcm.aplicar(muestras, 50))
        assertArrayEquals(shortArrayOf(1_500, -3_000, 0), GananciaPcm.aplicar(muestras, 150))
    }

    @Test
    fun `subir el volumen no desborda ni recorta de golpe y conserva el orden`() {
        val rampa = ShortArray(65_536) { (it - 32_768).toShort() }
        val salida = GananciaPcm.aplicar(rampa, 150)
        assertTrue(salida.all { abs(it.toInt()) <= Short.MAX_VALUE })
        for (i in 1 until salida.size) assertTrue("no decrece en $i", salida[i] >= salida[i - 1])
        // Cerca del techo se comprime: no llega a duplicar el escalón de la entrada.
        val picoEntrada = 30_000
        val comprimido = GananciaPcm.aplicar(shortArrayOf(picoEntrada.toShort()), 150)[0].toInt()
        assertTrue(comprimido in (picoEntrada + 1)..Short.MAX_VALUE.toInt())
        assertTrue(comprimido < picoEntrada * 3 / 2)
    }

    @Test
    fun `la ganancia es simetrica y el porcentaje se acota a 50-150`() {
        val salida = GananciaPcm.aplicar(shortArrayOf(25_000, -25_000), 150)
        assertEquals(-salida[1].toInt(), salida[0].toInt())
        assertArrayEquals(GananciaPcm.aplicar(shortArrayOf(1_000), 50), GananciaPcm.aplicar(shortArrayOf(1_000), 10))
        assertArrayEquals(GananciaPcm.aplicar(shortArrayOf(1_000), 150), GananciaPcm.aplicar(shortArrayOf(1_000), 900))
    }
}
