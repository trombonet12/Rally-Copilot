package app.trombito.rallycopilot.ui.resumen

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatoResumenTest {
    @Test
    fun `la duracion es un reloj`() {
        assertEquals("00:45", formatearDuracion(45L))
        assertEquals("40:00", formatearDuracion(2_400L))
        assertEquals("1:05:12", formatearDuracion(3_912L))
        assertEquals("00:00", formatearDuracion(-3L))
    }

    @Test
    fun `la duracion larga redondea a minutos y pasa a horas`() {
        assertEquals("5 min", formatearDuracionLarga(300L))
        assertEquals("5 min", formatearDuracionLarga(-310L))
        assertEquals("1 h 05 min", formatearDuracionLarga(3_900L))
        assertEquals(0, minutosDeDesviacion(20L))
        assertEquals(1, minutosDeDesviacion(40L))
    }

    @Test
    fun `el porcentaje lleva signo y coma de menos`() {
        assertEquals("+7 %", formatearPorcentaje(6.9))
        assertEquals("−20 %", formatearPorcentaje(-20.0))
        assertEquals("0 %", formatearPorcentaje(0.4))
        assertEquals("0 %", formatearPorcentaje(-0.4))
    }

    @Test
    fun `km y velocidad con coma decimal y sin unidad`() {
        assertEquals("31,9", formatearKm(31_900.0))
        assertEquals("0,9", formatearKm(900.0))
        assertEquals("55", formatearKmhEntero(54.7))
        assertEquals("0", formatearKmhEntero(-3.0))
    }
}
