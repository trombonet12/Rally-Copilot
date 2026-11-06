package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorSenalGpsTest {
    private fun fija(
        tiempoMs: Long,
        precisionM: Double? = 5.0,
    ) = Fijacion(PuntoGeo(39.7, 2.9), tiempoMs, 10.0, rumboGrados = 90.0, precisionM = precisionM)

    @Test
    fun `sin ninguna fijacion no hay perdida que avisar`() {
        assertFalse(DetectorSenalGps().sinSenal(60_000))
    }

    @Test
    fun `tras 5 s sin fijacion buena se pierde la senal y una nueva la recupera`() {
        val detector = DetectorSenalGps()
        detector.fijacion(fija(1_000))
        assertFalse(detector.sinSenal(6_000))
        assertTrue(detector.sinSenal(6_001))
        detector.fijacion(fija(8_000))
        assertFalse(detector.sinSenal(8_500))
    }

    @Test
    fun `una fijacion de mas de 50 m de error no cuenta como senal`() {
        val detector = DetectorSenalGps()
        detector.fijacion(fija(1_000))
        detector.fijacion(fija(5_000, precisionM = 80.0))
        assertTrue("las malas no renuevan el plazo", detector.sinSenal(6_500))
        detector.fijacion(fija(7_000, precisionM = 50.0))
        assertFalse("50 m justos todavia vale", detector.sinSenal(7_500))
    }

    @Test
    fun `sin precision conocida la fijacion vale`() {
        val detector = DetectorSenalGps()
        detector.fijacion(fija(1_000, precisionM = null))
        assertFalse(detector.sinSenal(3_000))
    }
}

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

    @Test
    fun `la distancia a la polilinea es la del segmento mas cercano`() {
        val linea = listOf(PuntoGeo(39.70, 2.90), PuntoGeo(39.70, 2.91), PuntoGeo(39.71, 2.91))
        assertEquals(0.0, distanciaAPolilineaM(PuntoGeo(39.705, 2.91), linea), 0.5)
        assertEquals(111.0, distanciaAPolilineaM(PuntoGeo(39.701, 2.90), listOf(linea[0], linea[0], linea[0])), 1.0)
    }
}
