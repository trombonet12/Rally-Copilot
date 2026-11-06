package app.trombito.rallycopilot.ui.tutorial

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
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

private const val TEXTO_PISTA = "Aquí cambias lo que te canta."

/** El globo de pista: se enseña una sola vez, señala el elemento y «Entendido» la da por vista. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PistaContextoTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    /** Dibuja el globo en línea (un Popup vive en otra ventana que el banco no lee). */
    private val enLinea: @Composable (IntRect, String, LadoPista, () -> Unit) -> Unit =
        { _, texto, lado, onEntendido -> PistaContexto(texto, lado, flecha = 20, onEntendido = onEntendido) }

    private fun hayGlobo() = banco.hay(ETIQUETA_PISTA)

    private fun pulsarEntendido() = banco.pulsar(banco.conEtiqueta(ETIQUETA_PISTA_ENTENDIDO))

    private fun poner(
        estado: EstadoPistas?,
        contenido: @Composable () -> Unit,
    ) = banco.poner {
        RallyCopilotTheme {
            CompositionLocalProvider(
                LocalPistas provides (estado ?: EstadoPistas.Ninguna),
                LocalPresentadorPista provides enLinea,
                content = contenido,
            )
        }
    }

    @Test
    fun `el globo muestra el texto y entendido avisa`() {
        var entendidos = 0
        banco.poner {
            RallyCopilotTheme {
                PistaContexto(TEXTO_PISTA, LadoPista.ABAJO, flecha = 40, onEntendido = { entendidos++ })
            }
        }
        assertTrue(banco.todosLosTextos().contains(TEXTO_PISTA))
        assertTrue("Entendido" in banco.todosLosTextos())
        banco.pulsar(banco.conEtiqueta(ETIQUETA_PISTA_ENTENDIDO))
        assertEquals(1, entendidos)
    }

    @Test
    fun `entendido mide al menos 48 dp de alto`() {
        banco.poner {
            RallyCopilotTheme { PistaContexto(TEXTO_PISTA, LadoPista.ARRIBA, flecha = 40, onEntendido = {}) }
        }
        val alto = banco.conEtiqueta(ETIQUETA_PISTA_ENTENDIDO).size.height
        assertTrue("Alto $alto px", alto >= 48 * 3)
    }

    @Test
    fun `la pista sale tras un instante, una sola vez, y entendida no vuelve`() {
        var vistas by mutableStateOf(emptySet<Pista>())
        banco.poner {
            RallyCopilotTheme {
                val estado = remember(vistas) { EstadoPistas(vistas) { vistas = vistas + it } }
                CompositionLocalProvider(LocalPistas provides estado, LocalPresentadorPista provides enLinea) {
                    ConPista(Pista.MAPA, TEXTO_PISTA, LadoPista.IZQUIERDA) {
                        Box(Modifier.size(48.dp).testTag("objetivo"))
                    }
                }
            }
        }
        assertFalse("No sale al instante", hayGlobo())
        banco.esperar(RETARDO_PISTA_MS + 600)
        assertTrue("Sale pasado el retardo", hayGlobo())
        assertTrue(TEXTO_PISTA in banco.todosLosTextos())
        pulsarEntendido()
        banco.esperar(600)
        assertEquals(setOf(Pista.MAPA), vistas)
        assertFalse("Entendida, no vuelve", hayGlobo())
        banco.esperar(RETARDO_PISTA_MS + 600)
        assertFalse(hayGlobo())
    }

    @Test
    fun `con la pista ya vista el elemento se muestra sin globo`() {
        poner(EstadoPistas(setOf(Pista.MAPA)) {}) {
            ConPista(Pista.MAPA, TEXTO_PISTA, LadoPista.ABAJO) { Box(Modifier.size(48.dp).testTag("objetivo")) }
        }
        banco.esperar(RETARDO_PISTA_MS + 600)
        assertTrue(banco.hay("objetivo"))
        assertFalse(hayGlobo())
    }

    @Test
    fun `sin proveedor no sale ninguna pista`() {
        poner(null) {
            ConPista(Pista.ANALISIS, TEXTO_PISTA, LadoPista.ARRIBA) { Box(Modifier.size(48.dp).testTag("objetivo")) }
        }
        banco.esperar(RETARDO_PISTA_MS + 600)
        assertFalse(hayGlobo())
    }

    @Test
    fun `una pista inactiva no sale aunque este pendiente`() {
        poner(EstadoPistas(emptySet()) {}) {
            ConPista(Pista.MIS_TRAMOS, TEXTO_PISTA, LadoPista.ABAJO, activa = false) {
                Box(Modifier.size(48.dp).testTag("objetivo"))
            }
        }
        banco.esperar(RETARDO_PISTA_MS + 600)
        assertFalse(hayGlobo())
    }

    @Test
    fun `cada pantalla enseña su pista una vez, las demas siguen pendientes`() {
        val estado = EstadoPistas(setOf(Pista.MAPA)) {}
        assertFalse(estado.pendiente(Pista.MAPA))
        listOf(Pista.MIS_TRAMOS, Pista.ANALISIS, Pista.EDITAR_TRAMO).forEach { assertTrue(estado.pendiente(it)) }
    }
}
