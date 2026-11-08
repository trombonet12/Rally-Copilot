package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ResultadoCrono
import app.trombito.rallycopilot.domain.tramos.TramoCronometrado
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.PosicionFantasma
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun pos(
        lat: Double,
        lon: Double,
        rumbo: Double,
        dist: Double = 0.0,
    ) = PosicionFantasma(PuntoGeo(lat, lon), rumbo, dist)

    @Test
    fun `la interpolacion pasa por los extremos y por el punto medio`() {
        val a = pos(39.700, 2.900, 90.0, 1_000.0)
        val b = pos(39.702, 2.904, 100.0, 1_020.0)
        assertEquals(a, interpolarFantasma(a, b, 0.0))
        assertEquals(b, interpolarFantasma(a, b, 1.0))
        val medio = interpolarFantasma(a, b, 0.5)
        assertEquals(39.701, medio.punto.lat, 1e-9)
        assertEquals(2.902, medio.punto.lon, 1e-9)
        assertEquals(95.0, medio.rumboGrados, 1e-9)
        assertEquals(1_010.0, medio.distanciaM, 1e-9)
    }

    @Test
    fun `la fraccion se acota a cero y uno`() {
        val a = pos(39.700, 2.900, 90.0)
        val b = pos(39.702, 2.904, 100.0)
        assertEquals(a, interpolarFantasma(a, b, -0.5))
        assertEquals(b, interpolarFantasma(a, b, 1.5))
    }

    @Test
    fun `el rumbo gira por el camino corto cruzando el norte`() {
        val a = pos(39.7, 2.9, 350.0)
        val b = pos(39.7, 2.9, 10.0)
        assertEquals(0.0, interpolarFantasma(a, b, 0.5).rumboGrados, 1e-9)
        assertEquals(355.0, interpolarFantasma(a, b, 0.25).rumboGrados, 1e-9)
        assertEquals(5.0, interpolarFantasma(a, b, 0.75).rumboGrados, 1e-9)
        // Y a la inversa: de 10° a 350° baja pasando por 0°.
        assertEquals(0.0, interpolarFantasma(b, a, 0.5).rumboGrados, 1e-9)
        assertTrue(interpolarFantasma(b, a, 0.3).rumboGrados in 0.0..360.0)
    }

    @Test
    fun `un avance normal se anima y un salto de mas de 200 m no`() {
        val a = pos(39.7, 2.9, 0.0)
        // 0,001° de latitud son ~111 m; 0,003° son ~334 m.
        assertTrue(debeAnimarFantasma(a, pos(39.701, 2.9, 0.0)))
        assertFalse(debeAnimarFantasma(a, pos(39.703, 2.9, 0.0)))
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
