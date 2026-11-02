package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.motor.edicion.InterpreteOrdenes
import app.trombito.rallycopilot.motor.edicion.Orden
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.FrasesNota
import app.trombito.rallycopilot.motor.notas.Modificador
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GramaticaOrdenesTest {
    private fun frasesDe(json: String): List<String> =
        Json.parseToJsonElement(json).jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun `la gramatica de activacion es solo copiloto y unk`() {
        assertEquals(listOf("copiloto", "[unk]"), frasesDe(GramaticaOrdenes.jsonActivacion()))
    }

    @Test
    fun `la gramatica de ordenes acaba en unk y lleva las ordenes sueltas`() {
        val frases = frasesDe(GramaticaOrdenes.jsonOrdenes())

        assertEquals("[unk]", frases.last())
        val ordenes = listOf("repite", "más pronto", "más tarde", "silencio", "habla", "marca aquí", "quita", "cuidado")
        assertTrue(frases.containsAll(ordenes))
        assertEquals(frases.size, frases.toSet().size)
    }

    @Test
    fun `lleva las notas dictadas con direccion grado largura tendencia y distancia`() {
        val frases = frasesDe(GramaticaOrdenes.jsonOrdenes())

        assertTrue("derecha tres" in frases)
        assertTrue("izquierda uno cierra" in frases)
        assertTrue("derecha cuatro muy larga abre cincuenta" in frases)
        assertTrue("izquierda media" in frases)
        assertTrue("derecha muy cerrada ciento veinte" in frases)
        assertTrue("cuidado derecha tres larga" in frases)
        assertTrue("horquilla izquierda" in frases)
        assertTrue("horquilla derecha doscientos cincuenta" in frases)
    }

    @Test
    fun `se mantiene en un tamano razonable para compilarla en el movil`() {
        assertTrue(GramaticaOrdenes.frasesOrdenes().size in 1000..4000)
    }

    @Test
    fun `el interprete del motor entiende todas las frases de la gramatica`() {
        val sinEntender = GramaticaOrdenes.frasesOrdenes().filter { InterpreteOrdenes.interpretar(it) == null }

        assertEquals(emptyList<String>(), sinEntender)
    }

    @Test
    fun `cada distancia de la gramatica la entiende el interprete tal cual`() {
        for (distancia in GramaticaOrdenes.DISTANCIAS_M) {
            val orden = InterpreteOrdenes.interpretar("izquierda tres ${FrasesNota.enPalabras(distancia)}")

            assertEquals(distancia, (orden as Orden.Nota).distanciaSiguienteM)
        }
    }

    @Test
    fun `una frase de la gramatica da la orden esperada`() {
        val orden = InterpreteOrdenes.interpretar("derecha tres larga cincuenta")

        assertEquals(
            Orden.Nota(Direccion.DERECHA, 3, setOf(Modificador.LARGA), distanciaSiguienteM = 50),
            orden,
        )
        assertEquals(Orden.MasPronto, InterpreteOrdenes.interpretar("más pronto"))
        assertEquals(Orden.MarcaAqui, InterpreteOrdenes.interpretar("marca aquí"))
    }

    @Test
    fun `las palabras de la gramatica van en minusculas y sin signos`() {
        val raras = GramaticaOrdenes.palabras().filter { palabra -> palabra.any { !it.isLetter() || it.isUpperCase() } }

        assertEquals(emptyList<String>(), raras)
    }
}
