package app.trombito.rallycopilot.ui.copiloto

import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El diálogo de elegir voz lleva a Trombito saludando arriba, decorativo. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class DialogoVozCopilotoTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    @Test
    fun `el dialogo de voz muestra a trombito saludando junto al titulo`() {
        banco.poner { RallyCopilotTheme { DialogoVozCopiloto(onElegir = {}, onProbar = {}, onPosponer = {}) } }
        assertTrue(banco.hay(ETIQUETA_VOZ_DIALOGO))
        assertTrue(banco.hay(ETIQUETA_VOZ_MASCOTA))
        assertTrue(banco.todosLosTextos().toString(), "Elige la voz del copiloto" in banco.todosLosTextos())
    }
}
