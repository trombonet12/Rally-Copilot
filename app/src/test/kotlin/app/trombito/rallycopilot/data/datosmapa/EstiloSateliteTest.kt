package app.trombito.rallycopilot.data.datosmapa

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EstiloSateliteTest {
    private val estilo =
        """{"version":8,"name":"sat","sources":{""" +
            """"pnoa":{"type":"raster","url":"pmtiles://{{DIR}}/pnoa-mallorca-z16.pmtiles",""" +
            """"tileSize":512,"attribution":"PNOA"},""" +
            """"otra":{"type":"vector","url":"pmtiles://{{DIR}}/mallorca.pmtiles"}},""" +
            """"layers":[{"id":"fondo","type":"background"},{"id":"pnoa","type":"raster","source":"pnoa"},""" +
            """{"id":"calles","type":"line","source":"otra"}]}"""

    private fun ids(json: JsonObject) =
        json.getValue("layers").jsonArray.map {
            it.jsonObject
                .getValue("id")
                .jsonPrimitive.content
        }

    @Test
    fun `anade la fuente z17 y su capa justo encima de la del z16`() {
        val resultado = Json.parseToJsonElement(EstiloSatelite.conZ17(estilo)).jsonObject

        assertEquals(listOf("fondo", "pnoa", "pnoa-z17", "calles"), ids(resultado))
        val fuente =
            resultado
                .getValue("sources")
                .jsonObject
                .getValue("pnoa-z17")
                .jsonObject
        assertEquals("raster", fuente.getValue("type").jsonPrimitive.content)
        assertEquals("pmtiles://{{DIR}}/$NOMBRE_TESELAS_Z17", fuente.getValue("url").jsonPrimitive.content)
        assertEquals(17, fuente.getValue("minzoom").jsonPrimitive.int)
        assertEquals(17, fuente.getValue("maxzoom").jsonPrimitive.int)
        assertEquals(512, fuente.getValue("tileSize").jsonPrimitive.int)
        assertEquals("PNOA", fuente.getValue("attribution").jsonPrimitive.content)
        assertEquals(
            17,
            resultado
                .getValue("layers")
                .jsonArray[2]
                .jsonObject
                .getValue("minzoom")
                .jsonPrimitive.int,
        )
        assertEquals("sat", resultado.getValue("name").jsonPrimitive.content)
        assertTrue(resultado.getValue("sources").jsonObject.containsKey("otra"))
    }

    @Test
    fun `es idempotente`() {
        val una = EstiloSatelite.conZ17(estilo)

        assertEquals(una, EstiloSatelite.conZ17(una))
    }

    @Test
    fun `un estilo sin la fuente pnoa o ilegible se devuelve igual`() {
        val sinPnoa = """{"version":8,"sources":{"otra":{"type":"vector"}},"layers":[]}"""

        assertEquals(sinPnoa, EstiloSatelite.conZ17(sinPnoa))
        assertEquals("no es json", EstiloSatelite.conZ17("no es json"))
        assertFalse(EstiloSatelite.conZ17(estilo).isEmpty())
    }

    @Test
    fun `conDetalle anade la fuente mbtiles absoluta z18 a z19 y su capa encima de la del z17`() {
        val conZ17 = EstiloSatelite.conZ17(estilo)

        val resultado =
            Json
                .parseToJsonElement(
                    EstiloSatelite.conDetalle(conZ17, "/data/files/mapa/satelite-detalle.mbtiles"),
                ).jsonObject

        assertEquals(listOf("fondo", "pnoa", "pnoa-z17", "pnoa-detalle", "calles"), ids(resultado))
        val fuente =
            resultado
                .getValue("sources")
                .jsonObject
                .getValue("pnoa-detalle")
                .jsonObject
        assertEquals("raster", fuente.getValue("type").jsonPrimitive.content)
        assertEquals(
            "mbtiles:///data/files/mapa/satelite-detalle.mbtiles",
            fuente.getValue("url").jsonPrimitive.content,
        )
        assertEquals(18, fuente.getValue("minzoom").jsonPrimitive.int)
        assertEquals(19, fuente.getValue("maxzoom").jsonPrimitive.int)
        assertEquals(512, fuente.getValue("tileSize").jsonPrimitive.int)
    }

    @Test
    fun `conDetalle sin z17 va encima de la del z16 y es valido para el validador`() {
        val resultado = EstiloSatelite.conDetalle(estilo, "/data/files/mapa/satelite-detalle.mbtiles")

        assertEquals(
            listOf("fondo", "pnoa", "pnoa-detalle", "calles"),
            ids(Json.parseToJsonElement(resultado).jsonObject),
        )
        val limpio = resultado.replace("{{DIR}}", "file:///data/files/mapa")
        assertTrue(ValidadorEstilo.esValido(limpio, "file:///data/files/mapa"))
    }

    @Test
    fun `conDetalle no cambia un estilo sin la fuente pnoa ni uno roto ni lo anade dos veces`() {
        val sinPnoa = """{"version":8,"sources":{},"layers":[]}"""
        assertEquals(sinPnoa, EstiloSatelite.conDetalle(sinPnoa, "/x/d.mbtiles"))
        assertEquals("no es json", EstiloSatelite.conDetalle("no es json", "/x/d.mbtiles"))
        val una = EstiloSatelite.conDetalle(estilo, "/x/d.mbtiles")
        assertEquals(una, EstiloSatelite.conDetalle(una, "/x/d.mbtiles"))
    }
}
