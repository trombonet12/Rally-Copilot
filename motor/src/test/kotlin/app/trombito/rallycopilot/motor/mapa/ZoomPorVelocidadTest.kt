package app.trombito.rallycopilot.motor.mapa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomPorVelocidadTest {
    private val delta = 1e-9

    @Test
    fun `la curva pasa por los puntos de control`() {
        val esperado =
            mapOf(0.0 to 17.0, 20.0 to 17.0, 30.0 to 16.5, 50.0 to 16.0, 90.0 to 15.0, 120.0 to 14.0, 200.0 to 14.0)
        esperado.forEach { (kmh, zoom) -> assertEquals("a $kmh km/h", zoom, ZoomPorVelocidad.zoom(kmh), delta) }
    }

    @Test
    fun `entre puntos de control interpola linealmente`() {
        assertEquals(16.75, ZoomPorVelocidad.zoom(25.0), delta)
        assertEquals(16.25, ZoomPorVelocidad.zoom(40.0), delta)
        assertEquals(15.5, ZoomPorVelocidad.zoom(70.0), delta)
        assertEquals(14.5, ZoomPorVelocidad.zoom(105.0), delta)
    }

    @Test
    fun `es monotona decreciente y continua`() {
        var anterior = ZoomPorVelocidad.zoom(0.0)
        for (decima in 1..2500) {
            val zoom = ZoomPorVelocidad.zoom(decima / 10.0)
            assertTrue("sube a ${decima / 10.0} km/h", zoom <= anterior)
            assertTrue("salto a ${decima / 10.0} km/h", anterior - zoom <= 0.005 + 1e-9)
            anterior = zoom
        }
    }

    @Test
    fun `cada nivel desplaza medio punto, positivo es mas cerca`() {
        assertEquals(16.5, ZoomPorVelocidad.zoom(50.0, 1), delta)
        assertEquals(17.0, ZoomPorVelocidad.zoom(50.0, 2), delta)
        assertEquals(15.5, ZoomPorVelocidad.zoom(50.0, -1), delta)
        assertEquals(15.0, ZoomPorVelocidad.zoom(50.0, -2), delta)
        assertEquals(18.0, ZoomPorVelocidad.zoom(10.0, 2), delta)
        assertEquals(13.0, ZoomPorVelocidad.zoom(130.0, -2), delta)
    }

    @Test
    fun `el nivel se limita a menos 2 y 2`() {
        assertEquals(ZoomPorVelocidad.zoom(50.0, 2), ZoomPorVelocidad.zoom(50.0, 7), delta)
        assertEquals(ZoomPorVelocidad.zoom(50.0, -2), ZoomPorVelocidad.zoom(50.0, -9), delta)
    }

    @Test
    fun `el resultado siempre esta entre 12 y 18`() {
        for (nivel in -10..10) {
            for (kmh in listOf(-5.0, 0.0, 10.0, 35.0, 80.0, 119.0, 300.0, Double.POSITIVE_INFINITY, Double.NaN)) {
                val zoom = ZoomPorVelocidad.zoom(kmh, nivel)
                assertTrue("zoom $zoom con $kmh km/h nivel $nivel", zoom in 12.0..18.0)
            }
        }
    }

    @Test
    fun `NaN y negativos cuentan como parado, infinito como velocidad maxima`() {
        assertEquals(17.0, ZoomPorVelocidad.zoom(Double.NaN), delta)
        assertEquals(17.0, ZoomPorVelocidad.zoom(-30.0), delta)
        assertEquals(14.0, ZoomPorVelocidad.zoom(Double.POSITIVE_INFINITY), delta)
    }

    // --- SuavizadorZoom ---

    @Test
    fun `la primera llamada devuelve el zoom objetivo sin suavizar`() {
        val s = SuavizadorZoom()
        assertNull(s.zoomActual)
        assertEquals(16.0, s.actualizar(50.0, 0, 123_456), delta)
        assertEquals(16.0, s.zoomActual ?: Double.NaN, delta)
    }

    @Test
    fun `no cambia si la diferencia es menor de 0,25`() {
        val s = SuavizadorZoom()
        s.actualizar(50.0, 0, 0)
        assertEquals(16.0, s.actualizar(52.0, 0, 10_000), delta) // objetivo 15,95
        assertEquals(16.0, s.actualizar(55.0, 0, 20_000), delta) // objetivo 15,875
        assertEquals(16.0, s.actualizar(48.0, 0, 30_000), delta) // objetivo 16,05
    }

    @Test
    fun `cambia cuando la diferencia alcanza 0,25`() {
        val s = SuavizadorZoom()
        s.actualizar(50.0, 0, 0)
        assertEquals(15.75, s.actualizar(60.0, 0, 10_000), delta)
    }

    @Test
    fun `una vez fijado el objetivo se alcanza aunque la velocidad oscile dentro de la histeresis`() {
        val s = SuavizadorZoom()
        s.actualizar(20.0, 0, 0)
        assertEquals(16.5, s.actualizar(120.0, 0, 1_000), delta)
        assertEquals(16.0, s.actualizar(119.0, 0, 2_000), delta)
        assertEquals(15.5, s.actualizar(121.0, 0, 3_000), delta)
    }

    @Test
    fun `el zoom cambia como mucho 0,5 niveles por segundo`() {
        val s = SuavizadorZoom()
        s.actualizar(20.0, 0, 0)
        assertEquals(16.5, s.actualizar(120.0, 0, 1_000), delta)
        assertEquals(16.25, s.actualizar(120.0, 0, 1_500), delta)
        assertEquals(15.0, s.actualizar(120.0, 0, 4_000), delta)
        assertEquals(14.0, s.actualizar(120.0, 0, 6_000), delta)
        assertEquals(14.0, s.actualizar(120.0, 0, 9_000), delta)
    }

    @Test
    fun `tambien limita la subida`() {
        val s = SuavizadorZoom()
        s.actualizar(120.0, 0, 0)
        assertEquals(14.5, s.actualizar(10.0, 0, 1_000), delta)
        assertEquals(15.0, s.actualizar(10.0, 0, 2_000), delta)
    }

    @Test
    fun `un cambio de nivel mueve el zoom`() {
        val s = SuavizadorZoom()
        s.actualizar(50.0, 0, 0)
        assertEquals(16.5, s.actualizar(50.0, 1, 10_000), delta)
    }

    @Test
    fun `con la pausa se mantiene el zoom manual y despues vuelve suavemente`() {
        val s = SuavizadorZoom()
        s.actualizar(50.0, 0, 0)
        s.pausar(hastaMs = 5_000, zoomManual = 14.5)
        assertEquals(14.5, s.zoomActual ?: Double.NaN, delta)
        assertEquals(14.5, s.actualizar(20.0, 0, 1_000), delta)
        assertEquals(14.5, s.actualizar(20.0, 0, 4_999), delta)
        // Acaba la pausa: el objetivo es 17,0 pero se llega a 0,5 niveles/s.
        assertEquals(14.5, s.actualizar(20.0, 0, 5_000), 0.001)
        assertEquals(15.5, s.actualizar(20.0, 0, 7_000), 0.001)
        assertEquals(17.0, s.actualizar(20.0, 0, 20_000), delta)
    }

    @Test
    fun `pausar sin zoom manual conserva el zoom actual`() {
        val s = SuavizadorZoom()
        s.actualizar(50.0, 0, 0)
        s.pausar(hastaMs = 3_000)
        assertEquals(16.0, s.actualizar(120.0, 0, 2_000), delta)
        // Reanuda: 3 s desde la ultima lectura a 0,5 niveles/s son 1,5 niveles (el objetivo seria 14,0).
        assertEquals(14.5, s.actualizar(120.0, 0, 5_000), delta)
    }

    @Test
    fun `el zoom manual se acota a 12 y 18`() {
        val s = SuavizadorZoom()
        s.pausar(hastaMs = 1_000, zoomManual = 25.0)
        assertEquals(18.0, s.zoomActual ?: Double.NaN, delta)
        s.pausar(hastaMs = 2_000, zoomManual = 3.0)
        assertEquals(12.0, s.zoomActual ?: Double.NaN, delta)
    }

    @Test
    fun `pausar antes de la primera lectura no impide calcular el zoom`() {
        val s = SuavizadorZoom()
        s.pausar(hastaMs = 5_000)
        assertEquals(16.0, s.actualizar(50.0, 0, 0), delta)
    }

    @Test
    fun `un reloj que retrocede no mueve el zoom`() {
        val s = SuavizadorZoom()
        s.actualizar(20.0, 0, 10_000)
        assertEquals(17.0, s.actualizar(120.0, 0, 5_000), delta)
    }
}
