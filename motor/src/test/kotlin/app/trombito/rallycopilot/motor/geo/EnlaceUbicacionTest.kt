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
        assertNoReconocido("39,5,2,6")
        assertNoReconocido("39,83")
        assertNoReconocido("39,5 2")
        assertNoReconocido("39.5,2,6")
    }

    // --- Lo que pega Google Maps en español (coma decimal) ---

    @Test
    fun `caso real de Google Maps en espanol con coma decimal`() {
        assertPunto(39.5789682, 2.6515765, null, "39,5789682, 2,6515765")
    }

    @Test
    fun `coma decimal con separador coma, punto y coma o espacios`() {
        assertPunto(39.83, 2.81, null, "39,83 2,81")
        assertPunto(39.83, 2.81, null, "39,83   2,81")
        assertPunto(39.83, 2.81, null, "39,83; 2,81")
        assertPunto(39.83, 2.81, null, "39,83;2,81")
        assertPunto(39.83, 2.81, null, "39,83 ; 2,81")
        assertPunto(39.8505, 2.7997, null, "39,8505 2,7997")
        assertPunto(-33.8688, 151.2093, null, "-33,8688, 151,2093")
        assertPunto(40.4168, -3.7038, null, "40,4168, -3,7038")
    }

    @Test
    fun `coma decimal fuera de rango no se reconoce`() {
        assertNoReconocido("91,0 2,0")
        assertNoReconocido("39,8 180,5")
        assertNoReconocido("-90,5, 2,0")
    }

    // --- Normalizacion del texto pegado ---

    @Test
    fun `espacios unicode se tratan como espacio`() {
        assertPunto(39.5789682, 2.6515765, null, "39,5789682,\u00A02,6515765")
        assertPunto(39.5789682, 2.6515765, null, "39.5789682,\u202F2.6515765")
        assertPunto(39.5789682, 2.6515765, null, "39.5789682\u2009\u20092.6515765")
        assertPunto(39.5789682, 2.6515765, null, "39.5789682,\u30002.6515765")
    }

    @Test
    fun `caracteres invisibles se quitan`() {
        assertPunto(39.5789682, 2.6515765, null, "\u200E39.5789682, 2.6515765\u200E")
        assertPunto(39.5789682, 2.6515765, null, "\uFEFF39.5789682, 2.6515765")
        assertPunto(39.5789682, 2.6515765, null, "39.5789682,\u200B 2.6515765\u200F")
        assertPunto(39.5789682, 2.6515765, null, "39,5789682,\u200E 2,6515765")
    }

    @Test
    fun `puntuacion sobrante en los bordes se quita`() {
        assertPunto(39.5789682, 2.6515765, null, "(39.5789682, 2.6515765)")
        assertPunto(39.5789682, 2.6515765, null, "\u201C39,5789682, 2,6515765\u201D")
        assertPunto(39.5789682, 2.6515765, null, "39.5789682, 2.6515765.")
        assertPunto(39.5789682, 2.6515765, null, "\"39.5789682, 2.6515765\";")
    }

    @Test
    fun `menos tipografico vale como signo`() {
        assertPunto(39.5, -2.5, null, "39.5, \u22122.5")
    }

    // --- Sexagesimales con simbolos tipograficos ---

    @Test
    fun `dms con ordinal y comillas rectas`() {
        assertPunto(39 + 34 / 60.0 + 44.3 / 3600, 2 + 39 / 60.0 + 5.7 / 3600, null, "39º34'44.3\"N 2º39'05.7\"E", 1e-9)
    }

    @Test
    fun `dms con simbolos tipograficos`() {
        val lat = 39 + 34 / 60.0 + 44.3 / 3600
        val lon = 2 + 39 / 60.0 + 5.7 / 3600
        assertPunto(lat, lon, null, "39°34\u203244.3\u2033N 2°39\u203205.7\u2033E", 1e-9)
        assertPunto(lat, lon, null, "39\u02DA34\u203244.3\u2033N 2\u02DA39\u203205.7\u2033E", 1e-9)
        assertPunto(lat, lon, null, "39°34\u201944.3\u201DN 2°39\u201905.7\u201DE", 1e-9)
        assertPunto(lat, lon, null, "39°34\u201844.3\u201CN 2°39\u201805.7\u201CE", 1e-9)
        assertPunto(lat, lon, null, "39°34'44.3''N 2°39'05.7''E", 1e-9)
        assertPunto(lat, lon, null, "\u200E39°34'44.3\"N\u00A02°39'05.7\"E\u200E", 1e-9)
    }

    // --- Enlaces sin esquema ---

    @Test
    fun `google sin esquema`() {
        assertPunto(39.57, 2.65, null, "www.google.com/maps/@39.57,2.65,15z")
        assertPunto(39.57, 2.65, null, "maps.google.com/?q=39.57,2.65")
        assertPunto(39.7667, 2.7150, "Sóller", "google.es/maps/place/S%C3%B3ller/@39.7667,2.7150,14z")
        assertPunto(39.57, 2.65, null, "https://www.google.cat/maps/@39.57,2.65,15z")
        assertPunto(39.57, 2.65, null, "google.com.mx/maps/@39.57,2.65,15z")
    }

    @Test
    fun `enlaces cortos sin esquema necesitan red con https`() {
        assertEquals(
            ResultadoEnlace.NecesitaRed("https://maps.app.goo.gl/AbCdEf123"),
            EnlaceUbicacion.leer("maps.app.goo.gl/AbCdEf123"),
        )
        assertEquals(
            ResultadoEnlace.NecesitaRed("https://goo.gl/maps/Xy9z"),
            EnlaceUbicacion.leer("goo.gl/maps/Xy9z"),
        )
        assertEquals(
            ResultadoEnlace.NecesitaRed("https://maps.app.goo.gl/AbCdEf123"),
            EnlaceUbicacion.leer("Sa Calobra\nmaps.app.goo.gl/AbCdEf123"),
        )
    }

    @Test
    fun `puntuacion al final de un enlace no forma parte de el`() {
        assertEquals(
            ResultadoEnlace.NecesitaRed("https://maps.app.goo.gl/AbCdEf123"),
            EnlaceUbicacion.leer("(https://maps.app.goo.gl/AbCdEf123)."),
        )
    }

    @Test
    fun `dominios que no son de mapas no se reconocen sin esquema`() {
        assertNoReconocido("example.com/@39.8505,2.7997,15z")
        assertNoReconocido("notgoogle.com/maps/@39.8505,2.7997,15z")
        assertNoReconocido("google.com.evil.com/maps/@39.8505,2.7997,15z")
    }

    // --- Plus Codes ---

    @Test
    fun `plus code completo`() {
        assertPunto(39.5789375, 2.6515625, null, "8FF4HMH2+HJ", 1e-7)
        assertPunto(39.5789375, 2.6515625, null, "8ff4hmh2+hj", 1e-7)
        assertPunto(39.5789375, 2.6515625, null, "8FF4HMH2+HJ Palma, Illes Balears", 1e-7)
        assertPunto(37.4220625, -122.0840625, null, "849VCWC8+R9", 1e-7)
    }

    @Test
    fun `plus code corto con localidad se recupera en el centro de Mallorca`() {
        assertPunto(39.5789375, 2.6515625, null, "HMH2+HJ Palma", 1e-7)
        assertPunto(39.5789375, 2.6515625, null, "hmh2+hj palma, illes balears", 1e-7)
        assertPunto(39.5789375, 2.6515625, null, "HMH2+HJ", 1e-7)
    }

    @Test
    fun `plus code invalido no se reconoce`() {
        assertNoReconocido("8FF4HMH2+H") // un solo carácter tras el +
        assertNoReconocido("8FF4HMH2") // sin +
        assertNoReconocido("HMH+HJ") // longitud impar
        assertNoReconocido("X2222222+") // latitud fuera de rango
        assertNoReconocido("AAAA+BB") // letras fuera del alfabeto
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
        assertPunto(39.83, 2.81, null, "39°49.8\u2032N, 2°48.6\u2032E")
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
