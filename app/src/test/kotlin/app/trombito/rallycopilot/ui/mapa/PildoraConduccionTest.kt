package app.trombito.rallycopilot.ui.mapa

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.ui.alertas.AlertasPanel
import app.trombito.rallycopilot.ui.alertas.MediaTramoUi
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PildoraConduccionTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun poner(
        velocidad: Int? = 63,
        limite: Int? = 50,
        alertas: AlertasPanel = AlertasPanel(),
        sinSenal: Boolean = false,
    ) = banco.poner { RallyCopilotTheme { PildoraConduccion(velocidad, limite, alertas, sinSenal) } }

    @Test
    fun `muestra la velocidad y la senal de limite y TalkBack lee una sola frase`() {
        poner()
        assertTrue("63" in banco.textos(banco.conEtiqueta(ETIQUETA_VELOCIDAD)))
        assertTrue(banco.hay(ETIQUETA_LIMITE))
        assertEquals(
            "Velocidad: 63 kilómetros por hora. Límite de velocidad: 50 kilómetros por hora",
            banco.descripcion(banco.conEtiqueta(ETIQUETA_VELOCIDAD)),
        )
        assertFalse(banco.hay(ETIQUETA_EXCESO))
        assertFalse(banco.hay(ETIQUETA_MEDIA))
    }

    @Test
    fun `sin limite de la via no hay senal y sin dato la velocidad es una raya`() {
        poner(velocidad = null, limite = null)
        assertFalse(banco.hay(ETIQUETA_LIMITE))
        assertTrue("—" in banco.textos(banco.conEtiqueta(ETIQUETA_VELOCIDAD)))
    }

    @Test
    fun `sin senal de GPS la velocidad es una raya aunque haya cifra`() {
        poner(velocidad = 63, sinSenal = true, alertas = AlertasPanel(rojo = true))
        assertTrue("—" in banco.textos(banco.conEtiqueta(ETIQUETA_VELOCIDAD)))
        assertFalse("sin velocidad no hay exceso", banco.hay(ETIQUETA_EXCESO))
    }

    @Test
    fun `al pasar del limite aparece el aviso como region viva y la descripcion lo dice`() {
        poner(alertas = AlertasPanel(rojo = true))
        val aviso = banco.conEtiqueta(ETIQUETA_EXCESO)
        assertEquals("Por encima del límite de velocidad", banco.descripcion(aviso))
        assertEquals(LiveRegionMode.Polite, aviso.config.getOrNull(SemanticsProperties.LiveRegion))
        val velocidad = banco.conEtiqueta(ETIQUETA_VELOCIDAD)
        assertTrue("Por encima del límite" in banco.descripcion(velocidad).orEmpty())
        // La cifra en sí no es una región viva: no se anuncia con cada cambio.
        assertNull(velocidad.config.getOrNull(SemanticsProperties.LiveRegion))
    }

    @Test
    fun `con el exceso la pildora sigue siendo legible y el aviso la envuelve`() {
        poner(velocidad = 148, limite = 120, alertas = AlertasPanel(rojo = true, exceso = true))
        banco.esperar(2_000)
        val aviso = banco.conEtiqueta(ETIQUETA_EXCESO).boundsInRoot
        val pildora = banco.conEtiqueta(ETIQUETA_VELOCIDAD).boundsInRoot
        assertEquals(pildora, aviso)
        assertTrue("148" in banco.textos(banco.conEtiqueta(ETIQUETA_VELOCIDAD)))
    }

    @Test
    fun `la media del tramo sale junto a la pildora`() {
        poner(alertas = AlertasPanel(tramo = MediaTramoUi(78, supera = false, terminado = false)))
        val media = banco.conEtiqueta(ETIQUETA_MEDIA)
        assertEquals(listOf("MEDIA", "78"), banco.textos(media))
        assertEquals("Velocidad media del tramo: 78 kilómetros por hora", banco.descripcion(media))
        assertTrue(banco.hay(ETIQUETA_LIMITE))
    }

    @Test
    fun `la media que supera el limite lo dice y la final se rotula FINAL`() {
        poner(alertas = AlertasPanel(tramo = MediaTramoUi(91, supera = true, terminado = false)))
        assertEquals(
            "Velocidad media del tramo: 91 kilómetros por hora, por encima del límite",
            banco.descripcion(banco.conEtiqueta(ETIQUETA_MEDIA)),
        )
        poner(alertas = AlertasPanel(tramo = MediaTramoUi(74, supera = false, terminado = true)))
        assertEquals(listOf("FINAL", "74"), banco.textos(banco.conEtiqueta(ETIQUETA_MEDIA)))
    }

    @Test
    fun `antes de recorrer 100 m la media lleva una raya y sin limite la media sale igual`() {
        poner(limite = null, alertas = AlertasPanel(tramo = MediaTramoUi(null, supera = false, terminado = false)))
        assertEquals(listOf("MEDIA", "—"), banco.textos(banco.conEtiqueta(ETIQUETA_MEDIA)))
        assertFalse(banco.hay(ETIQUETA_LIMITE))
    }

    @Test
    fun `con exceso la pildora crece hacia arriba y no a lo ancho`() {
        poner(velocidad = 63, limite = 50, alertas = AlertasPanel(rojo = true))
        val exceso = banco.conEtiqueta(ETIQUETA_VELOCIDAD).boundsInRoot
        val otro = BancoCompose()
        otro.poner { RallyCopilotTheme { PildoraConduccion(63, 50, AlertasPanel(), false) } }
        val normal = otro.conEtiqueta(ETIQUETA_VELOCIDAD).boundsInRoot
        assertEquals("el ancho no cambia (no tapa la posición)", normal.width, exceso.width, 1f)
        assertEquals("ni más alta: la flecha va fuera, encima", normal.height, exceso.height, 1f)
    }

    @Test
    fun `la media del tramo va encima de la pildora y no a su derecha`() {
        poner(alertas = AlertasPanel(tramo = MediaTramoUi(mediaKmh = 78, supera = false, terminado = false)))
        val media = banco.conEtiqueta(ETIQUETA_MEDIA).boundsInRoot
        val pildora = banco.conEtiqueta(ETIQUETA_VELOCIDAD).boundsInRoot
        assertTrue("encima", media.bottom <= pildora.top)
        assertTrue("no a la derecha", media.left < pildora.right)
    }
}
