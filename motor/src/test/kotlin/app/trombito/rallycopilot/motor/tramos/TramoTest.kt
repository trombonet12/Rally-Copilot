package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.notas.TrazasDePrueba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TramoTest {
    private val traza = TrazasDePrueba.recta(4000.0)

    private fun tramo(
        longitudM: Double = traza.longitudMetros(),
        sectores: List<Double>? = null,
    ) = if (sectores == null) Tramo("t", traza, longitudM) else Tramo("t", traza, longitudM, sectores = sectores)

    @Test
    fun `por defecto cuatro sectores a cuartos y un microsector por kilometro`() {
        val t = tramo(4000.0)
        assertEquals(listOf(1000.0, 2000.0, 3000.0), t.sectores)
        assertEquals(listOf(1000.0, 2000.0, 3000.0), t.microsectores)
    }

    @Test
    fun `los microsectores por defecto no incluyen la meta ni cortes mas alla`() {
        assertEquals(listOf(1000.0, 2000.0), Tramo.microsectoresPorDefecto(2500.0))
        assertEquals(emptyList<Double>(), Tramo.microsectoresPorDefecto(1000.0))
        assertEquals(listOf(500.0, 1000.0, 1500.0), Tramo.microsectoresPorDefecto(2000.0, 500.0))
        // Con la meta 10 m antes del final, una traza de 2 km + 5 m no lleva corte a 2 km (caería tras la meta).
        assertEquals(listOf(1000.0), Tramo.microsectoresPorDefecto(2005.0))
        val traza = listOf(PuntoGeo(39.6, 2.9), PuntoGeo(39.6, 2.92))
        assertEquals(listOf(1000.0), Tramo("t", traza, 2005.0).microsectores)
    }

    @Test
    fun `numero y posicion de los sectores son configurables`() {
        val t = tramo(4000.0, sectores = listOf(300.0, 3900.0))
        assertEquals(2, t.sectores.size)
        assertEquals(listOf(1000.0, 2000.0), Tramo.sectoresPorDefecto(3000.0, 3))
    }

    @Test
    fun `cortes invalidos se rechazan`() {
        assertThrows(IllegalArgumentException::class.java) { tramo(4000.0, sectores = listOf(2000.0, 1000.0)) }
        assertThrows(IllegalArgumentException::class.java) { tramo(4000.0, sectores = listOf(0.0)) }
        assertThrows(IllegalArgumentException::class.java) { tramo(4000.0, sectores = listOf(4000.0)) }
        assertThrows(IllegalArgumentException::class.java) { Tramo("t", traza.take(1), 10.0) }
        assertThrows(IllegalArgumentException::class.java) { Tramo("t", traza, 0.0) }
    }

    @Test
    fun `la pasada interpola y no extrapola`() {
        val p = Pasada("t", listOf(MarcaTiempo(0.0, 0), MarcaTiempo(100.0, 10_000), MarcaTiempo(300.0, 20_000)), 20_000)
        assertEquals(5_000.0, p.tiempoEn(50.0)!!, 1e-9)
        assertEquals(15_000.0, p.tiempoEn(200.0)!!, 1e-9)
        assertEquals(0.0, p.tiempoEn(0.0)!!, 1e-9)
        assertEquals(20_000.0, p.tiempoEn(300.0)!!, 1e-9)
        assertEquals(null, p.tiempoEn(300.1))
    }

    @Test
    fun `una pasada con marcas desordenadas se rechaza`() {
        assertThrows(IllegalArgumentException::class.java) {
            Pasada("t", listOf(MarcaTiempo(100.0, 10), MarcaTiempo(50.0, 20)), 20)
        }
    }
}
