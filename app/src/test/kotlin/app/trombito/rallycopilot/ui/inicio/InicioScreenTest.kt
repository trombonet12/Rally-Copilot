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
    fun `el menu muestra las seis entradas del producto en orden`() {
        banco.poner { RallyCopilotTheme { InicioScreen() } }
        val esperadas = listOf("conducir", "mis_tramos", "reconocimiento", "mapas_offline", "perfil", "ajustes")
        assertEquals(esperadas.map { "inicio-$it" }, banco.etiquetas("inicio-"))
        val textos = banco.textos(banco.raiz())
        listOf("CONDUCIR", "MIS TRAMOS", "RECONOCIMIENTO", "MAPAS OFFLINE", "PERFIL DEL PILOTO", "AJUSTES").forEach {
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
    fun `conducir, mapas offline y ajustes son pulsables y navegan`() {
        val abiertas = mutableListOf<EntradaInicio>()
        banco.poner { RallyCopilotTheme { InicioScreen(onEntrada = { abiertas += it }) } }
        listOf("conducir", "mapas_offline", "ajustes").forEach { banco.pulsar(banco.conEtiqueta("inicio-$it")) }
        assertEquals(listOf(EntradaInicio.CONDUCIR, EntradaInicio.MAPAS_OFFLINE, EntradaInicio.AJUSTES), abiertas)
    }

    @Test
    fun `el resto de entradas sigue desactivado y marcado como proximamente`() {
        banco.poner { RallyCopilotTheme { InicioScreen() } }
        listOf("mis_tramos", "reconocimiento", "perfil").forEach {
            val nodo = banco.conEtiqueta("inicio-$it")
            assertFalse("$it no debe ser pulsable", banco.esPulsable(nodo))
            assertFalse(banco.estaHabilitado(nodo))
            assertTrue("Próximamente" in banco.textos(nodo))
        }
        val activa = banco.conEtiqueta("inicio-conducir")
        assertFalse("Próximamente" in banco.textos(activa))
    }
}
