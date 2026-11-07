package app.trombito.rallycopilot.motor.velocidad

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorSenalGpsTest {
    private fun fija(
        tiempoMs: Long,
        precisionM: Double? = 5.0,
    ) = Fijacion(PuntoGeo(39.7, 2.9), tiempoMs, 10.0, rumboGrados = 90.0, precisionM = precisionM)

    /** Simula [hasta] ms de reloj con un tick por segundo; las fijaciones llegan en el segundo de su `tiempoMs`. */
    private fun DetectorSenalGps.simular(
        fijaciones: List<Fijacion>,
        hastaMs: Long,
        desdeMs: Long = 0L,
    ): List<Boolean> {
        val estados = mutableListOf<Boolean>()
        var t = desdeMs
        while (t <= hastaMs) {
            fijaciones.filter { it.tiempoMs == t }.forEach { fijacion(it, t) }
            estados += sinSenal(t)
            t += 1_000
        }
        return estados
    }

    private fun transiciones(estados: List<Boolean>) = estados.zipWithNext().count { it.first != it.second }

    @Test
    fun `sin ninguna fijacion no hay perdida que avisar`() {
        assertFalse(DetectorSenalGps().sinSenal(60_000))
    }

    @Test
    fun `tras 10 s sin fijacion util se pierde la senal`() {
        val detector = DetectorSenalGps()
        detector.fijacion(fija(1_000), 1_000)
        assertFalse(detector.sinSenal(11_000))
        assertTrue(detector.sinSenal(11_001))
    }

    @Test
    fun `una perdida de menos de 10 s no se declara aunque la fijacion se retrase`() {
        val detector = DetectorSenalGps()
        detector.fijacion(fija(1_000), 1_000)
        assertFalse(detector.sinSenal(8_000))
        detector.fijacion(fija(9_000), 9_000)
        assertFalse(detector.sinSenal(15_000))
    }

    @Test
    fun `con buenas fijaciones cada 6 s no oscila`() {
        val detector = DetectorSenalGps()
        val buenas = listOf(1_000L, 7_000, 13_000, 19_000, 25_000).map { fija(it) }
        val estados = detector.simular(buenas, 30_000, desdeMs = 1_000)
        assertTrue("transiciones: ${transiciones(estados)}", transiciones(estados) <= 1)
        assertFalse(estados.any { it })
    }

    @Test
    fun `una sola fijacion buena no recupera tras una perdida larga`() {
        val detector = DetectorSenalGps()
        detector.fijacion(fija(1_000), 1_000)
        assertTrue(detector.sinSenal(20_000))
        detector.fijacion(fija(21_000), 21_000)
        assertTrue("1 util", detector.sinSenal(21_000))
        detector.fijacion(fija(22_000), 22_000)
        assertTrue("2 utiles", detector.sinSenal(22_000))
        detector.fijacion(fija(23_000), 23_000)
        assertFalse("3 utiles seguidas", detector.sinSenal(23_000))
    }

    @Test
    fun `una fijacion mala corta la racha de recuperacion`() {
        val detector = DetectorSenalGps()
        detector.fijacion(fija(1_000), 1_000)
        assertTrue(detector.sinSenal(20_000))
        detector.fijacion(fija(21_000), 21_000)
        detector.fijacion(fija(22_000), 22_000)
        detector.fijacion(fija(23_000, precisionM = 45.0), 23_000)
        detector.fijacion(fija(24_000), 24_000)
        assertTrue(detector.sinSenal(24_000))
        detector.fijacion(fija(25_000), 25_000)
        detector.fijacion(fija(26_000), 26_000)
        assertFalse(detector.sinSenal(26_000))
    }

    @Test
    fun `tres segundos de utiles seguidas tambien recuperan`() {
        val detector = DetectorSenalGps()
        detector.fijacion(fija(1_000), 1_000)
        assertTrue(detector.sinSenal(20_000))
        detector.fijacion(fija(21_000), 21_000)
        detector.fijacion(fija(24_000), 24_000)
        assertFalse(detector.sinSenal(24_000))
    }

    @Test
    fun `precision que baila alrededor del umbral cambia de estado como mucho una vez en 30 s`() {
        val detector = DetectorSenalGps()
        val bailan = listOf(25.0, 35.0, 28.0, 40.0, 29.0, 31.0, 30.0, 45.0, 27.0, 33.0)
        val fijaciones = bailan.mapIndexed { i, p -> fija(1_000L + i * 1_000, precisionM = p) }
        val estados = detector.simular(fijaciones, 40_000, desdeMs = 1_000)
        assertTrue("transiciones: ${transiciones(estados)}", transiciones(estados) <= 1)
    }

    @Test
    fun `precision mala sostenida es perdida y bailar junto al umbral no la recupera`() {
        val detector = DetectorSenalGps()
        detector.fijacion(fija(1_000), 1_000)
        val malas = (0 until 10).map { i -> fija(2_000L + i * 1_000, precisionM = 40.0) }
        val bailan = (0 until 20).map { i -> fija(12_000L + i * 1_000, precisionM = if (i % 2 == 0) 31.0 else 29.0) }
        val estados = detector.simular(malas + bailan, 31_000, desdeMs = 2_000)
        assertTrue("sale la banda y no se quita", estados.last())
        assertTrue("transiciones: ${transiciones(estados)}", transiciones(estados) <= 1)
    }

    @Test
    fun `una fijacion con mas de 5 s de antiguedad respecto al reloj no cuenta como senal`() {
        val detector = DetectorSenalGps()
        // La «última conocida» al registrar el GPS llega con su marca de hace un minuto.
        detector.fijacion(fija(40_000), 100_000)
        assertFalse(detector.sinSenal(103_000))
        assertFalse("sin senal util todavia no hay perdida que avisar", detector.sinSenal(160_000))
        assertNull(detector.ultimaUtilMs)
    }

    @Test
    fun `una antigua no renueva una senal que ya existia`() {
        val detector = DetectorSenalGps()
        detector.fijacion(fija(100_000), 100_000)
        detector.fijacion(fija(90_000), 110_000)
        assertEquals(100_000L, detector.ultimaUtilMs)
        assertTrue(detector.sinSenal(110_001))
    }

    @Test
    fun `un tiempo que retrocede o se repite ni rompe ni recupera falsamente`() {
        val detector = DetectorSenalGps()
        detector.fijacion(fija(10_000), 10_000)
        assertTrue(detector.sinSenal(25_000))
        // Tres fijaciones con el mismo tiempo y una anterior: ninguna cuenta.
        detector.fijacion(fija(26_000), 26_000)
        detector.fijacion(fija(26_000), 26_100)
        detector.fijacion(fija(26_000), 26_200)
        detector.fijacion(fija(25_500), 26_300)
        assertTrue(detector.sinSenal(26_300))
        assertEquals(26_000L, detector.ultimaUtilMs)
    }

    @Test
    fun `sin precision conocida la fijacion vale y NaN no`() {
        val detector = DetectorSenalGps()
        detector.fijacion(fija(1_000, precisionM = null), 1_000)
        assertEquals(1_000L, detector.ultimaUtilMs)
        detector.fijacion(fija(2_000, precisionM = Double.NaN), 2_000)
        assertEquals("NaN no es util", 1_000L, detector.ultimaUtilMs)
        assertFalse(detector.sinSenal(3_000))
    }

    @Test
    fun `30 m justos todavia vale y 30,1 no`() {
        val detector = DetectorSenalGps()
        detector.fijacion(fija(1_000, precisionM = 30.0), 1_000)
        assertEquals(1_000L, detector.ultimaUtilMs)
        detector.fijacion(fija(2_000, precisionM = 30.1), 2_000)
        assertEquals(1_000L, detector.ultimaUtilMs)
    }
}
