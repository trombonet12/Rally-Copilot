package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SDK_ANDROID_11 = 30

/** «Audio por el casco» y «Escucha con casco» en Ajustes > Voz: estado inicial, texto que cambia y callbacks. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AjustesVozCascoTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun estadoDe(etiqueta: String) =
        banco
            .conEtiqueta(etiqueta)
            .children
            .first { banco.esPulsable(it) }
            .config
            .getOrNull(SemanticsProperties.ToggleableState)

    @Test
    fun `por defecto el audio por el casco es automatico y la escucha solo al pulsar`() {
        banco.ponerAjustes(seccion = SeccionAjustes.VOZ)

        assertEquals(ToggleableState.On, estadoDe(ETIQUETA_AJUSTES_AUDIO_CASCO))
        assertEquals(ToggleableState.Off, estadoDe(ETIQUETA_AJUSTES_ESCUCHA_CASCO))
        val textos = banco.todosLosTextos()
        assertTrue(textos.any { it.startsWith("Automático") })
        assertTrue(textos.any { it.startsWith("Solo al pulsar el micrófono") && "música" in it })
    }

    @Test
    fun `tocar los interruptores cambia el valor, avisa y reescribe el texto de lo que pasa`() {
        val audio = mutableListOf<Boolean>()
        val escucha = mutableListOf<Boolean>()
        banco.ponerAjustes(
            seccion = SeccionAjustes.VOZ,
            acciones = AccionesAjustes(onAudioCasco = { audio += it }, onEscuchaCascoSiempre = { escucha += it }),
        )

        banco.pulsarInterruptor(ETIQUETA_AJUSTES_AUDIO_CASCO)
        banco.pulsarInterruptor(ETIQUETA_AJUSTES_ESCUCHA_CASCO)

        assertEquals(listOf(false), audio)
        assertEquals(listOf(true), escucha)
        assertEquals(ToggleableState.Off, estadoDe(ETIQUETA_AJUSTES_AUDIO_CASCO))
        assertEquals(ToggleableState.On, estadoDe(ETIQUETA_AJUSTES_ESCUCHA_CASCO))
        val textos = banco.todosLosTextos()
        assertTrue(textos.any { it.startsWith("Apagado") })
        assertTrue(textos.any { it.startsWith("Siempre") && "calidad de llamada" in it })
    }

    @Test
    @Config(sdk = [SDK_ANDROID_11])
    fun `antes de Android 12 los dos ajustes tambien existen aunque no haya permiso que pedir`() {
        banco.ponerAjustes(seccion = SeccionAjustes.VOZ)

        assertFalse(banco.hay(ETIQUETA_INTERCOM_ESTADO))
        assertTrue(banco.hay(ETIQUETA_AJUSTES_AUDIO_CASCO))
        assertTrue(banco.hay(ETIQUETA_AJUSTES_ESCUCHA_CASCO))
    }
}
