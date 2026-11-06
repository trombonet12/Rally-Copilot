package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.ajustes.Orientacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.IllegalStateException

class CamaraSeguimientoTest {
    @Test
    fun `con rumbo arriba el mapa se inclina y con norte arriba va plano`() {
        assertEquals(50.0, inclinacionSeguimiento(Orientacion.RUMBO), 0.0)
        assertEquals(0.0, inclinacionSeguimiento(Orientacion.NORTE), 0.0)
    }

    @Test
    fun `en vertical el marcador queda hacia el 80 por ciento de la altura`() {
        val alto = 2000
        val relleno = rellenoSuperiorSeguimiento(anchoPx = 1080, altoPx = alto)
        assertEquals(0.80, centroVisible(relleno, alto), 0.001)
    }

    @Test
    fun `en horizontal baja menos que en vertical`() {
        val alto = 1080
        val horizontal = rellenoSuperiorSeguimiento(anchoPx = 2000, altoPx = alto)
        assertEquals(0.70, centroVisible(horizontal, alto), 0.001)
        assertTrue(horizontal.toDouble() / alto < rellenoSuperiorSeguimiento(1080, 2000).toDouble() / 2000)
    }

    @Test
    fun `sin alto medido no hay relleno`() {
        assertEquals(0, rellenoSuperiorSeguimiento(anchoPx = 0, altoPx = 0))
    }

    @Test
    fun `el descarte del seguimiento mientras la app mueve la cámara no es del usuario`() {
        val guarda = DescarteSeguimiento()
        assertTrue(guarda.esDelUsuario())
        guarda.aplicando {
            // MapLibre avisa del descarte al cambiar de modo (p. ej. norte arriba a rumbo arriba): lo provoca la app.
            assertFalse(guarda.esDelUsuario())
        }
        assertTrue(guarda.esDelUsuario())
    }

    @Test
    fun `si aplicar la cámara falla el descarte vuelve a ser del usuario`() {
        val guarda = DescarteSeguimiento()
        try {
            guarda.aplicando { throw IllegalStateException("fallo del mapa") }
        } catch (_: IllegalStateException) {
            // esperado: lo que se comprueba es que el estado se restablece
        }
        assertTrue(guarda.esDelUsuario())
    }

    /** Fracción del alto a la que cae el centro de la zona visible (donde la cámara pone el marcador). */
    private fun centroVisible(
        relleno: Int,
        alto: Int,
    ) = (relleno + (alto - relleno) / 2.0) / alto
}
