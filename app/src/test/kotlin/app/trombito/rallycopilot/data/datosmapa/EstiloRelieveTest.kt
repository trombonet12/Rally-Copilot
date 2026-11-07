package app.trombito.rallycopilot.data.datosmapa

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EstiloRelieveTest {
    private val estilo =
        """{"version":8,"sources":{"protomaps":{"type":"vector","url":"pmtiles://{{DIR}}/mallorca.pmtiles"},""" +
            """"relieve":{"type":"raster-dem","url":"pmtiles://{{DIR}}/relieve-mallorca.pmtiles"},""" +
            """"curvas":{"type":"vector","url":"pmtiles://{{DIR}}/curvas-mallorca.pmtiles"}},""" +
            """"layers":[{"id":"fondo","type":"background"},""" +
            """{"id":"relieve-sombra","type":"hillshade","source":"relieve"},""" +
            """{"id":"calles","type":"line","source":"protomaps"},""" +
            """{"id":"curvas-linea","type":"line","source":"curvas"},""" +
            """{"id":"curvas-etiqueta","type":"symbol","source":"curvas"}]}"""

    private fun capas(json: String) =
        Json
            .parseToJsonElement(json)
            .jsonObject
            .getValue("layers")
            .jsonArray
            .map {
                it.jsonObject
                    .getValue("id")
                    .jsonPrimitive.content
            }

    private fun fuentes(json: String): Set<String> =
        (Json.parseToJsonElement(json).jsonObject.getValue("sources") as JsonObject).keys

    @Test
    fun `con los dos archivos el estilo queda igual`() {
        assertSame(estilo, EstiloRelieve.sinAusentes(estilo) { true })
    }

    @Test
    fun `sin relieve ni curvas se quitan sus fuentes y capas`() {
        val resultado = EstiloRelieve.sinAusentes(estilo) { false }

        assertEquals(setOf("protomaps"), fuentes(resultado))
        assertEquals(listOf("fondo", "calles"), capas(resultado))
    }

    @Test
    fun `solo se quita lo que falta`() {
        val resultado = EstiloRelieve.sinAusentes(estilo) { it == "curvas-mallorca.pmtiles" }

        assertEquals(setOf("protomaps", "curvas"), fuentes(resultado))
        assertEquals(listOf("fondo", "calles", "curvas-linea", "curvas-etiqueta"), capas(resultado))
    }

    @Test
    fun `un estilo ilegible se devuelve tal cual`() {
        assertSame("no es json", EstiloRelieve.sinAusentes("no es json") { false })
    }
}
