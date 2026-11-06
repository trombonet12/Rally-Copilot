package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.busqueda.Buscador
import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.motor.edicion.InterpreteOrdenes
import app.trombito.rallycopilot.motor.edicion.Orden
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lo que la gramática cerrada puede decir tiene que existir en el modelo de Vosk, y los destinos caben en ella. */
class VocabularioGramaticaTest {
    private fun conocidas(nombres: List<String>) =
        LugaresPronunciables(FalsoIndice(nombres)).let { runBlocking { it.nombres() } }

    private class FalsoIndice(
        private val nombres: List<String>,
    ) : Buscador {
        override suspend fun buscar(
            texto: String,
            cerca: PuntoGeo?,
            limite: Int,
        ): List<ResultadoBusqueda> = emptyList()

        override suspend fun nombresDeLugares(limite: Int): List<String> = nombres.take(limite)
    }

    @Test
    fun `el vocabulario del modelo se carga entero y trae las palabras del copiloto`() {
        assertTrue(VocabularioVosk.palabras.size > 90_000)
        assertTrue(
            listOf("copiloto", "llévame", "sóller", "cronómetro", "zoom", "olvídalo").all(VocabularioVosk::conoce),
        )
        assertFalse(VocabularioVosk.conoce("pollença"))
        assertFalse(VocabularioVosk.conoce("glorietas"))
    }

    @Test
    fun `el vocabulario es el del modelo que lleva la app`() {
        assertTrue(CopiaModeloVosk.VERSION, CopiaModeloVosk.VERSION.startsWith("vosk-model-small-es-0.42"))
    }

    @Test
    fun `todas las palabras de las ordenes y las notas existen en el vocabulario del modelo`() {
        val fuera = GramaticaOrdenes.palabras().filterNot(VocabularioVosk::conoce).sorted()

        assertEquals(emptyList<String>(), fuera)
    }

    @Test
    fun `los nombres de lugares solo valen si el modelo conoce todas sus palabras`() {
        val nombres = conocidas(listOf("Sóller", "Palma", "Pollença", "Cala d'Or", "Llucmajor", "Inca", "Camí 7", ""))

        assertTrue("sóller" in nombres)
        assertTrue("cala d or" in nombres)
        assertFalse(nombres.any { "pollen" in it || "llucmajor" in it || "7" in it })
        assertEquals(nombres.distinct(), nombres)
    }

    @Test
    fun `con cientos de destinos la gramatica no pasa de su tope y el interprete los entiende`() {
        val nombres = (1..900).map { "monte$it" }
        val frases = GramaticaOrdenes.frasesOrdenes(nombres)

        assertTrue("frases: ${frases.size}", frases.size <= GramaticaOrdenes.MAX_FRASES)
        assertTrue(GramaticaOrdenes.MAX_FRASES < 4_000)
        assertEquals(frases.size, frases.toSet().size)
        assertTrue("llévame a monte1" in frases)
        assertTrue("vamos a monte1" in frases)
        assertTrue("añade parada en monte1" in frases)
        // Los primeros (más importantes) son los que se quedan.
        assertFalse("llévame a monte900" in frases)
    }

    @Test
    fun `una gramatica con destinos se entiende entera y sus palabras existen en el modelo`() {
        val destinos = conocidas(listOf("Sóller", "Palma", "Inca", "Manacor", "Cala d'Or", "Alcúdia", "Sa Pobla"))
        val frases = GramaticaOrdenes.frasesOrdenes(destinos)

        val sinEntender = frases.filter { InterpreteOrdenes.interpretar(it) == null }
        assertEquals(emptyList<String>(), sinEntender)
        assertEquals(emptyList<String>(), GramaticaOrdenes.palabras(destinos).filterNot(VocabularioVosk::conoce))
        assertEquals(Orden.IrA("soller"), InterpreteOrdenes.interpretar("llévame a sóller"))
        assertEquals(Orden.ParadaEn("sa pobla"), InterpreteOrdenes.interpretar("añade parada en sa pobla"))
    }

    @Test
    fun `la gramatica en json lleva los destinos y acaba en unk`() {
        val json = GramaticaOrdenes.jsonOrdenes(listOf("sóller"))

        assertTrue("\"llévame a sóller\"" in json)
        assertTrue(json.endsWith("\"[unk]\"]"))
        assertFalse("\"llévame a sóller\"" in GramaticaOrdenes.jsonOrdenes())
    }
}
