package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

class LectorPkTest {
    private fun leer(vararg lineas: String) = LectorPk.leer((sequenceOf(LectorPk.CABECERA) + lineas).asSequence())

    @Test
    fun `lee los hitos con carretera, km y posicion`() {
        val r = leer("#ref\tkm\tlat\tlon", "Ma-10\t23\t39.7\t2.7", "Ma-1C\t44.8\t39.512346\t2.9", "")

        assertEquals(0, r.descartadas)
        assertEquals(
            listOf(HitoKm("Ma-10", 23.0, PuntoGeo(39.7, 2.7)), HitoKm("Ma-1C", 44.8, PuntoGeo(39.512346, 2.9))),
            r.hitos,
        )
    }

    @Test
    fun `descarta y cuenta las lineas malformadas`() {
        val r =
            leer(
                "Ma-10\t23\t39.7",
                "Ma-10\tveinte\t39.7\t2.7",
                "\t5\t39.7\t2.7",
                "Ma-10\t-1\t39.7\t2.7",
                "Ma-10\t5\t95.0\t2.7",
                "Ma-10\tNaN\t39.7\t2.7",
                "Ma-10\t5\t39.7\t2.7",
            )

        assertEquals(1, r.hitos.size)
        assertEquals(6, r.descartadas)
    }

    @Test
    fun `sin la cabecera pk v1 falla`() {
        assertFailsWith<IllegalArgumentException> { LectorPk.leer(sequenceOf("#radares v1")) }
        assertFailsWith<IllegalArgumentException> { LectorPk.leer(emptySequence()) }
    }
}
