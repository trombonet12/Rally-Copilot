package app.trombito.rallycopilot.ui.mapa

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomSeguimientoTest {
    @Test
    fun `sin camara previa la primera fijacion lleva el seguimiento a zoom 15`() {
        assertEquals(15.0, zoomParaSeguir(null), 0.0)
    }

    @Test
    fun `con la vista de la isla (z9) o menos de 12 se sigue a zoom 15`() {
        assertEquals(15.0, zoomParaSeguir(9.0), 0.0)
        assertEquals(15.0, zoomParaSeguir(11.9), 0.0)
    }

    @Test
    fun `un zoom de conduccion guardado se respeta`() {
        assertEquals(12.0, zoomParaSeguir(12.0), 0.0)
        assertEquals(16.5, zoomParaSeguir(16.5), 0.0)
    }
}
