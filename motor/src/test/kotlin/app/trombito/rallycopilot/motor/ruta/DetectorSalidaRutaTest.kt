package app.trombito.rallycopilot.motor.ruta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorSalidaRutaTest {
    @Test
    fun `dentro de la ruta nunca avisa`() {
        val d = DetectorSalidaRuta()
        for (t in 0..20) assertFalse(d.actualizar(5.0, t * 1000L))
    }

    @Test
    fun `no avisa hasta que pasa el tiempo continuado fuera del umbral`() {
        val d = DetectorSalidaRuta(umbralM = 40.0, tiempoMs = 5000)
        assertFalse(d.actualizar(60.0, 1000))
        assertFalse(d.actualizar(60.0, 3000))
        assertFalse(d.actualizar(60.0, 5999))
        assertTrue(d.actualizar(60.0, 6000))
        assertTrue(d.actualizar(60.0, 7000))
    }

    @Test
    fun `volver dentro del umbral antes de tiempo reinicia la cuenta`() {
        val d = DetectorSalidaRuta()
        assertFalse(d.actualizar(60.0, 0))
        assertFalse(d.actualizar(60.0, 4000))
        assertFalse(d.actualizar(10.0, 4500))
        assertFalse(d.actualizar(60.0, 5000))
        assertFalse(d.actualizar(60.0, 9000))
        assertTrue(d.actualizar(60.0, 10_000))
    }

    @Test
    fun `el umbral es estricto`() {
        val d = DetectorSalidaRuta(umbralM = 40.0, tiempoMs = 1000)
        assertFalse(d.actualizar(40.0, 0))
        assertFalse(d.actualizar(40.0, 5000))
    }

    @Test
    fun `histeresis la vuelta exige bajar de la mitad del umbral`() {
        val d = DetectorSalidaRuta(umbralM = 40.0, tiempoMs = 1000)
        d.actualizar(60.0, 0)
        assertTrue(d.actualizar(60.0, 1000))
        assertTrue(d.actualizar(30.0, 2000))
        assertTrue(d.actualizar(20.0, 3000))
        assertFalse(d.actualizar(19.9, 4000))
    }

    @Test
    fun `tras volver puede salir de nuevo`() {
        val d = DetectorSalidaRuta(umbralM = 40.0, tiempoMs = 1000)
        d.actualizar(60.0, 0)
        assertTrue(d.actualizar(60.0, 1000))
        assertFalse(d.actualizar(0.0, 2000))
        assertFalse(d.actualizar(60.0, 3000))
        assertTrue(d.actualizar(60.0, 4000))
    }
}
