package app.trombito.rallycopilot.ui.comun

import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
class BotonIconoTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun poner(
        estado: EstadoBotonIcono = EstadoBotonIcono(),
        onClick: () -> Unit = {},
    ) = banco.poner {
        RallyCopilotTheme {
            BotonIcono("Capas", onClick, Modifier.testTag("boton"), estado) {
                Icon(Iconos.Capas, contentDescription = null)
            }
        }
    }

    @Test
    fun `mide al menos 48 dp, no lleva texto y se describe`() {
        poner()
        val nodo = banco.conEtiqueta("boton")
        assertTrue(nodo.size.width >= 48 * 3 && nodo.size.height >= 48 * 3)
        assertTrue(banco.textos(nodo).isEmpty())
        assertEquals("Capas", banco.descripcion(nodo))
    }

    @Test
    fun `pulsar llama a la accion salvo deshabilitado`() {
        var pulsaciones = 0
        poner(EstadoBotonIcono(rol = Role.RadioButton)) { pulsaciones++ }
        banco.pulsar(banco.conEtiqueta("boton"))
        assertEquals(1, pulsaciones)
    }

    @Test
    fun `deshabilitado no es pulsable`() {
        poner(EstadoBotonIcono(habilitado = false))
        assertFalse(banco.estaHabilitado(banco.conEtiqueta("boton")))
    }
}
