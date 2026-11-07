package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViaCoincideTest {
    private val trazado = listOf(PuntoGeo(39.700, 2.900), PuntoGeo(39.700, 2.910))
    private val via = ViaActual("Carretera", "Ma-10", 50, trazado)

    @Test
    fun `un punto sobre el trazado coincide`() {
        assertTrue(viaCoincide(PuntoGeo(39.700, 2.905), via))
    }

    @Test
    fun `un punto a 5 m coincide y a 40 m no`() {
        // Un milesimo de grado de latitud son unos 111 m.
        assertTrue(viaCoincide(PuntoGeo(39.700045, 2.905), via))
        assertFalse(viaCoincide(PuntoGeo(39.70036, 2.905), via))
    }

    @Test
    fun `sin via o sin geometria no hay nada que contradiga`() {
        assertTrue(viaCoincide(PuntoGeo(39.9, 2.1), null))
        assertTrue(viaCoincide(PuntoGeo(39.9, 2.1), ViaActual("Cami", null, null)))
    }
}
