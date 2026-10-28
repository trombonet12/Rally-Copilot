package app.trombito.rallycopilot.data.datosmapa

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidadorEstiloTest {
    private val raiz = "file:///data/user/0/app.trombito.rallycopilot/files/mapa"

    private fun valido(json: String) = ValidadorEstilo.esValido(json, raiz)

    @Test
    fun `acepta un estilo con todo bajo la carpeta del mapa`() {
        val json =
            """
            {"version":8,"glyphs":"$raiz/glyphs/{fontstack}/{range}.pbf","sprite":"$raiz/sprite",
             "sources":{"a":{"type":"vector","url":"pmtiles://$raiz/mallorca.pmtiles"},
                        "b":{"type":"raster","tiles":["$raiz/t/{z}/{x}/{y}.png"]}},
             "layers":[{"id":"x","type":"fill","source":"a","filter":["==",["get","url"],"https://no-es-clave"]}]}
            """.trimIndent()

        assertTrue(valido(json))
    }

    @Test
    fun `acepta un estilo sin recursos`() {
        assertTrue(valido("""{"version":8,"sources":{},"layers":[]}"""))
    }

    @Test
    fun `rechaza una url de red en una fuente`() {
        assertFalse(valido("""{"sources":{"a":{"url":"https://evil.example/t.json"}}}"""))
        assertFalse(valido("""{"sources":{"a":{"url":"pmtiles://https://evil.example/x.pmtiles"}}}"""))
    }

    @Test
    fun `rechaza tiles que salen de la carpeta`() {
        assertFalse(valido("""{"sources":{"a":{"tiles":["$raiz/ok/{z}.png","https://evil.example/{z}.png"]}}}"""))
    }

    @Test
    fun `rechaza glyphs y sprite externos`() {
        assertFalse(valido("""{"glyphs":"https://evil.example/{fontstack}/{range}.pbf"}"""))
        assertFalse(valido("""{"sprite":"http://evil.example/sprite"}"""))
        assertFalse(valido("""{"sprite":[{"id":"a","url":"https://evil.example/sprite"}]}"""))
    }

    @Test
    fun `rechaza otra carpeta local aunque empiece igual`() {
        assertFalse(valido("""{"url":"${raiz}2/x.pmtiles"}"""))
        assertFalse(valido("""{"url":"file:///data/otra/x.pmtiles"}"""))
        assertFalse(valido("""{"url":"$raiz/../secreto.pmtiles"}"""))
    }

    @Test
    fun `rechaza geojson remoto`() {
        assertFalse(valido("""{"sources":{"a":{"type":"geojson","data":"https://evil.example/x.geojson"}}}"""))
    }

    @Test
    fun `revisa tambien las claves anidadas`() {
        assertFalse(valido("""{"layers":[{"metadata":{"deep":{"url":"https://evil.example/x"}}}]}"""))
    }

    @Test
    fun `rechaza json roto o que no es un objeto`() {
        assertFalse(valido("{ roto"))
        assertFalse(valido(""))
    }

    @Test
    fun `una url que no es texto no se salta la comprobacion`() {
        assertFalse(valido("""{"url":{"url":"https://evil.example/x"}}"""))
    }
}
