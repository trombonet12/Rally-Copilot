package app.trombito.rallycopilot.motor.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlusCodeTest {
    private val referencia = PuntoGeo(39.6, 2.9)
    private val tol = 1e-7

    private fun assertCentro(
        lat: Double,
        lon: Double,
        texto: String,
        ref: PuntoGeo = referencia,
    ) {
        val p = PlusCode.leer(texto, ref)
        assertNotNull("«$texto»", p)
        assertEquals("lat de «$texto»", lat, p!!.lat, tol)
        assertEquals("lon de «$texto»", lon, p.lon, tol)
    }

    @Test
    fun `decodifica el centro de codigos completos conocidos`() {
        assertCentro(37.4220625, -122.0840625, "849VCWC8+R9") // Googleplex
        assertCentro(20.375, 2.775, "7FG49Q00+") // vector del OLC: 20.35,2.75 – 20.4,2.8
        assertCentro(39.5, 2.5, "8FF40000+")
        assertCentro(39.5789375, 2.6515625, "8FF4HMH2+HJ")
    }

    @Test
    fun `acepta minusculas y texto de localidad detras`() {
        assertCentro(39.5789375, 2.6515625, "8ff4hmh2+hj")
        assertCentro(39.5789375, 2.6515625, "8FF4HMH2+HJ Palma, Illes Balears")
        assertCentro(39.5789375, 2.6515625, "8FF4HMH2+HJ, Palma")
    }

    @Test
    fun `decodifica codigos de mas de diez digitos`() {
        // 8FF4HMH2+HJ más un dígito de rejilla (fila 0, columna 0): el área se reduce y el centro baja.
        val p = PlusCode.leer("8FF4HMH2+HJ2", referencia)!!
        assertEquals(39.578875 + 0.000025 / 2, p.lat, tol)
        assertEquals(2.6515 + 0.000125 / 4 / 2, p.lon, tol)
    }

    @Test
    fun `recupera codigos cortos con la referencia`() {
        assertCentro(39.5789375, 2.6515625, "HMH2+HJ")
        assertCentro(39.5789375, 2.6515625, "HMH2+HJ Palma")
    }

    @Test
    fun `recuperar elige la celda mas cercana a la referencia`() {
        // Con la referencia en lon 3.0 el prefijo caería en la celda [3,4): el área recuperada se desplaza 1 grado.
        assertCentro(39.5789375, 2.6515625, "HMH2+HJ", PuntoGeo(39.5, 3.0))
        // Lo mismo en latitud, y al revés (hacia arriba) cuando el sitio queda en la parte baja de su celda.
        assertCentro(39.5789375, 2.6515625, "HMH2+HJ", PuntoGeo(40.0, 2.2))
        assertCentro(40.2013125, 2.6515625, "6M22+GJ", PuntoGeo(39.95, 2.65))
    }

    @Test
    fun `recupera codigos cortos de seis y dos caracteres`() {
        // Quitando 2 dígitos (celda de 20°) y quitando 6 (celda de 0,05°, con la referencia cerca).
        assertCentro(39.5789375, 2.6515625, "F4HMH2+HJ", referencia)
        assertCentro(39.5789375, 2.6515625, "H2+HJ", PuntoGeo(39.58, 2.652))
    }

    @Test
    fun `codifica a diez digitos`() {
        assertEquals("8FF4HMH2+HJ", PlusCode.codificar(39.5789682, 2.6515765))
        assertEquals("849VCWC8+R9", PlusCode.codificar(37.4220625, -122.0840625))
    }

    @Test
    fun `codigos invalidos devuelven null`() {
        listOf(
            "8FF4HMH2+H", // un solo carácter tras el +
            "8FF4HMH2", // sin +
            "HMH+HJ", // longitud impar antes del +
            "X2222222+", // latitud fuera de rango
            "2X222222+", // longitud fuera de rango (la X de longitud es índice 19 > 17)
            "AAAA+BB", // letras fuera del alfabeto
            "8F94HM0H+HJ", // relleno seguido de dígitos
            "8FF4HMH2+HJ0", // relleno tras el +
            "8FF4HMH2++HJ",
            "+HJ",
            "",
            "Sa Calobra",
        ).forEach { assertNull("«$it»", PlusCode.leer(it, referencia)) }
    }
}
