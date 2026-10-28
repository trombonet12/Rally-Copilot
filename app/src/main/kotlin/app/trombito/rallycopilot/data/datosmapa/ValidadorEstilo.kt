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
 */
object ValidadorEstilo {
    private val CLAVES_TEXTO = setOf("url", "glyphs", "sprite", "data")
    private const val CLAVE_TILES = "tiles"
    private const val PREFIJO_PMTILES = "pmtiles://"

    /** @param raizUrl carpeta del mapa como URL, p. ej. `file:///data/.../mapa` (sin barra final). */
    fun esValido(
        estilo: String,
        raizUrl: String,
    ): Boolean {
        val raiz = parsear(estilo) ?: return false
        return recorrer(raiz, "$raizUrl/")
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
        prefijo: String,
    ): Boolean =
        when (elemento) {
            is JsonObject -> elemento.all { (clave, valor) -> entradaValida(clave, valor, prefijo) }
            is JsonArray -> elemento.all { recorrer(it, prefijo) }
            is JsonPrimitive -> true
        }

    private fun entradaValida(
        clave: String,
        valor: JsonElement,
        prefijo: String,
    ): Boolean =
        when {
            clave in CLAVES_TEXTO && valor is JsonPrimitive && valor.isString -> rutaLocal(valor.content, prefijo)
            clave == CLAVE_TILES && valor is JsonArray -> valor.all { tileValido(it, prefijo) }
            else -> recorrer(valor, prefijo)
        }

    private fun tileValido(
        tile: JsonElement,
        prefijo: String,
    ): Boolean =
        if (tile is JsonPrimitive) tile.isString && rutaLocal(tile.content, prefijo) else recorrer(tile, prefijo)

    private fun rutaLocal(
        valor: String,
        prefijo: String,
    ): Boolean {
        val sinEsquema = valor.removePrefix(PREFIJO_PMTILES)
        return sinEsquema.startsWith(prefijo) && sinEsquema.substring(prefijo.length).split('/').none { it == ".." }
    }
}
