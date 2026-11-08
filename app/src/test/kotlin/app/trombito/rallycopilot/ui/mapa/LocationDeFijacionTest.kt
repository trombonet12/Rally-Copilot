package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** El marcador del mapa no gira al norte cuando una fijación llega sin rumbo: se conserva el último mostrado. */
@RunWith(RobolectricTestRunner::class)
class LocationDeFijacionTest {
    private fun fijacion(rumbo: Double?) = Fijacion(PuntoGeo(39.7, 2.9), 1_000L, 20.0, rumbo, 5.0)

    @Test
    fun `con rumbo se usa el de la fijacion`() {
        val l = fijacion(75.0).aLocation(ultimoRumbo = 200.0)
        assertTrue(l.hasBearing())
        assertEquals(75.0f, l.bearing, 0.01f)
    }

    @Test
    fun `sin rumbo se usa el ultimo mostrado y no 0 grados`() {
        val l = fijacion(null).aLocation(ultimoRumbo = 200.0)
        assertTrue(l.hasBearing())
        assertEquals(200.0f, l.bearing, 0.01f)
    }

    @Test
    fun `sin rumbo ni ultimo no se pone rumbo`() {
        assertFalse(fijacion(null).aLocation(ultimoRumbo = null).hasBearing())
    }
}
