package app.trombito.rallycopilot.ui.inicio

import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
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
}
