package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.ui.navegacion.FalsoControlNavegacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** El rumbo que pinta el mapa nunca queda vacío (MapLibre lo tomaría como 0° y giraría al norte y de vuelta). */
class PosicionSobreCaminoTest {
    private val sobre = PosicionSobreCamino(FalsoControlNavegacion())

    /** Carretera de 1 km hacia el este por la latitud 39,7. */
    private val via =
        ViaActual("Ma-13", null, null, listOf(PuntoGeo(39.7, 2.90), PuntoGeo(39.7, 2.912)))

    private fun fijacion(
        norteM: Double,
        rumbo: Double?,
    ) = Fijacion(PuntoGeo(39.7 + norteM / M_POR_GRADO, 2.905), 0L, 15.0, rumbo, 5.0)

    @Test
    fun `fuera de la via y sin rumbo se conserva el ultimo rumbo mostrado`() {
        // A 60 m de la carretera: no se ajusta y el rumbo es el bruto.
        assertEquals(123.0, sobre.ajustar(fijacion(60.0, 123.0), via).rumboGrados ?: Double.NaN, 0.0)
        assertEquals(123.0, sobre.ajustar(fijacion(60.0, null), via).rumboGrados ?: Double.NaN, 0.0)
    }

    @Test
    fun `sin via ni rumbo se conserva el ultimo rumbo mostrado`() {
        sobre.ajustar(fijacion(0.0, 200.0), null)
        assertEquals(200.0, sobre.ajustar(fijacion(0.0, null), null).rumboGrados ?: Double.NaN, 0.0)
    }

    @Test
    fun `sin ningun rumbo previo sigue sin rumbo`() {
        assertEquals(null, sobre.ajustar(fijacion(0.0, null), null).rumboGrados)
    }

    @Test
    fun `sobre la via el rumbo es el de la via aunque el bruto falte`() {
        val ajustada = sobre.ajustar(fijacion(3.0, null), via)
        assertNotNull(ajustada.rumboGrados)
        assertEquals(90.0, ajustada.rumboGrados ?: Double.NaN, 1.0)
    }

    private companion object {
        const val M_POR_GRADO = 111_194.9
    }
}
