package app.trombito.rallycopilot.ui.mapa

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
class PanelVelocidadTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun poner(
        velocidad: Int?,
        limite: Int?,
        via: String? = null,
        enColumna: Boolean = false,
    ) = banco.poner {
        RallyCopilotTheme { PanelVelocidad(velocidad, limite, via, enColumna) }
    }

    @Test
    fun `con limite muestra velocidad y senal`() {
        poner(velocidad = 63, limite = 50)
        assertTrue("63" in banco.textos(banco.conEtiqueta(ETIQUETA_VELOCIDAD)))
        val senal = banco.conEtiqueta(ETIQUETA_LIMITE)
        assertEquals("Límite de velocidad: 50 kilómetros por hora", banco.descripcion(senal))
        assertEquals("Velocidad: 63 kilómetros por hora", banco.descripcion(banco.conEtiqueta(ETIQUETA_VELOCIDAD)))
    }

    @Test
    fun `sin limite no se muestra la senal`() {
        poner(velocidad = 63, limite = null)
        assertFalse(banco.hay(ETIQUETA_LIMITE))
        assertTrue(banco.hay(ETIQUETA_VELOCIDAD))
    }

    @Test
    fun `sin velocidad muestra una raya y lo dice a TalkBack`() {
        poner(velocidad = null, limite = null)
        val nodo = banco.conEtiqueta(ETIQUETA_VELOCIDAD)
        assertTrue("—" in banco.textos(nodo))
        assertEquals("Velocidad desconocida", banco.descripcion(nodo))
    }

    @Test
    fun `la velocidad cero se muestra como cero`() {
        poner(velocidad = 0, limite = null)
        assertTrue("0" in banco.textos(banco.conEtiqueta(ETIQUETA_VELOCIDAD)))
    }

    @Test
    fun `muestra la via cuando se conoce`() {
        poner(velocidad = 40, limite = 50, via = "Ma-1110")
        assertTrue("Ma-1110" in banco.textos(banco.conEtiqueta(ETIQUETA_VIA)))
    }

    @Test
    fun `sin via no hay rotulo de via`() {
        poner(velocidad = 40, limite = 50, via = null)
        assertFalse(banco.hay(ETIQUETA_VIA))
    }

    @Test
    fun `en columna tambien muestra velocidad y senal`() {
        poner(velocidad = 90, limite = 90, enColumna = true)
        assertTrue(banco.hay(ETIQUETA_VELOCIDAD))
        assertTrue(banco.hay(ETIQUETA_LIMITE))
    }
}
