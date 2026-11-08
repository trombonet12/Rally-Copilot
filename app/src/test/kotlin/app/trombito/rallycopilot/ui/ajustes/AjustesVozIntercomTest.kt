package app.trombito.rallycopilot.ui.ajustes

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

private const val SDK_ANDROID_11 = 30

/** Fila «Intercom del casco» de Ajustes > Voz: sus tres estados y que no existe antes de Android 12. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AjustesVozIntercomTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun poner(
        estado: EstadoIntercom,
        onConceder: () -> Unit = {},
        onAbrirAjustes: () -> Unit = {},
    ) = banco.poner { RallyCopilotTheme { FilaIntercom(estado, onConceder, onAbrirAjustes) } }

    @Test
    fun `concedido dice concedido y no ofrece boton`() {
        poner(EstadoIntercom.CONCEDIDO)

        assertEquals("Concedido", banco.textoDe(ETIQUETA_INTERCOM_ESTADO))
        assertEquals("Intercom del casco: concedido", banco.descripcion(banco.conEtiqueta(ETIQUETA_INTERCOM_ESTADO)))
        assertFalse(banco.hay(ETIQUETA_INTERCOM_BOTON))
    }

    @Test
    fun `sin permiso lo dice y el boton conceder lo pide`() {
        var pedido = 0
        var ajustes = 0
        poner(EstadoIntercom.SIN_PERMISO, onConceder = { pedido++ }, onAbrirAjustes = { ajustes++ })

        assertTrue(banco.textoDe(ETIQUETA_INTERCOM_ESTADO).startsWith("Sin permiso"))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_INTERCOM_BOTON))

        assertEquals(1, pedido)
        assertEquals(0, ajustes)
    }

    @Test
    fun `bloqueado para siempre el boton abre los ajustes del sistema`() {
        var pedido = 0
        var ajustes = 0
        poner(EstadoIntercom.BLOQUEADO, onConceder = { pedido++ }, onAbrirAjustes = { ajustes++ })

        assertTrue("Abrir ajustes" in banco.todosLosTextos())
        banco.pulsar(banco.conEtiqueta(ETIQUETA_INTERCOM_BOTON))

        assertEquals(0, pedido)
        assertEquals(1, ajustes)
    }

    @Test
    fun `en Ajustes Voz la fila aparece sin permiso desde Android 12`() {
        banco.ponerAjustes(seccion = SeccionAjustes.VOZ)

        assertTrue(banco.hay(ETIQUETA_INTERCOM_ESTADO))
        assertTrue(banco.hay(ETIQUETA_INTERCOM_BOTON))
    }

    @Test
    @Config(sdk = [SDK_ANDROID_11])
    fun `en Ajustes Voz la fila no existe antes de Android 12`() {
        banco.ponerAjustes(seccion = SeccionAjustes.VOZ)

        assertFalse(banco.hay(ETIQUETA_INTERCOM_ESTADO))
    }
}
