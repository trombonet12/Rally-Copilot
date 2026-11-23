package app.trombito.rallycopilot.ui.ajustes

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

/** Subpantallas que reúnen varias secciones: subtítulos, todos los ajustes y vista previa del último grupo. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AjustesGruposTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun previaActual() =
        PrevistaAjustes.entries.filter { banco.hay(ETIQUETA_SECCION_PREVISTA_PREFIJO + it.clave) }

    @Test
    fun `copiloto y notas reune el modo la anticipacion y las dos opciones de notas con sus subtitulos`() {
        banco.ponerAjustes(seccion = SeccionAjustes.COPILOTO)
        val textos = banco.todosLosTextos()
        assertTrue("Copiloto" in textos)
        assertTrue("Notas" in textos)
        assertTrue(banco.hay(ETIQUETA_AJUSTES_MODO_PREFIJO + "completo"))
        assertTrue(banco.hay(ETIQUETA_ANTICIPACION_PREFIJO + "normal"))
        listOf(ETIQUETA_NOTAS_SIMPLIFICADAS, ETIQUETA_NOTAS_GRANDES).forEach { assertTrue(it, banco.hay(it)) }
    }

    @Test
    fun `voz y avisos reune voz volumen casco habla avisos e indicaciones`() {
        banco.ponerAjustes(seccion = SeccionAjustes.VOZ)
        val textos = banco.todosLosTextos()
        assertTrue("Voz" in textos)
        assertTrue("Indicaciones" in textos)
        listOf(
            ETIQUETA_AJUSTES_AUDIO_CASCO,
            ETIQUETA_AJUSTES_ESCUCHA_CASCO,
            ETIQUETA_AJUSTES_VOLUMEN,
            ETIQUETA_AJUSTES_CRUCES,
            ETIQUETA_AJUSTES_PUENTES,
            ETIQUETA_AJUSTES_SALIDAS,
            ETIQUETA_AJUSTES_ROTONDAS,
        ).forEach { assertTrue(it, banco.hay(it)) }
    }

    @Test
    fun `una subpantalla de un solo grupo no pone subtitulo`() {
        banco.ponerAjustes(seccion = SeccionAjustes.TRAMOS)
        assertEquals(listOf(PrevistaAjustes.TRAMOS), previaActual())
    }

    @Test
    fun `al abrir la vista previa es la del primer grupo y pasa a la del ultimo grupo tocado`() {
        banco.ponerAjustes(seccion = SeccionAjustes.COPILOTO)
        assertEquals(listOf(PrevistaAjustes.COPILOTO), previaActual())
        banco.pulsarInterruptor(ETIQUETA_NOTAS_SIMPLIFICADAS)
        assertEquals(listOf(PrevistaAjustes.NOTAS), previaActual())
        banco.pulsar(banco.conEtiqueta(ETIQUETA_AJUSTES_MODO_PREFIJO + "silencio"))
        assertEquals(listOf(PrevistaAjustes.COPILOTO), previaActual())
    }

    @Test
    fun `en voz y avisos tocar una indicacion cambia a la vista previa de indicaciones`() {
        banco.ponerAjustes(seccion = SeccionAjustes.VOZ)
        assertEquals(listOf(PrevistaAjustes.VOZ), previaActual())
        banco.pulsarInterruptor(ETIQUETA_AJUSTES_CRUCES)
        assertEquals(listOf(PrevistaAjustes.INDICACIONES), previaActual())
        banco.pulsarInterruptor(ETIQUETA_AJUSTES_AUDIO_CASCO)
        assertEquals(listOf(PrevistaAjustes.VOZ), previaActual())
        assertFalse(banco.hay(ETIQUETA_SECCION_PREVISTA_PREFIJO + "indicaciones"))
    }
}
