package app.trombito.rallycopilot.ui.comun

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import app.trombito.rallycopilot.ui.maniobras.BannerManiobra
import app.trombito.rallycopilot.ui.maniobras.ETIQUETA_BANNER
import app.trombito.rallycopilot.ui.maniobras.ETIQUETA_BANNER_DISTANCIA
import app.trombito.rallycopilot.ui.maniobras.ETIQUETA_BANNER_SIGUIENTE
import app.trombito.rallycopilot.ui.theme.ESCALA_FUENTE_MAXIMA_HUD
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import app.trombito.rallycopilot.ui.theme.escalaFuenteHud
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val DENSIDAD = 3f
private val ESCALAS = listOf(1.0f, 1.3f, 2.0f)

/** UX-15: el HUD (píldora de velocidad y banner de maniobra) no desborda con la fuente del sistema grande. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HudEscalaFuenteTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    @Test
    fun `la escala del HUD no pasa de 1,3 y por debajo se respeta`() {
        assertEquals(1.0f, escalaFuenteHud(1.0f), 0f)
        assertEquals(1.15f, escalaFuenteHud(1.15f), 0f)
        assertEquals(ESCALA_FUENTE_MAXIMA_HUD, escalaFuenteHud(1.3f), 0f)
        assertEquals(ESCALA_FUENTE_MAXIMA_HUD, escalaFuenteHud(2.0f), 0f)
    }

    private fun pildora(escala: Float): Rect {
        banco.poner {
            RallyCopilotTheme {
                ConEscalaDeFuente(escala) { PildoraVelocidad(velocidadKmh = 128, limiteKmh = 120) }
            }
        }
        return banco.conEtiqueta(ETIQUETA_PILDORA_VELOCIDAD).boundsInRoot
    }

    @Test
    fun `la pildora con fuente grande cabe en la pantalla y no crece mas alla de 1,3`() {
        val a13 = pildora(1.3f)
        assertTrue("cabe en 411 dp: $a13", a13.right <= 411 * DENSIDAD && a13.left >= 0f)
        val a20 = pildora(2.0f)
        assertEquals("con 2,0 mide lo mismo que con 1,3", a13.width, a20.width, 1f)
        assertEquals(a13.height, a20.height, 1f)
        assertTrue("sin escalar: ${a13.height}", a13.height <= pildora(1.0f).height * 1.4f)
    }

    @Test
    fun `los textos de la pildora quedan dentro de ella con las tres escalas`() {
        ESCALAS.forEach { escala ->
            val caja = pildora(escala)
            val textos = banco.nodos().filter { banco.textos(it).isNotEmpty() && it.children.isEmpty() }
            assertTrue(textos.isNotEmpty())
            textos.forEach {
                val b = it.boundsInRoot
                assertTrue(
                    "con $escala el texto $b sale de $caja",
                    b.left >= caja.left - 1 && b.right <= caja.right + 1,
                )
            }
        }
    }

    private fun banner(
        escala: Float,
        siguiente: Pair<TipoManiobra, String>? = TipoManiobra.IZQUIERDA to "Gira a la izquierda por la Ma-10",
    ) = banco.poner {
        RallyCopilotTheme {
            ConEscalaDeFuente(escala) {
                BannerManiobra(TipoManiobra.DERECHA, 350.0, "Gira a la derecha hacia Sóller", siguiente, via = "Ma-10")
            }
        }
    }

    private fun hojas(nodo: SemanticsNode): List<SemanticsNode> = listOf(nodo) + nodo.children.flatMap(::hojas)

    @Test
    fun `el banner con la maniobra siguiente cabe a lo ancho con las tres escalas`() {
        ESCALAS.forEach { escala ->
            banner(escala)
            val raiz = banco.raiz().boundsInRoot
            val banner = banco.conEtiqueta(ETIQUETA_BANNER).boundsInRoot
            assertTrue("con $escala el banner $banner sale de $raiz", banner.left >= 0f && banner.right <= raiz.right)
            val distancia = banco.conEtiqueta(ETIQUETA_BANNER_DISTANCIA).boundsInRoot
            assertTrue("con $escala la distancia sale del banner", distancia.right <= banner.right)
            assertTrue(banco.hay(ETIQUETA_BANNER_SIGUIENTE))
            val ficha = banco.conEtiqueta(ETIQUETA_BANNER_SIGUIENTE).boundsInRoot
            assertTrue("con $escala la ficha $ficha sale del banner $banner", ficha.right <= banner.right + 1)
        }
    }

    @Test
    fun `el banner a 2,0 mide lo mismo que a 1,3`() {
        banner(1.3f)
        val a13 = banco.conEtiqueta(ETIQUETA_BANNER).boundsInRoot
        banner(2.0f)
        val a20 = banco.conEtiqueta(ETIQUETA_BANNER).boundsInRoot
        assertEquals(a13.height, a20.height, 1f)
    }

    @Test
    fun `la ficha de luego mide al menos 56 dp, sus textos caben dentro y no bajan de 12 sp`() {
        ESCALAS.forEach { escala ->
            banner(escala)
            val nodo = banco.conEtiqueta(ETIQUETA_BANNER_SIGUIENTE)
            val ficha = nodo.boundsInRoot
            assertTrue("con $escala la ficha mide ${nodo.size.height}", nodo.size.height >= 56 * DENSIDAD - 1)
            // Robolectric mide el texto con anchos de mentira: se comprueba el reparto, no si «cabe» la frase.
            hojas(nodo).filter { it.children.isEmpty() && banco.textos(it).isNotEmpty() }.forEach {
                val b = it.boundsInRoot
                assertTrue(
                    "con $escala el texto $b sale de la ficha $ficha",
                    b.left >= ficha.left && b.right <= ficha.right,
                )
                assertTrue(
                    "con $escala el texto $b sale en vertical de $ficha",
                    b.top >= ficha.top && b.bottom <= ficha.bottom,
                )
            }
            val disposiciones = mutableListOf<TextLayoutResult>()
            hojas(nodo).forEach { n ->
                n.config
                    .getOrNull(SemanticsActions.GetTextLayoutResult)
                    ?.action
                    ?.invoke(disposiciones)
            }
            assertTrue(disposiciones.isNotEmpty())
            disposiciones.forEach {
                val sp = it.layoutInput.style.fontSize
                assertTrue("con $escala el texto baja de 12 sp: $sp", sp.value >= 12f)
            }
        }
    }

    @Test
    fun `sin maniobra siguiente no hay ficha`() {
        banner(1.0f, siguiente = null)
        assertFalse(banco.hay(ETIQUETA_BANNER_SIGUIENTE))
    }
}
