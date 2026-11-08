package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ResultadoCrono
import app.trombito.rallycopilot.domain.tramos.TramoCronometrado
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.PosicionFantasma
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.Point

class CapaFantasmaTest {
    private val fantasma = PosicionFantasma(PuntoGeo(39.7, 2.9), 135.0, 1_200.0)

    private fun crono(
        fantasma: PosicionFantasma? = this.fantasma,
        tramo: Boolean = true,
        mejor: Boolean = true,
        resultado: ResultadoCrono? = null,
    ) = CronoEnCurso(
        tramo = if (tramo) TramoCronometrado("t1", null, 4_000.0, 3_990.0, emptyList(), emptyList()) else null,
        transcurridoMs = 60_000L,
        actualizadoEnMs = 1L,
        hayMejorPasada = mejor,
        resultado = resultado,
        fantasma = fantasma,
    )

    @Test
    fun `sin fantasma la fuente esta vacia`() {
        assertTrue(fantasmaGeoJson(null).features().orEmpty().isEmpty())
    }

    @Test
    fun `el fantasma es un punto con su rumbo`() {
        val features = fantasmaGeoJson(fantasma).features().orEmpty()
        assertEquals(1, features.size)
        val punto = features.single().geometry() as Point
        assertEquals(2.9, punto.longitude(), 0.0)
        assertEquals(39.7, punto.latitude(), 0.0)
        assertEquals(135.0, features.single().getNumberProperty(PROPIEDAD_RUMBO_FANTASMA).toDouble(), 0.0)
    }

    @Test
    fun `la capa es translucida`() {
        assertEquals(0.45f, OPACIDAD_FANTASMA, 0f)
    }

    @Test
    fun `con tramo vivo y mejor pasada el mapa recibe el fantasma`() {
        assertEquals(fantasma, fantasmaDelCrono(crono()))
    }

    @Test
    fun `sin cronometro, sin tramo, sin mejor pasada o tras la meta no hay fantasma`() {
        assertNull(fantasmaDelCrono(null))
        assertNull(fantasmaDelCrono(crono(tramo = false)))
        assertNull(fantasmaDelCrono(crono(mejor = false)))
        assertNull(fantasmaDelCrono(crono(resultado = ResultadoCrono(150_000L, -800L, true, 150_800L))))
        assertNull(fantasmaDelCrono(crono(fantasma = null)))
    }
}
