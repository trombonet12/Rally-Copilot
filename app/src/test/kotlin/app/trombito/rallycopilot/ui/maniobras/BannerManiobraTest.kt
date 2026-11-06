package app.trombito.rallycopilot.ui.maniobras

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ETIQUETA_PLACA
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.AmbarSobreCarbon
import app.trombito.rallycopilot.ui.theme.CarbonOpaco
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import app.trombito.rallycopilot.ui.theme.TextoSecundarioSobreCarbon
import app.trombito.rallycopilot.ui.theme.TextoSobreCarbon
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
        distanciaM: Double,
        siguiente: Pair<TipoManiobra, String>? = null,
        tipo: TipoManiobra = TipoManiobra.IZQUIERDA,
        salida: Int? = null,
    ) = banco.poner {
        RallyCopilotTheme {
            BannerManiobra(tipo, distanciaM, "Gira a la izquierda", siguiente, salidaRotonda = salida)
        }
    }

    @Test
    fun `muestra la distancia formateada y la frase`() {
        poner(352.0)
        assertEquals(listOf("350 m"), banco.textos(banco.conEtiqueta(ETIQUETA_BANNER_DISTANCIA)))
        assertEquals(listOf("Gira a la izquierda"), banco.textos(banco.conEtiqueta(ETIQUETA_BANNER_FRASE)))
    }

    @Test
    fun `con via pinta la placa de la carretera junto a la frase`() {
        banco.poner {
            RallyCopilotTheme {
                BannerManiobra(
                    TipoManiobra.IZQUIERDA,
                    352.0,
                    "cruce, izquierda",
                    null,
                    via = "Ma-2141",
                )
            }
        }
        val placa = banco.nodos().single { it.config.getOrNull(SemanticsProperties.TestTag) == ETIQUETA_PLACA }
        assertEquals(listOf("Ma-2141"), banco.textos(placa))
        assertEquals("carretera Ma-2141", banco.descripcion(placa))
        assertEquals(listOf("cruce, izquierda"), banco.textos(banco.conEtiqueta(ETIQUETA_BANNER_FRASE)))
        val frase = banco.conEtiqueta(ETIQUETA_BANNER_FRASE).boundsInRoot
        assertTrue("la placa va a la derecha de la frase", placa.boundsInRoot.left >= frase.right - 1)
    }

    @Test
    fun `sin via no hay placa`() {
        poner(200.0)
        assertFalse(banco.hay(ETIQUETA_PLACA))
    }

    @Test
    fun `en kilometros usa coma decimal`() {
        poner(1234.0)
        assertEquals(listOf("1,2 km"), banco.textos(banco.conEtiqueta(ETIQUETA_BANNER_DISTANCIA)))
    }

    @Test
    fun `sin siguiente maniobra no hay fila de luego`() {
        poner(200.0)
        assertFalse(banco.hay(ETIQUETA_BANNER_SIGUIENTE))
    }

    @Test
    fun `con siguiente maniobra la muestra en pequeno`() {
        poner(200.0, siguiente = TipoManiobra.DERECHA to "derecha")
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
    fun `los textos del banner cumplen contraste AA sobre el carbon`() {
        for (texto in listOf(TextoSobreCarbon, TextoSecundarioSobreCarbon, AmbarSobreCarbon)) {
            val c = contraste(texto, CarbonOpaco)
            assertTrue("${texto.value} sobre el carbon: $c", c >= 4.5)
        }
    }

    @Test
    fun `el banner ocupa el ancho y es una tarjeta con barra roja y no un relleno rojo`() {
        poner(352.0)
        val banner = banco.conEtiqueta(ETIQUETA_BANNER).boundsInRoot
        assertTrue("a todo el ancho: $banner", banner.width > 411 * 3f - 4)
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
