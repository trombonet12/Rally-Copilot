package app.trombito.rallycopilot.motor.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs

class SolTest {
    private val palma = PuntoGeo(39.57, 2.65)
    private val zona = Sol.MADRID

    private fun hora(instante: Instant?): LocalTime = instante!!.atZone(zona).toLocalTime()

    private fun assertHora(
        horas: Int,
        minutos: Int,
        real: LocalTime,
        toleranciaMin: Int = 5,
    ) {
        val d = abs(real.hour * 60 + real.minute - (horas * 60 + minutos))
        assertTrue("Esperaba ~$horas:$minutos y salió $real", d <= toleranciaMin)
    }

    private fun local(
        fecha: String,
        hora: String,
    ): Instant = LocalDateTime.parse("${fecha}T$hora").atZone(zona).toInstant()

    @Test
    fun `solsticio de verano en Palma sale hacia las 06_21 y se pone hacia las 21_22 hora local`() {
        val e = Sol.eventos(palma, LocalDate.of(2026, 6, 21))
        assertHora(6, 21, hora(e.salida))
        assertHora(21, 22, hora(e.puesta))
    }

    @Test
    fun `solsticio de invierno en Palma sale hacia las 08_11 y se pone hacia las 17_30 hora local`() {
        val e = Sol.eventos(palma, LocalDate.of(2026, 12, 21))
        assertHora(8, 11, hora(e.salida))
        assertHora(17, 30, hora(e.puesta))
    }

    @Test
    fun `el cambio de hora desplaza la hora local una hora y no el sol`() {
        val antes = Sol.eventos(palma, LocalDate.of(2026, 3, 28))
        val despues = Sol.eventos(palma, LocalDate.of(2026, 3, 29))
        // El 29-mar-2026 empieza el horario de verano: la salida local salta ~1 h (menos el adelanto natural).
        val salto = hora(despues.salida).toSecondOfDay() - hora(antes.salida).toSecondOfDay()
        assertTrue("Salto $salto s", salto in 3_300..3_700)
    }

    @Test
    fun `dia largo en verano y corto en invierno`() {
        val verano = Sol.eventos(palma, LocalDate.of(2026, 6, 21))
        val invierno = Sol.eventos(palma, LocalDate.of(2026, 12, 21))
        val horasVerano = (verano.puesta!!.epochSecond - verano.salida!!.epochSecond) / 3600.0
        val horasInvierno = (invierno.puesta!!.epochSecond - invierno.salida!!.epochSecond) / 3600.0
        assertEquals(15.0, horasVerano, 0.2)
        assertEquals(9.3, horasInvierno, 0.2)
    }

    @Test
    fun `sin sol calculable en el polo en invierno`() {
        val e = Sol.eventos(PuntoGeo(89.0, 0.0), LocalDate.of(2026, 12, 21))
        assertNull(e.salida)
        assertNull(e.puesta)
    }

    @Test
    fun `de dia es claro y el proximo cambio es la puesta mas el crepusculo`() {
        val fase = Sol.fase(local("2026-06-21", "13:00"), palma)
        assertFalse(fase.oscuro)
        assertHora(21, 37, hora(fase.proximoCambio))
    }

    @Test
    fun `de noche es oscuro y el proximo cambio es la salida menos el crepusculo`() {
        val fase = Sol.fase(local("2026-06-21", "23:30"), palma)
        assertTrue(fase.oscuro)
        assertEquals(LocalDate.of(2026, 6, 22), fase.proximoCambio.atZone(zona).toLocalDate())
        assertHora(6, 6, hora(fase.proximoCambio))
    }

    @Test
    fun `de madrugada es oscuro hasta la salida menos el crepusculo`() {
        val fase = Sol.fase(local("2026-12-21", "03:00"), palma)
        assertTrue(fase.oscuro)
        assertEquals(LocalDate.of(2026, 12, 21), fase.proximoCambio.atZone(zona).toLocalDate())
        assertHora(7, 56, hora(fase.proximoCambio))
    }

    @Test
    fun `el crepusculo a ambos lados del limite`() {
        val e = Sol.eventos(palma, LocalDate.of(2026, 12, 21))
        val limite = e.puesta!!.plus(Sol.CREPUSCULO)
        assertFalse(Sol.fase(limite.minusSeconds(60), palma).oscuro)
        assertTrue(Sol.fase(limite.plusSeconds(60), palma).oscuro)
        val alba = e.salida!!.minus(Sol.CREPUSCULO)
        assertTrue(Sol.fase(alba.minusSeconds(60), palma).oscuro)
        assertFalse(Sol.fase(alba.plusSeconds(60), palma).oscuro)
    }

    @Test
    fun `sin ubicacion usa Palma`() {
        val ahora = local("2026-12-21", "12:00")
        assertEquals(Sol.fase(ahora, palma), Sol.fase(ahora, null))
    }

    @Test
    fun `el proximo cambio siempre es futuro durante todo el año`() {
        var t = local("2026-01-01", "00:00")
        repeat(400) {
            val f = Sol.fase(t, palma)
            assertTrue(f.proximoCambio > t)
            t = t.plusSeconds(86_400L - 600)
        }
    }
}
