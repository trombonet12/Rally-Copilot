package app.trombito.rallycopilot.ui.mistramos

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val CAPA_ETIQUETA_ISLAS = "earth_label_islands"

/**
 * El estilo del mapa sin la etiqueta de las islas («Mallorca»): en el mapa compacto del detalle se solapaba con el pin
 * de meta. Si el estilo no se puede leer se devuelve tal cual: peor es quedarse sin mapa.
 */
fun sinEtiquetaDeIslas(estiloJson: String): String =
    try {
        val estilo = JSONObject(estiloJson)
        val capas = estilo.getJSONArray("layers")
        val sinIslas = JSONArray()
        for (i in 0 until capas.length()) {
            val capa = capas.getJSONObject(i)
            if (capa.optString("id") != CAPA_ETIQUETA_ISLAS) sinIslas.put(capa)
        }
        estilo.put("layers", sinIslas).toString()
    } catch (_: JSONException) {
        estiloJson
    }
