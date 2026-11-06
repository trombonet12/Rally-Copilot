package app.trombito.rallycopilot.ui.copiloto

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.trombito.rallycopilot.domain.escucha.DialogoVoz
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

/** La burbuja de escucha: aparece al escuchar, enseña lo oído y la respuesta y se esconde a los 3 s. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BurbujaEscuchaTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private var escuchando by mutableStateOf(false)
    private var dialogo by mutableStateOf<DialogoVoz?>(null)

    private fun poner() = banco.poner { RallyCopilotTheme { BurbujaEscucha(escuchando, dialogo) } }

    @Test
    fun `sin escuchar ni dialogo no hay burbuja`() {
        poner()
        assertFalse(banco.hay(ETIQUETA_BURBUJA_ESCUCHA))
    }

    @Test
    fun `al escuchar sale con Escuchando`() {
        poner()
        escuchando = true
        banco.esperar(600)
        assertTrue(banco.hay(ETIQUETA_BURBUJA_ESCUCHA))
        assertTrue("Escuchando…" in banco.todosLosTextos())
    }

    @Test
    fun `al resolverse enseña lo oido y la respuesta, y se esconde a los 3 segundos`() {
        poner()
        escuchando = true
        banco.esperar(600)
        dialogo = DialogoVoz(1, "modo notas", "modo notas")
        banco.esperar(600)
        assertEquals(listOf("«modo notas»"), banco.textos(banco.conEtiqueta(ETIQUETA_BURBUJA_TEXTO)))
        assertEquals(listOf("Modo notas"), banco.textos(banco.conEtiqueta(ETIQUETA_BURBUJA_RESPUESTA)))
        escuchando = false
        banco.esperar(RETARDO_OCULTAR_BURBUJA_MS - 1_000)
        assertTrue("Sigue a la vista antes de los 3 s", banco.hay(ETIQUETA_BURBUJA_ESCUCHA))
        banco.esperar(2_000)
        assertFalse("Se esconde pasados los 3 s", banco.hay(ETIQUETA_BURBUJA_ESCUCHA))
    }

    @Test
    fun `sin oir nada dice que no te ha oido`() {
        poner()
        dialogo = DialogoVoz(1, "", null)
        banco.esperar(600)
        assertTrue("No te he oído" in banco.todosLosTextos())
    }
}
