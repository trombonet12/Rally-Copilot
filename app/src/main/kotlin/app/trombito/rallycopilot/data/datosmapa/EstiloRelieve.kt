package app.trombito.rallycopilot.data.datosmapa

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * El relieve y las curvas de nivel se descargan al elegirlos: mientras no estén, el estilo no debe apuntar a sus
 * PMTiles (MapLibre protestaría por una fuente cuyo archivo no existe). Quita del estilo las fuentes `relieve` y
 * `curvas` cuyo archivo falte, junto con las capas que las usan; si están, lo deja igual.
 */
internal object EstiloRelieve {
    /** Fuente del estilo -> archivo de la release que la alimenta. */
    private val ARCHIVOS = mapOf("relieve" to "relieve-mallorca.pmtiles", "curvas" to "curvas-mallorca.pmtiles")

    /** El [estilo] sin las fuentes cuyo archivo no cumple [existe] (se le pasa el nombre del archivo). */
    fun sinAusentes(
        estilo: String,
        existe: (String) -> Boolean,
    ): String {
        val ausentes = ARCHIVOS.filterValues { !existe(it) }.keys
        return if (ausentes.isEmpty()) {
            estilo
        } else {
            try {
                quitar(estilo, ausentes)
            } catch (_: SerializationException) {
                estilo
            } catch (_: IllegalArgumentException) {
                estilo
            }
        }
    }

    private fun quitar(
        estilo: String,
        ausentes: Set<String>,
    ): String {
        val raiz = Json.parseToJsonElement(estilo).jsonObject
        val fuentes = raiz["sources"]?.jsonObject
        val capas = raiz["layers"]?.jsonArray
        return if (fuentes == null || capas == null) {
            estilo
        } else {
            val nuevasFuentes = JsonObject(fuentes.filterKeys { it !in ausentes })
            val nuevasCapas = JsonArray(capas.filter { it.jsonObject["source"]?.jsonPrimitive?.content !in ausentes })
            JsonObject(raiz + ("sources" to nuevasFuentes) + ("layers" to nuevasCapas)).toString()
        }
    }
}
