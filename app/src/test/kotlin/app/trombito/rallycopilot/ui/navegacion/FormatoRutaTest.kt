package app.trombito.rallycopilot.ui.navegacion

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.mapa.limitesDe
import app.trombito.rallycopilot.ui.mapa.lineaGeoJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatoRutaTest {
    @Test
    fun `la duracion se redondea a minutos y nunca baja de uno`() {
        assertEquals("1 min", formatearDuracionRuta(5.0))
        assertEquals("23 min", formatearDuracionRuta(1_380.0))
        assertEquals("23 min", formatearDuracionRuta(1_400.0))
    }

    @Test
    fun `desde una hora se da en horas y minutos`() {
        assertEquals("1 h 00 min", formatearDuracionRuta(3_600.0))
        assertEquals("1 h 05 min", formatearDuracionRuta(3_900.0))
        assertEquals("2 h 30 min", formatearDuracionRuta(9_000.0))
    }

    @Test
    fun `la linea geojson lleva longitud antes que latitud y es vacia con menos de dos puntos`() {
        val linea = lineaGeoJson(listOf(PuntoGeo(39.0, 2.0), PuntoGeo(39.5, 2.5)))
        val json = linea.toJson()
        assert("[2.0,39.0]" in json) { json }
        assertEquals(0, lineaGeoJson(listOf(PuntoGeo(39.0, 2.0))).features()?.size)
    }

    @Test
    fun `los limites abarcan todos los puntos`() {
        val limites = limitesDe(listOf(PuntoGeo(39.0, 2.0), PuntoGeo(39.8, 3.1), PuntoGeo(39.4, 2.5)))!!
        assertEquals(39.8, limites.latitudeNorth, 1e-9)
        assertEquals(39.0, limites.latitudeSouth, 1e-9)
        assertEquals(3.1, limites.longitudeEast, 1e-9)
        assertEquals(2.0, limites.longitudeWest, 1e-9)
        assertNull(limitesDe(listOf(PuntoGeo(39.0, 2.0))))
    }
}
