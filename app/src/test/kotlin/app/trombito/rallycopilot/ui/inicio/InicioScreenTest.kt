package app.trombito.rallycopilot.ui.inicio

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
class InicioScreenTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    @Test
    fun `el menu muestra las cuatro entradas del producto en orden`() {
        banco.poner { RallyCopilotTheme { InicioScreen() } }
        val esperadas = listOf("conducir", "mis_tramos", "perfil", "ajustes")
        assertEquals(esperadas.map { "inicio-$it" }, banco.etiquetas("inicio-"))
        val textos = banco.textos(banco.raiz())
        listOf("CONDUCIR", "Mis tramos", "Perfil del piloto", "Ajustes").forEach {
            assertTrue("Falta la entrada $it en $textos", it in textos)
        }
    }

    @Test
    fun `la cabecera muestra el nombre y el lema`() {
        banco.poner { RallyCopilotTheme { InicioScreen() } }
        val textos = banco.textos(banco.raiz())
        assertTrue("RALLY COPILOT" in textos)
        assertTrue("Tu copiloto en cada curva" in textos)
    }

    @Test
    fun `se puede desplazar en pantallas bajas`() {
        banco.poner { RallyCopilotTheme { InicioScreen() } }
        assertTrue(banco.hayDesplazable())
    }

    @Test
    fun `conducir, mis tramos, perfil y ajustes son pulsables y navegan`() {
        val abiertas = mutableListOf<EntradaInicio>()
        banco.poner { RallyCopilotTheme { InicioScreen(onEntrada = { abiertas += it }) } }
        listOf("conducir", "mis_tramos", "perfil", "ajustes").forEach {
            banco.pulsar(banco.conEtiqueta("inicio-$it"))
        }
        assertEquals(
            listOf(
                EntradaInicio.CONDUCIR,
                EntradaInicio.MIS_TRAMOS,
                EntradaInicio.PERFIL,
                EntradaInicio.AJUSTES,
            ),
            abiertas,
        )
    }

    @Test
    fun `todas las entradas son pulsables y miden lo pedido`() {
        banco.poner { RallyCopilotTheme { InicioScreen() } }
        val densidad = 3f
        EntradaInicio.entries.forEach {
            val nodo = banco.conEtiqueta("inicio-${it.name.lowercase()}")
            assertTrue("${it.name} debe ser pulsable", banco.esPulsable(nodo))
            val alto = if (it == EntradaInicio.CONDUCIR) 120 else 96
            assertEquals("${it.name} debe medir $alto dp", alto * densidad, nodo.size.height.toFloat(), 1f)
        }
    }

    @Test
    fun `sin salidas da la bienvenida y con una muestra la ultima con sus tiempos`() {
        banco.poner { RallyCopilotTheme { InicioScreen() } }
        assertTrue(banco.hay(ETIQUETA_BIENVENIDA))
        assertFalse(banco.hay(ETIQUETA_ULTIMA_SALIDA))
    }

    @Test
    fun `la ultima salida dice el tramo, el tiempo y el mejor con coma decimal`() {
        val salida = UltimaSalida("a", "Ma-2141 · Escorca", 257_300L, 252_300L)
        banco.poner { RallyCopilotTheme { InicioScreen(estado = InicioUiState(salida)) } }
        assertFalse(banco.hay(ETIQUETA_BIENVENIDA))
        val textos = banco.textos(banco.conEtiqueta(ETIQUETA_ULTIMA_SALIDA))
        assertTrue("Última salida: Ma-2141 · Escorca · 04:17,3 · mejor 04:12,3" in textos)
    }
}
