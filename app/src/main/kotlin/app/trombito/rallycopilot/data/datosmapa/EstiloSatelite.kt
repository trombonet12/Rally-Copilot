package app.trombito.rallycopilot.data.datosmapa

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Teselas del satélite a z17 (solo ese nivel); el z16 va en el `satelite.json` del paquete de estilo. */
internal const val NOMBRE_TESELAS_Z17 = "pnoa-mallorca-z17.pmtiles"

/**
 * Añade al estilo de satélite del paquete la fuente ráster del z17 y su capa justo encima de la del z16. Por encima de
 * z17 MapLibre sobreamplía el z17; por debajo no se pinta (minzoom 17) y manda el z16. Trabaja sobre el estilo con
 * `{{DIR}}` sin sustituir.
 */
internal object EstiloSatelite {
    private const val FUENTE_Z16 = "pnoa"
    private const val FUENTE_Z17 = "pnoa-z17"
    private const val NIVEL_Z17 = 17
    private const val FUENTE_DETALLE = "pnoa-detalle"
    private const val NIVEL_DETALLE_MIN = 18
    private const val NIVEL_DETALLE_MAX = 19
    private const val TAMANO_TESELA_POR_DEFECTO = 256

    /** El [estilo] con la capa z17; igual que llegó si no se reconoce la estructura (sin fuente `pnoa`, JSON roto). */
    fun conZ17(estilo: String): String =
        try {
            ampliar(estilo)
        } catch (_: SerializationException) {
            estilo
        } catch (_: IllegalArgumentException) {
            estilo
        }

    /**
     * Añade la fuente ráster del detalle (`mbtiles://` con [rutaAbsoluta], z18–19) y su capa encima de la del z17 (o de
     * la del z16 si no hay z17). Sin la fuente `pnoa` o con JSON roto devuelve el [estilo] igual. Es la única forma de
     * ruta que MapLibre carga para un MBTiles: absoluta y sin `file://`.
     */
    fun conDetalle(
        estilo: String,
        rutaAbsoluta: String,
    ): String =
        try {
            ampliarDetalle(estilo, rutaAbsoluta)
        } catch (_: SerializationException) {
            estilo
        } catch (_: IllegalArgumentException) {
            estilo
        }

    private fun ampliarDetalle(
        estilo: String,
        rutaAbsoluta: String,
    ): String {
        val raiz = Json.parseToJsonElement(estilo).jsonObject
        val fuentes = raiz["sources"]?.jsonObject
        val z16 = fuentes?.get(FUENTE_Z16)?.jsonObject
        val capas = raiz["layers"]?.jsonArray
        val indice = capas?.indexOfLast { esCapaDePnoa(it) } ?: -1
        val aplicable = z16 != null && capas != null && fuentes != null && indice >= 0
        return if (aplicable && FUENTE_DETALLE !in fuentes) {
            serializarDetalle(raiz, fuentes, capas, z16, indice, rutaAbsoluta)
        } else {
            estilo
        }
    }

    /** Capa de la fuente del z16 o del z17. */
    private fun esCapaDePnoa(capa: JsonElement): Boolean {
        val fuente = capa.jsonObject["source"]?.jsonPrimitive?.content
        return fuente == FUENTE_Z17 || fuente == FUENTE_Z16
    }

    @Suppress("LongParameterList") // las piezas ya leídas del estilo y dónde insertar la capa
    private fun serializarDetalle(
        raiz: JsonObject,
        fuentes: JsonObject,
        capas: JsonArray,
        z16: JsonObject,
        indice: Int,
        rutaAbsoluta: String,
    ): String {
        val fuente =
            buildJsonObject {
                put("type", "raster")
                put("url", "mbtiles://$rutaAbsoluta")
                put("tileSize", z16["tileSize"]?.jsonPrimitive?.content?.toIntOrNull() ?: TAMANO_TESELA_POR_DEFECTO)
                put("minzoom", NIVEL_DETALLE_MIN)
                put("maxzoom", NIVEL_DETALLE_MAX)
                z16["attribution"]?.let { put("attribution", it) }
            }
        val capa =
            buildJsonObject {
                put("id", FUENTE_DETALLE)
                put("type", "raster")
                put("source", FUENTE_DETALLE)
                put("minzoom", NIVEL_DETALLE_MIN)
            }
        val capasNuevas = capas.toMutableList().apply { add(indice + 1, capa) }
        val resultado =
            JsonObject(
                raiz.mapValues { (clave, valor) ->
                    when (clave) {
                        "sources" -> JsonObject(fuentes + (FUENTE_DETALLE to fuente))
                        "layers" -> JsonArray(capasNuevas)
                        else -> valor
                    }
                },
            )
        return Json.encodeToString(JsonObject.serializer(), resultado)
    }

    private fun ampliar(estilo: String): String {
        val raiz = Json.parseToJsonElement(estilo).jsonObject
        val fuentes = raiz["sources"]?.jsonObject
        val z16 = fuentes?.get(FUENTE_Z16)?.jsonObject
        val capas = raiz["layers"]?.jsonArray
        val indiceZ16 = capas?.indexOfFirst { it.jsonObject["source"]?.jsonPrimitive?.content == FUENTE_Z16 } ?: -1
        val aplicable = z16 != null && capas != null && FUENTE_Z17 !in fuentes && indiceZ16 >= 0
        return if (aplicable) serializar(raiz, fuentes, capas, z16, indiceZ16) else estilo
    }

    private fun serializar(
        raiz: JsonObject,
        fuentes: JsonObject,
        capas: JsonArray,
        z16: JsonObject,
        indiceZ16: Int,
    ): String {
        val fuenteZ17 =
            buildJsonObject {
                put("type", "raster")
                put("url", "pmtiles://{{DIR}}/$NOMBRE_TESELAS_Z17")
                put("tileSize", z16["tileSize"]?.jsonPrimitive?.content?.toIntOrNull() ?: TAMANO_TESELA_POR_DEFECTO)
                put("minzoom", NIVEL_Z17)
                put("maxzoom", NIVEL_Z17)
                z16["attribution"]?.let { put("attribution", it) }
            }
        val capaZ17 =
            buildJsonObject {
                put("id", FUENTE_Z17)
                put("type", "raster")
                put("source", FUENTE_Z17)
                put("minzoom", NIVEL_Z17)
            }
        val capasNuevas = capas.toMutableList().apply { add(indiceZ16 + 1, capaZ17) }
        val resultado =
            JsonObject(
                raiz.mapValues { (clave, valor) ->
                    when (clave) {
                        "sources" -> JsonObject(fuentes + (FUENTE_Z17 to fuenteZ17))
                        "layers" -> JsonArray(capasNuevas)
                        else -> valor
                    }
                },
            )
        return Json.encodeToString(JsonObject.serializer(), resultado)
    }
}
