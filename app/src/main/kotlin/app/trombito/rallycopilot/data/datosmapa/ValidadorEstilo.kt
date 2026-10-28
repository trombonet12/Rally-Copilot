package app.trombito.rallycopilot.data.datosmapa

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Garantiza que un estilo de MapLibre (ya con `{{DIR}}` sustituido) solo referencia archivos de la carpeta del mapa:
 * ninguna `url`, `tiles`, `glyphs`, `sprite` ni `data` puede apuntar a la red ni a otra carpeta. Así un zip de estilo
 * manipulado no puede hacer que la app salga a internet por su cuenta.
 *
 * Fuentes locales admitidas:
 * - `file://<raiz>/...` y `pmtiles://file://<raiz>/...`.
 * - `mbtiles://<ruta absoluta>/...` (sin `file://`): es la única forma que MapLibre 13.6.1 carga de verdad para la capa
 *   de detalle de satélite escribible (H9). Spike en emulador: `MBTilesFileSource only supports absolute path urls`;
 *   `mbtiles://file://...` se rechaza porque MapLibre no lo acepta.
 *
 * Cualquier otro esquema, `..` o una carpeta distinta de la del mapa se rechaza.
 */
object ValidadorEstilo {
    private val CLAVES_TEXTO = setOf("url", "glyphs", "sprite", "data")
    private const val CLAVE_TILES = "tiles"
    private const val ESQUEMA_PMTILES = "pmtiles://"
    private const val ESQUEMA_MBTILES = "mbtiles://"
    private const val PREFIJO_FILE = "file://"

    /**
     * Prefijos válidos para una ruta de la carpeta del mapa: [conFile] (`file://<raiz>/`, para `file://` y
     * `pmtiles://`) y [absoluta] (`<raiz sin file://>/`, p. ej. `/data/.../mapa/`, para `mbtiles://`).
     */
    private class Raices(
        val conFile: String,
        val absoluta: String,
    )

    /** @param raizUrl carpeta del mapa como URL, p. ej. `file:///data/.../mapa` (sin barra final). */
    fun esValido(
        estilo: String,
        raizUrl: String,
    ): Boolean {
        val raiz = parsear(estilo) ?: return false
        val raices =
            Raices(
                conFile = "$raizUrl/",
                absoluta = "${raizUrl.removePrefix(PREFIJO_FILE)}/",
            )
        return recorrer(raiz, raices)
    }

    private fun parsear(texto: String): JsonElement? =
        try {
            Json.parseToJsonElement(texto)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun recorrer(
        elemento: JsonElement,
        raices: Raices,
    ): Boolean =
        when (elemento) {
            is JsonObject -> elemento.all { (clave, valor) -> entradaValida(clave, valor, raices) }
            is JsonArray -> elemento.all { recorrer(it, raices) }
            is JsonPrimitive -> true
        }

    private fun entradaValida(
        clave: String,
        valor: JsonElement,
        raices: Raices,
    ): Boolean =
        when {
            clave in CLAVES_TEXTO && valor is JsonPrimitive && valor.isString -> rutaLocal(valor.content, raices)
            clave == CLAVE_TILES && valor is JsonArray -> valor.all { tileValido(it, raices) }
            else -> recorrer(valor, raices)
        }

    private fun tileValido(
        tile: JsonElement,
        raices: Raices,
    ): Boolean = if (tile is JsonPrimitive) tile.isString && rutaLocal(tile.content, raices) else recorrer(tile, raices)

    private fun rutaLocal(
        valor: String,
        raices: Raices,
    ): Boolean {
        val (resto, prefijo) =
            when {
                valor.startsWith(ESQUEMA_MBTILES) -> valor.removePrefix(ESQUEMA_MBTILES) to raices.absoluta
                valor.startsWith(ESQUEMA_PMTILES) -> valor.removePrefix(ESQUEMA_PMTILES) to raices.conFile
                else -> valor to raices.conFile
            }
        return resto.startsWith(prefijo) && resto.substring(prefijo.length).split('/').none { it == ".." }
    }
}
