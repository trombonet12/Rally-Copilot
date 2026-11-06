package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.busqueda.Buscador
import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LugaresPronunciablesTest {
    private class IndiceContado(
        var nombres: List<String>,
    ) : Buscador {
        var consultas = 0

        override suspend fun buscar(
            texto: String,
            cerca: PuntoGeo?,
            limite: Int,
        ): List<ResultadoBusqueda> = emptyList()

        override suspend fun nombresDeLugares(limite: Int): List<String> {
            consultas++
            return nombres.take(limite)
        }
    }

    private val modelo = setOf("sóller", "cala", "d", "or", "palma", "sa", "pobla")

    @Test
    fun `normaliza el nombre y descarta los que el modelo no conoce`() {
        val lugares = LugaresPronunciables(IndiceContado(emptyList()), conoce = modelo::contains)

        assertEquals("sóller", lugares.pronunciable("Sóller"))
        assertEquals("cala d or", lugares.pronunciable("Cala d'Or"))
        assertEquals("sa pobla", lugares.pronunciable("  Sa-Pobla "))
        assertEquals(null, lugares.pronunciable("Pollença"))
        assertEquals(null, lugares.pronunciable("Camí 7"))
        assertEquals(null, lugares.pronunciable("..."))
    }

    @Test
    fun `da los nombres sin repetir y los guarda cuando hay indice`() =
        runBlocking {
            val indice = IndiceContado(listOf("Sóller", "Pollença", "Palma", "SÓLLER"))
            val lugares = LugaresPronunciables(indice, conoce = modelo::contains)

            assertEquals(listOf("sóller", "palma"), lugares.nombres())
            assertEquals(listOf("sóller", "palma"), lugares.nombres())
            assertEquals(1, indice.consultas)
        }

    @Test
    fun `sin indice no guarda la lista vacia y lo vuelve a intentar`() =
        runBlocking {
            val indice = IndiceContado(emptyList())
            val lugares = LugaresPronunciables(indice, conoce = modelo::contains)

            assertEquals(emptyList<String>(), lugares.nombres())
            indice.nombres = listOf("Palma")
            assertEquals(listOf("palma"), lugares.nombres())
            assertEquals(2, indice.consultas)
        }
}
