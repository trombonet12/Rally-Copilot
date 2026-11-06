package app.trombito.rallycopilot.ui.tutorial

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

private val VENTANA = IntSize(1000, 2000)
private val GLOBO = IntSize(400, 200)
private const val HUECO = 20
private const val MARGEN = 30

/** La geometría del globo de pista: lado pedido, vuelco al contrario y flecha al centro del elemento. */
class PosicionPistaTest {
    private fun colocar(
        ancla: IntRect,
        lado: LadoPista,
    ) = posicionarPista(ancla, EspacioPista(VENTANA, HUECO, MARGEN), GLOBO, lado)

    @Test
    fun `abajo el globo cuelga bajo el elemento y la flecha apunta a su centro`() {
        val p = colocar(IntRect(400, 500, 500, 600), LadoPista.ABAJO)
        assertEquals(LadoPista.ABAJO, p.lado)
        assertEquals(620, p.origen.y)
        assertEquals(450 - p.origen.x, p.flecha)
    }

    @Test
    fun `arriba el globo queda sobre el elemento`() {
        val p = colocar(IntRect(400, 800, 500, 900), LadoPista.ARRIBA)
        assertEquals(LadoPista.ARRIBA, p.lado)
        assertEquals(800 - HUECO - GLOBO.height, p.origen.y)
    }

    @Test
    fun `si no cabe por arriba se vuelca abajo`() {
        val p = colocar(IntRect(400, 50, 500, 150), LadoPista.ARRIBA)
        assertEquals(LadoPista.ABAJO, p.lado)
        assertEquals(170, p.origen.y)
    }

    @Test
    fun `junto al borde derecho el globo se desliza y la flecha sigue al elemento`() {
        val p = colocar(IntRect(900, 500, 990, 600), LadoPista.ABAJO)
        assertEquals(VENTANA.width - GLOBO.width - MARGEN, p.origen.x)
        assertEquals(945 - p.origen.x, p.flecha)
    }

    @Test
    fun `izquierda el globo queda a la izquierda del elemento centrado en vertical`() {
        val p = colocar(IntRect(900, 1000, 990, 1100), LadoPista.IZQUIERDA)
        assertEquals(LadoPista.IZQUIERDA, p.lado)
        assertEquals(900 - HUECO - GLOBO.width, p.origen.x)
        assertEquals(1050 - p.origen.y, p.flecha)
    }

    @Test
    fun `si no cabe a la izquierda pasa a la derecha`() {
        val p = colocar(IntRect(40, 1000, 130, 1100), LadoPista.IZQUIERDA)
        assertEquals(LadoPista.DERECHA, p.lado)
        assertEquals(130 + HUECO, p.origen.x)
    }
}
