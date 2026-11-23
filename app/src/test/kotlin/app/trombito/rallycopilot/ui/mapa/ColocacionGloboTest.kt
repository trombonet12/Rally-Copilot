package app.trombito.rallycopilot.ui.mapa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColocacionGloboTest {
    // Pantalla de 400 px con 10 px de margen a la izquierda y 80 px de columna de botones a la derecha.
    private val limites = LimitesGlobo(400f, 10f, 80f, 60f)

    private fun colocar(
        x: Float,
        y: Float = 500f,
        ancho: Float = 200f,
        alto: Float = 100f,
    ) = colocarGlobo(x, y, ancho, alto, subidaPx = 26f, limites, margenPicoPx = 20f, pasoPx = 0f)

    @Test
    fun `con sitio a los dos lados el bocadillo se centra sobre el marcador`() {
        val c = colocar(x = 160f)
        assertEquals(100f, c.picoXPx, 0f)
        assertFalse(c.debajo)
        assertEquals(0f, c.desplazamientoPx(200f), 0f)
    }

    @Test
    fun `cerca del borde izquierdo se corre a la derecha y el piquito sigue apuntando al marcador`() {
        val c = colocar(x = 30f)
        // El bocadillo empieza en el margen (10): el marcador cae a 20 px de su borde.
        assertEquals(20f, c.picoXPx, 0f)
        assertEquals(80f, c.desplazamientoPx(200f), 0f)
    }

    @Test
    fun `no queda bajo la columna de botones de la derecha`() {
        val c = colocar(x = 390f)
        // Como mucho llega a 400 - 80 = 320: empieza en 120 y el marcador queda fuera, el piquito en el extremo.
        assertEquals(180f, c.picoXPx, 0f)
        assertTrue(c.desplazamientoPx(200f) < 0f)
    }

    @Test
    fun `el piquito nunca se sale del cuerpo`() {
        listOf(0f, 5f, 100f, 395f, 800f).forEach { x ->
            val c = colocar(x)
            assertTrue("x=$x pico=${c.picoXPx}", c.picoXPx in 20f..180f)
        }
    }

    @Test
    fun `si encima no cabe va debajo del marcador`() {
        assertFalse(colocar(x = 160f, y = 300f).debajo)
        // y - subida - alto = 100 - 26 - 100 < 60 (margen superior)
        assertTrue(colocar(x = 160f, y = 100f).debajo)
    }

    @Test
    fun `el redondeo del piquito no cambia el sitio donde cae`() {
        val fino = colocarGlobo(173f, 500f, 200f, 100f, 26f, limites, 20f, pasoPx = 0f)
        val grueso = colocarGlobo(173f, 500f, 200f, 100f, 26f, limites, 20f, pasoPx = 16f)
        assertEquals(0f, grueso.picoXPx % 16f, 0f)
        assertEquals(fino.picoXPx, grueso.picoXPx, 8f)
    }
}
