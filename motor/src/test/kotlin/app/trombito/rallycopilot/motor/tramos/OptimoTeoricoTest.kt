package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.notas.TrazasDePrueba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OptimoTeoricoTest {
    private val traza = TrazasDePrueba.recta(4_000.0)
    private val tramo = Tramo("recta", traza, traza.longitudMetros(), sectores = listOf(1_000.0, 2_000.0, 3_000.0))

    /** Pasada con cortes en 1000, 2000, 3000 y la meta, a partir de los tiempos [parcialesMs] de cada sector. */
    private fun pasada(
        vararg parcialesMs: Long,
        tramoId: String = tramo.id,
    ): Pasada {
        val distancias = listOf(0.0, 1_000.0, 2_000.0, 3_000.0, tramo.metaM)
        val acumulados = parcialesMs.toList().runningFold(0L) { a, b -> a + b }
        return Pasada(tramoId, distancias.zip(acumulados) { d, t -> MarcaTiempo(d, t) }, acumulados.last())
    }

    @Test
    fun `tres pasadas que ganan sectores distintos`() {
        val a = pasada(50_000, 60_000, 55_000, 50_000) // 215 s
        val b = pasada(52_000, 57_000, 56_000, 51_000) // 216 s
        val c = pasada(53_000, 61_000, 52_000, 52_000) // 218 s
        val optimo = OptimoTeorico.de(tramo, listOf(a, b, c))!!
        assertEquals(listOf(50_000L, 57_000L, 52_000L, 50_000L), optimo.sectores.map { it.tiempoMs })
        assertEquals(listOf(0, 1, 2, 0), optimo.sectores.map { it.pasadaIndice })
        assertEquals(listOf(1, 2, 3, 4), optimo.sectores.map { it.numero })
        assertEquals(listOf(0.0, 1_000.0, 2_000.0, 3_000.0), optimo.sectores.map { it.desdeM })
        assertEquals(tramo.metaM, optimo.sectores.last().hastaM, 0.0)
        assertEquals(209_000L, optimo.totalMs)
        assertEquals(0, optimo.mejorPasadaIndice)
        assertEquals(215_000L, optimo.mejorPasadaMs)
        assertEquals(6_000L, optimo.gananciaMs)
    }

    @Test
    fun `con una sola pasada el optimo es esa pasada`() {
        val unica = pasada(50_000, 60_000, 55_000, 50_000)
        val optimo = OptimoTeorico.de(tramo, listOf(unica))!!
        assertEquals(215_000L, optimo.totalMs)
        assertEquals(setOf(0), optimo.sectores.map { it.pasadaIndice }.toSet())
        assertEquals(0, optimo.mejorPasadaIndice)
        assertEquals(0L, optimo.gananciaMs)
    }

    @Test
    fun `sin pasadas no hay optimo`() {
        assertNull(OptimoTeorico.de(tramo, emptyList()))
    }

    @Test
    fun `los empates los gana la primera pasada y se ignoran las de otro tramo`() {
        val a = pasada(50_000, 60_000, 55_000, 50_000)
        val ajena = pasada(1_000, 1_000, 1_000, 1_000, tramoId = "otro")
        val optimo = OptimoTeorico.de(tramo, listOf(ajena, a, a))!!
        assertEquals(215_000L, optimo.totalMs)
        assertEquals(setOf(1), optimo.sectores.map { it.pasadaIndice }.toSet())
        assertNull(OptimoTeorico.de(tramo, listOf(ajena)))
    }

    @Test
    fun `una pasada parcial aporta solo los sectores que cubre`() {
        val completa = pasada(50_000, 60_000, 55_000, 50_000)
        val soloSector2 = Pasada("recta", listOf(MarcaTiempo(1_000.0, 0), MarcaTiempo(2_000.0, 40_000)), 40_000)
        val optimo = OptimoTeorico.de(tramo, listOf(completa, soloSector2))!!
        assertEquals(40_000L, optimo.sectores[1].tiempoMs)
        assertEquals(1, optimo.sectores[1].pasadaIndice)
        assertEquals(195_000L, optimo.totalMs)
        assertEquals(0, optimo.mejorPasadaIndice)
        assertEquals(20_000L, optimo.gananciaMs)
    }

    @Test
    fun `si un sector no lo cubre ninguna pasada no hay optimo`() {
        val corta = Pasada("recta", listOf(MarcaTiempo(0.0, 0), MarcaTiempo(2_000.0, 100_000)), 100_000)
        assertNull(OptimoTeorico.de(tramo, listOf(corta)))
    }

    @Test
    fun `sin pasada completa hay optimo pero no mejor pasada ni ganancia`() {
        val primera = Pasada("recta", listOf(MarcaTiempo(0.0, 0), MarcaTiempo(2_000.0, 100_000)), 100_000)
        val segunda =
            Pasada(
                "recta",
                listOf(MarcaTiempo(2_000.0, 0), MarcaTiempo(3_000.0, 30_000), MarcaTiempo(tramo.metaM, 80_000)),
                80_000,
            )
        val optimo = OptimoTeorico.de(tramo, listOf(primera, segunda))!!
        assertEquals(180_000L, optimo.totalMs)
        assertNull(optimo.mejorPasadaIndice)
        assertNull(optimo.gananciaMs)
    }
}
