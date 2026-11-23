package app.trombito.rallycopilot.ui.miscarreteras

import app.trombito.rallycopilot.domain.tramos.DatosVia
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.SentidoVia
import app.trombito.rallycopilot.ui.comun.resumenFalso
import app.trombito.rallycopilot.ui.mapa.TramoCalor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ReglaYFormatoCarreterasTest {
    private fun tramo(
        id: String,
        desde: Double?,
        hasta: Double?,
        sentido: SentidoVia = SentidoVia.CRECIENTE,
    ) = resumenFalso(id, via = DatosVia("MA10", desde, hasta, sentido, null, null))

    @Test
    fun `sin tramos con kilometros no hay regla`() {
        assertNull(reglaDe(emptyList()))
        assertNull(reglaDe(listOf(resumenFalso("viaje"), tramo("a", null, null), tramo("b", 3.0, null))))
    }

    @Test
    fun `la regla va del menor al mayor kilometro y las franjas siempre de menor a mayor`() {
        val tramos = listOf(tramo("a", 20.0, 32.4), tramo("b", 50.0, 41.0, SentidoVia.DECRECIENTE))
        val regla = checkNotNull(reglaDe(tramos))
        assertEquals(20.0, regla.minKm, 0.0)
        assertEquals(50.0, regla.maxKm, 0.0)
        assertEquals(30.0, regla.largoKm, 0.0)
        val b = regla.franjas.first { it.tramoId == "b" }
        assertEquals(41.0, b.desdeKm, 0.0)
        assertEquals(50.0, b.hastaKm, 0.0)
        assertEquals(SentidoVia.DECRECIENTE, b.sentido)
    }

    @Test
    fun `un tramo sin longitud no deja la regla en cero`() {
        val regla = checkNotNull(reglaDe(listOf(tramo("a", 10.0, 10.0))))
        assertTrue(regla.largoKm > 0.0)
    }

    @Test
    fun `las curvas por km se escriben con coma y sin decimal sobrante`() {
        assertEquals("7", formatoCurvasPorKm(7.0))
        assertEquals("7", formatoCurvasPorKm(6.96))
        assertEquals("6,5", formatoCurvasPorKm(6.5))
        assertEquals("12,3", formatoCurvasPorKm(12.34))
    }

    @Test
    fun `los kilometros llevan coma decimal y las marcas de la regla son enteras`() {
        assertEquals("20,1", formatoKm(20.1))
        assertEquals("74", formatoMarcaKm(73.5))
        assertEquals(10.5, largoKm(20.0, 9.5), 0.0)
    }

    @Test
    fun `la fecha es corta, con anio y en espanol`() {
        val fecha = formatoFecha(1_700_000_000_000L, ZoneId.of("Europe/Madrid"))
        assertTrue(fecha, fecha.startsWith("14 nov") && fecha.endsWith("2023"))
    }

    @Test
    fun `las esquinas encierran todas las vias y sin puntos no hay`() {
        assertTrue(esquinasDe(emptyList()).isEmpty())
        val tramos =
            listOf(
                TramoCalor(1, null, "Ma-10", listOf(PuntoGeo(39.6, 2.9), PuntoGeo(39.7, 2.95)), 1, 1.0, null),
                TramoCalor(2, null, "Ma-11", listOf(PuntoGeo(39.55, 3.0), PuntoGeo(39.65, 2.8)), 1, 1.0, null),
            )
        assertEquals(listOf(PuntoGeo(39.55, 2.8), PuntoGeo(39.7, 3.0)), esquinasDe(tramos))
    }

    @Test
    fun `el trazo sin base lleva uso y velocidad del tramo de calor`() {
        val calor = TramoCalor(1, null, "Ma-10", listOf(PuntoGeo(39.6, 2.9)), 4, 1.0, 62.0)
        val trazo = calor.aTrazo()
        assertEquals(4, trazo.pasadas)
        assertEquals(62.0, trazo.velMediaKmh!!, 0.0)
    }
}
