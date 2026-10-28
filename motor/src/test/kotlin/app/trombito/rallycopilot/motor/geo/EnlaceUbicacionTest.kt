package app.trombito.rallycopilot.motor.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnlaceUbicacionTest {
    private val delta = 1e-9

    // Sa Calobra 39.8505, 2.7997 · Soller 39.7667, 2.7150
    private fun assertPunto(
        lat: Double,
        lon: Double,
        nombre: String?,
        texto: String,
        tolerancia: Double = delta,
    ) {
        val r = EnlaceUbicacion.leer(texto)
        assertTrue("«$texto» dio $r", r is ResultadoEnlace.Punto)
        r as ResultadoEnlace.Punto
        assertEquals("lat de «$texto»", lat, r.punto.lat, tolerancia)
        assertEquals("lon de «$texto»", lon, r.punto.lon, tolerancia)
        assertEquals("nombre de «$texto»", nombre, r.nombre)
    }

    private fun assertNoReconocido(texto: String) =
        assertEquals("«$texto»", ResultadoEnlace.NoReconocido, EnlaceUbicacion.leer(texto))

    // --- Coordenadas en texto ---

    @Test
    fun `decimales separados por coma, con y sin espacio`() {
        assertPunto(39.8505, 2.7997, null, "39.8505,2.7997")
        assertPunto(39.8505, 2.7997, null, "39.8505, 2.7997")
        assertPunto(39.8505, 2.7997, null, "  39.8505 , 2.7997\n")
    }

    @Test
    fun `decimales separados por espacio o punto y coma con punto decimal`() {
        assertPunto(39.7667, 2.7150, null, "39.7667 2.7150")
        assertPunto(39.7667, 2.7150, null, "39.7667; 2.7150")
    }

    @Test
    fun `acepta cualquier punto del mundo y signos negativos`() {
        assertPunto(-33.8688, 151.2093, null, "-33.8688, 151.2093")
        assertPunto(40.4168, -3.7038, null, "40.4168,-3.7038")
    }

    @Test
    fun `coma decimal ambigua no se reconoce`() {
        assertNoReconocido("39,83 2,81")
        assertNoReconocido("39,83")
        assertNoReconocido("39,8505 2,7997")
    }

    @Test
    fun `rechaza coordenadas fuera de rango`() {
        assertNoReconocido("91.0, 2.0")
        assertNoReconocido("-90.5, 2.0")
        assertNoReconocido("39.8, 180.5")
        assertNoReconocido("39.8, -181.0")
    }

    @Test
    fun `texto vacio o sin coordenadas no se reconoce`() {
        assertNoReconocido("")
        assertNoReconocido("   ")
        assertNoReconocido("Sa Calobra")
        assertNoReconocido("Cala Sant Vicenç, 07460 Pollença")
    }

    // --- Sexagesimales ---

    @Test
    fun `grados minutos y segundos con hemisferio`() {
        assertPunto(39.83, 2.81, null, "39°49'48\"N 2°48'36\"E")
        assertPunto(39.8505, 2.7997, null, "39°51'1.8\"N 2°47'58.92\"E", 1e-6)
    }

    @Test
    fun `grados y minutos decimales con hemisferio`() {
        assertPunto(39.83, 2.81, null, "39°49.8'N 2°48.6'E")
        assertPunto(39.83, 2.81, null, "39°49.8′N, 2°48.6′E")
    }

    @Test
    fun `hemisferio delante y sin simbolos`() {
        assertPunto(39.83, 2.81, null, "N39 49.8 E2 48.6")
        assertPunto(39.83, 2.81, null, "N 39 49 48 E 2 48 36")
    }

    @Test
    fun `grados decimales con hemisferio y signo por hemisferio`() {
        assertPunto(39.83, 2.81, null, "39.83°N 2.81°E")
        assertPunto(-33.9, -70.5, null, "33.9°S 70.5°W")
        assertPunto(39.83, -2.81, null, "N39.83 W2.81")
    }

    @Test
    fun `longitud primero tambien vale`() {
        assertPunto(39.83, 2.81, null, "2°48.6'E 39°49.8'N")
    }

    @Test
    fun `sexagesimales invalidos no se reconocen`() {
        assertNoReconocido("39°75'N 2°48.6'E") // minutos >= 60
        assertNoReconocido("39°49'75\"N 2°48'36\"E") // segundos >= 60
        assertNoReconocido("39°49.8'N 2°48.6'N") // dos latitudes
        assertNoReconocido("39°49.8'N") // falta la longitud
        assertNoReconocido("95°00'N 2°48.6'E") // fuera de rango
        assertNoReconocido("39.5°49'N 2°48.6'E") // grados decimales con minutos
    }

    // --- geo: ---

    @Test
    fun `geo con coordenadas`() {
        assertPunto(39.8505, 2.7997, null, "geo:39.8505,2.7997")
        assertPunto(39.83, 2.81, null, "geo:39.83,2.81;u=35")
        assertPunto(39.83, 2.81, null, "GEO:39.83,2.81,120")
    }

    @Test
    fun `geo con q de nombre`() {
        assertPunto(39.8505, 2.7997, "Sa Calobra", "geo:39.8505,2.7997?q=Sa+Calobra")
        assertPunto(39.7667, 2.7150, "Sóller", "geo:39.7667,2.7150?q=S%C3%B3ller")
    }

    @Test
    fun `geo con q de coordenadas y nombre entre parentesis`() {
        assertPunto(39.7667, 2.7150, "Sóller", "geo:39.7667,2.7150?q=39.7667,2.7150(S%C3%B3ller)")
        assertPunto(39.7667, 2.7150, "Sóller", "geo:0,0?q=39.7667,2.7150(S%C3%B3ller)")
        assertPunto(39.7667, 2.7150, null, "geo:0,0?q=39.7667,2.7150")
    }

    @Test
    fun `geo sin ubicacion o fuera de rango no se reconoce`() {
        assertNoReconocido("geo:0,0?q=Palma")
        assertNoReconocido("geo:91,2")
        assertNoReconocido("geo:39.8")
    }

    // --- OpenStreetMap ---

    @Test
    fun `osm con marcador manda sobre el centro del mapa`() {
        assertPunto(
            39.8505,
            2.7997,
            null,
            "https://www.openstreetmap.org/?mlat=39.8505&mlon=2.7997#map=15/39.85/2.80",
        )
        assertPunto(39.7667, 2.7150, null, "https://www.openstreetmap.org/?mlat=39.7667&mlon=2.7150")
    }

    @Test
    fun `osm con centro del mapa en el fragmento`() {
        assertPunto(39.7667, 2.7150, null, "https://www.openstreetmap.org/#map=15/39.7667/2.7150")
        assertPunto(39.8505, 2.7997, null, "https://www.openstreetmap.org/#map=17/39.8505/2.7997&layers=N")
        assertPunto(-33.9, 151.2, null, "https://osm.org/#map=12/-33.9/151.2")
    }

    @Test
    fun `osm fuera de rango o sin ubicacion no se reconoce`() {
        assertNoReconocido("https://www.openstreetmap.org/?mlat=95.0&mlon=2.7997")
        assertNoReconocido("https://www.openstreetmap.org/")
        assertNoReconocido("https://www.openstreetmap.org/node/123456789")
    }

    @Test
    fun `enlace corto de osm necesita red`() {
        val url = "https://osm.org/go/xyz123-?m="
        assertEquals(ResultadoEnlace.NecesitaRed(url), EnlaceUbicacion.leer(url))
    }

    // --- Google Maps ---

    @Test
    fun `google con centro de la vista`() {
        assertPunto(39.8505, 2.7997, null, "https://www.google.com/maps/@39.8505,2.7997,15z")
        assertPunto(-33.8688, 151.2093, null, "https://www.google.com/maps/@-33.8688,151.2093,12z")
    }

    @Test
    fun `google place con nombre y centro`() {
        assertPunto(
            39.8505,
            2.7997,
            "Sa Calobra",
            "https://www.google.com/maps/place/Sa+Calobra/@39.8505,2.7997,15z",
        )
        assertPunto(
            39.7667,
            2.7150,
            "Sóller",
            "https://www.google.es/maps/place/S%C3%B3ller/@39.7667,2.7150,14z/data=!3m1!4b1",
        )
    }

    @Test
    fun `google prioriza el sitio de 3d 4d sobre el centro de la vista`() {
        assertPunto(
            39.8505,
            2.7997,
            "Sa Calobra, 07315 Escorca, Illes Balears",
            "https://www.google.com/maps/place/Sa+Calobra,+07315+Escorca,+Illes+Balears/@39.8508,2.8001,17z/" +
                "data=!3m1!4b1!4m6!3m5!1s0x1297b5a1c1f0b4a3:0x5d8f1e0f5a3c6b7d!8m2!3d39.8505!4d2.7997!16s%2Fg%2F11b6",
        )
        assertPunto(
            39.7667,
            2.7150,
            null,
            "https://www.google.com/maps/@39.7700,2.7200,15z/data=!3m1!4b1!4m2!3d39.7667!4d2.7150",
        )
    }

    @Test
    fun `google con parametro q o query`() {
        assertPunto(39.7667, 2.7150, null, "https://www.google.com/maps?q=39.7667,2.7150")
        assertPunto(39.7667, 2.7150, null, "https://maps.google.com/?q=39.7667,2.7150")
        assertPunto(39.7667, 2.7150, null, "https://www.google.com/maps/search/?api=1&query=39.7667%2C2.7150")
        assertPunto(39.7667, 2.7150, "Sóller", "https://www.google.com/maps?q=39.7667,2.7150(S%C3%B3ller)")
    }

    @Test
    fun `google ruta con destino en coordenadas`() {
        assertPunto(39.8505, 2.7997, null, "https://www.google.com/maps/dir/?api=1&destination=39.8505,2.7997")
    }

    @Test
    fun `google place cuyo nombre son las coordenadas`() {
        assertPunto(39.8505, 2.7997, null, "https://www.google.com/maps/place/39.8505,2.7997")
    }

    @Test
    fun `google sin coordenadas o fuera de rango no se reconoce`() {
        assertNoReconocido("https://www.google.com/maps/place/Palma")
        assertNoReconocido("https://www.google.com/maps?q=Sa+Calobra")
        assertNoReconocido("https://www.google.com/maps/@91.0,2.7997,15z")
        assertNoReconocido("https://www.google.com/maps/@39.8505,200.0,15z")
    }

    @Test
    fun `enlaces cortos de google necesitan red`() {
        listOf(
            "https://maps.app.goo.gl/AbCdEf123456GhIj",
            "https://goo.gl/maps/AbCdEf123456GhIj",
            "http://goo.gl/maps/Xy9z",
        ).forEach { url -> assertEquals(ResultadoEnlace.NecesitaRed(url), EnlaceUbicacion.leer(url)) }
    }

    @Test
    fun `texto compartido con nombre y enlace usa el enlace`() {
        assertEquals(
            ResultadoEnlace.NecesitaRed("https://maps.app.goo.gl/AbCdEf123456GhIj"),
            EnlaceUbicacion.leer("Sa Calobra\nhttps://maps.app.goo.gl/AbCdEf123456GhIj"),
        )
        assertPunto(
            39.8505,
            2.7997,
            null,
            "Sa Calobra https://www.google.com/maps/@39.8505,2.7997,15z",
        )
    }

    @Test
    fun `otros sitios web no se reconocen aunque lleven coordenadas`() {
        assertNoReconocido("https://example.com/@39.8505,2.7997,15z")
        assertNoReconocido("https://www.google.com/search?q=39.8505,2.7997")
        assertNoReconocido("https://notgoogle.com/maps/@39.8505,2.7997,15z")
    }
}
