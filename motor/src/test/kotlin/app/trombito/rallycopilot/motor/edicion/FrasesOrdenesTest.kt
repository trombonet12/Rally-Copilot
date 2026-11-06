package app.trombito.rallycopilot.motor.edicion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrasesOrdenesTest {
    @Test
    fun `cada frase de la lista se interpreta como su orden`() {
        val fallos =
            FrasesOrdenes.porOrden.flatMap { (orden, frases) ->
                frases.filter { InterpreteOrdenes.interpretar(it) != orden }.map { "$it -> $orden" }
            }

        assertEquals(emptyList<String>(), fallos)
    }

    @Test
    fun `una frase no sirve a dos ordenes distintas`() {
        val repetidas =
            FrasesOrdenes.porOrden
                .flatMap { (orden, frases) -> frases.map { it to orden } }
                .groupBy({ it.first }, { it.second })
                .filterValues { it.distinct().size > 1 }

        assertEquals(emptyMap<String, List<Orden>>(), repetidas)
    }

    @Test
    fun `las frases van en minusculas y son un conjunto pequeno`() {
        val frases = FrasesOrdenes.frases()

        assertEquals(frases.size, frases.toSet().size)
        assertTrue(frases.all { it == it.lowercase() })
        assertTrue(frases.size < 200)
    }
}
