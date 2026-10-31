package app.trombito.rallycopilot.ui.maniobras

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BannerManiobraTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun poner(
        variante: VarianteManiobra,
        distanciaM: Double,
        siguiente: Pair<TipoManiobra, String>? = null,
        tipo: TipoManiobra = TipoManiobra.IZQUIERDA,
        salida: Int? = null,
    ) = banco.poner {
        RallyCopilotTheme {
            BannerManiobra(variante, tipo, distanciaM, "Gira a la izquierda", siguiente, salidaRotonda = salida)
        }
    }

    @Test
    fun `muestra la distancia formateada y la frase en las tres variantes`() {
        for (variante in VarianteManiobra.entries) {
            val otro = BancoCompose()
            otro.poner {
                RallyCopilotTheme {
                    BannerManiobra(variante, TipoManiobra.IZQUIERDA, 352.0, "Gira a la izquierda", null)
                }
            }
            assertEquals(listOf("350 m"), otro.textos(otro.conEtiqueta(ETIQUETA_BANNER_DISTANCIA)))
            assertEquals(listOf("Gira a la izquierda"), otro.textos(otro.conEtiqueta(ETIQUETA_BANNER_FRASE)))
        }
    }

    @Test
    fun `en kilometros usa coma decimal`() {
        poner(VarianteManiobra.NOTA_RALLY, 1234.0)
        assertEquals(listOf("1,2 km"), banco.textos(banco.conEtiqueta(ETIQUETA_BANNER_DISTANCIA)))
    }

    @Test
    fun `sin siguiente maniobra no hay fila de luego`() {
        poner(VarianteManiobra.CHEVRON_HUD, 200.0)
        assertFalse(banco.hay(ETIQUETA_BANNER_SIGUIENTE))
    }

    @Test
    fun `con siguiente maniobra la muestra en pequeno`() {
        poner(VarianteManiobra.PLACA_INCLINADA, 200.0, siguiente = TipoManiobra.DERECHA to "derecha")
        val textos = banco.textos(banco.conEtiqueta(ETIQUETA_BANNER_SIGUIENTE))
        assertEquals(listOf("Luego", "derecha"), textos)
    }

    @Test
    fun `la rotonda con salida pone el numero en el icono`() {
        banco.poner {
            RallyCopilotTheme {
                IconoManiobra(TipoManiobra.ROTONDA_ENTRADA, VarianteManiobra.CHEVRON_HUD, salidaRotonda = 3)
            }
        }
        assertTrue("3" in banco.textos(banco.raiz()))
        assertTrue(
            banco.nodos().any { banco.descripcion(it) == "Rotonda, salida 3" },
        )
    }

    @Test
    fun `los textos del banner cumplen contraste AA en cada variante`() {
        for (variante in VarianteManiobra.entries) {
            val p = paletaBanner(variante)
            for (texto in listOf(p.distancia, p.frase, p.secundario)) {
                val c = contraste(texto, p.fondo)
                assertTrue("$variante ${texto.value} sobre ${p.fondo.value}: $c", c >= 4.5)
            }
        }
    }

    private fun contraste(
        a: Color,
        b: Color,
    ): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
