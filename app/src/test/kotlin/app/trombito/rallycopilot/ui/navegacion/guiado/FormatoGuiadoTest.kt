package app.trombito.rallycopilot.ui.navegacion.guiado

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class FormatoGuiadoTest {
    private val utc = ZoneId.of("UTC")
    private val madrid = ZoneId.of("Europe/Madrid")

    @Test
    fun `la hora de llegada usa 24 horas y la zona inyectada`() {
        // 2023-11-14T22:13:20Z
        assertEquals("22:13", formatearHoraLlegada(1_700_000_000_000L, utc))
        assertEquals("23:13", formatearHoraLlegada(1_700_000_000_000L, madrid))
    }

    @Test
    fun `la hora de llegada rellena con ceros`() {
        assertEquals("05:07", formatearHoraLlegada((5 * 3600 + 7 * 60) * 1000L, utc))
    }

    @Test
    fun `menos de un minuto`() {
        assertEquals("< 1 min", formatearTiempoRestante(0.0))
        assertEquals("< 1 min", formatearTiempoRestante(59.9))
        assertEquals("< 1 min", formatearTiempoRestante(-5.0))
    }

    @Test
    fun `minutos sueltos`() {
        assertEquals("1 min", formatearTiempoRestante(60.0))
        assertEquals("12 min", formatearTiempoRestante(12 * 60 + 40.0))
        assertEquals("59 min", formatearTiempoRestante(59 * 60 + 59.0))
    }

    @Test
    fun `horas y minutos con dos cifras`() {
        assertEquals("1 h 00 min", formatearTiempoRestante(3600.0))
        assertEquals("1 h 05 min", formatearTiempoRestante(3900.0))
        assertEquals("2 h 30 min", formatearTiempoRestante(9000.0))
    }

    @Test
    fun `distancia en kilometros con coma decimal`() {
        assertEquals("12,4 km", formatearDistanciaRestante(12_400.0))
        assertEquals("1,0 km", formatearDistanciaRestante(1_000.0))
        assertEquals("45,0 km", formatearDistanciaRestante(44_960.0))
    }

    @Test
    fun `por debajo de un kilometro va en metros a 10 m`() {
        assertEquals("850 m", formatearDistanciaRestante(851.0))
        assertEquals("0 m", formatearDistanciaRestante(3.0))
        assertEquals("0 m", formatearDistanciaRestante(-20.0))
    }

    @Test
    fun `cerca de un kilometro salta a kilometros`() {
        assertEquals("990 m", formatearDistanciaRestante(994.0))
        assertEquals("1,0 km", formatearDistanciaRestante(996.0))
    }
}
