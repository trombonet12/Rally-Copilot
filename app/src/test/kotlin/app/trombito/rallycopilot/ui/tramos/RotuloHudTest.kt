package app.trombito.rallycopilot.ui.tramos

import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El rótulo del HUD de un tramo de vía no se corta a media palabra. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RotuloHudTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val nombreVia = "Ma-15 · km ~20,1→~32,4 · hacia Manacor"

    @Test
    fun `en apaisado el rotulo de un tramo de via se queda en la carretera y el rango`() {
        assertEquals("Ma-15 · km ~20,1→~32,4", rotuloCompacto(nombreVia, compacto = true))
        assertEquals(nombreVia, rotuloCompacto(nombreVia, compacto = false))
        assertEquals("Ses Fontanelles", rotuloCompacto("Ses Fontanelles", compacto = true))
    }

    @Test
    fun `en vertical el nombre completo de un tramo de via llega al rotulo`() {
        val base = cronoConocido()
        val crono = base.copy(tramo = base.tramo!!.copy(nombre = nombreVia))
        banco.poner { RallyCopilotTheme { HudCrono(crono, reloj = { AHORA_PRUEBA_MS }) } }
        assertEquals(listOf(nombreVia), banco.textos(banco.conEtiqueta(ETIQUETA_CRONO_ROTULO)))
    }
}
