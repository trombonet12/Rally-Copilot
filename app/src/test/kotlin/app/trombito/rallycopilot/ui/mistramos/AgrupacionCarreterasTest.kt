package app.trombito.rallycopilot.ui.mistramos

import app.trombito.rallycopilot.domain.tramos.DatosVia
import app.trombito.rallycopilot.motor.vias.SentidoVia
import app.trombito.rallycopilot.ui.comun.resumenFalso
import org.junit.Assert.assertEquals
import org.junit.Test

class AgrupacionCarreterasTest {
    private fun tramo(
        id: String,
        ref: String,
        desde: Double?,
        sentido: SentidoVia = SentidoVia.CRECIENTE,
        destino: String? = null,
    ) = resumenFalso(id, via = DatosVia(ref, desde, desde?.plus(5.0), sentido, destino, null))

    private fun refs(grupos: List<GrupoCarretera>) = grupos.map { it.clave.substringBefore('|') }

    @Test
    fun `el orden de las carreteras es natural y no alfabetico`() {
        val grupos =
            agruparCarreteras(
                listOf(
                    tramo("a", "MA13A", 1.0),
                    tramo("b", "MA10", 1.0),
                    tramo("c", "MA2", 1.0),
                    tramo("d", "MA13", 1.0),
                ),
            )
        assertEquals(listOf("MA2", "MA10", "MA13", "MA13A"), refs(grupos))
    }

    @Test
    fun `una carretera con nombre sin ref va despues de las refs`() {
        val grupos = agruparCarreteras(listOf(tramo("a", "~cami vell", 1.0), tramo("b", "MA2", 1.0)))
        assertEquals(listOf("MA2", "~cami vell"), refs(grupos))
        assertEquals("Cami Vell", grupos.last().carretera)
    }

    @Test
    fun `dentro de un grupo van por kilometro y los que no tienen, al final`() {
        val tramos = listOf(tramo("a", "MA15", 20.0), tramo("b", "MA15", null), tramo("c", "MA15", 3.0))
        val grupo = agruparCarreteras(tramos).single()
        assertEquals(listOf("c", "a", "b"), grupo.tramos.map { it.id })
    }

    @Test
    fun `el destino del grupo es el mas repetido y la carretera sale del nombre automatico`() {
        val tramos =
            listOf(
                tramo("a", "MA15", 1.0, destino = "Palma").copy(carretera = "Ma-15"),
                tramo("b", "MA15", 8.0, destino = "Manacor"),
                tramo("c", "MA15", 15.0, destino = "Manacor"),
            )
        val grupo = agruparCarreteras(tramos).single()
        assertEquals("Manacor", grupo.destino)
        assertEquals("Ma-15", grupo.carretera)
    }

    @Test
    fun `el sentido contrario va en otro grupo`() {
        val grupos = agruparCarreteras(listOf(tramo("a", "MA15", 1.0, SentidoVia.DECRECIENTE), tramo("b", "MA15", 1.0)))
        assertEquals(listOf(SentidoVia.CRECIENTE, SentidoVia.DECRECIENTE), grupos.map { it.sentido })
    }
}
