package app.trombito.rallycopilot.ui.mapa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** La flecha de posición queda sobre los puntos de las notas aunque el componente de ubicación se ponga antes. */
class CapaNotasOrdenTest {
    @Test
    fun `con la ubicacion ya puesta las notas van bajo su capa mas baja`() {
        val capas = listOf("fondo", "carreteras", "mapbox-location-accuracy-layer", "mapbox-location-foreground-layer")
        assertEquals("mapbox-location-accuracy-layer", capaBajoLaPosicion(capas))
    }

    @Test
    fun `sin la ubicacion puesta no hay referencia y las notas van arriba`() {
        assertNull(capaBajoLaPosicion(listOf("fondo", "carreteras", "ruta-capa")))
    }
}
