package app.trombito.rallycopilot.data.ruta

import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Petición `route` de Valhalla (docs: valhalla/docs/api/turn-by-turn/api-reference.md, Valhalla 3.6+). Las [paradas]
 * van entre el origen y el destino, en orden.
 * Opciones de `costing_options.auto` usadas en [PerfilRuta.SINUOSA]: `use_highways`, `use_tolls` (0..1) y
 * `top_speed` (10..252 km/h, evita vías más rápidas). `exclude_tunnels` es una «hard exclusion» experimental que el
 * motor solo acepta con `service_limits.allow_hard_exclusions`; `FabricaMotorValhalla` lo activa.
 */
internal object PeticionRuta {
    private const val VELOCIDAD_MAXIMA_SINUOSA_KMH = 70

    fun json(
        origen: PuntoGeo,
        destino: PuntoGeo,
        perfil: PerfilRuta,
        excluirTuneles: Boolean = false,
        paradas: List<PuntoGeo> = emptyList(),
    ): String =
        buildJsonObject {
            // Todas las locations son de tipo break (por defecto): cada parada cierra una leg con su «has llegado».
            put("locations", JsonArray((listOf(origen) + paradas + destino).map(::punto)))
            put("costing", "auto")
            opciones(perfil, excluirTuneles)?.let { put("costing_options", it) }
            put("units", "kilometers")
            put("language", "es-ES")
            put("directions_type", "instructions")
        }.toString()

    private fun punto(p: PuntoGeo): JsonObject =
        buildJsonObject {
            put("lat", p.lat)
            put("lon", p.lon)
        }

    private fun opciones(
        perfil: PerfilRuta,
        excluirTuneles: Boolean,
    ): JsonObject? {
        if (perfil == PerfilRuta.MAS_RAPIDA && !excluirTuneles) return null
        val auto =
            buildJsonObject {
                if (perfil == PerfilRuta.SINUOSA) {
                    put("use_highways", 0.0)
                    put("use_tolls", 0.0)
                    put("top_speed", VELOCIDAD_MAXIMA_SINUOSA_KMH)
                }
                if (excluirTuneles) put("exclude_tunnels", JsonPrimitive(true))
            }
        return buildJsonObject { put("auto", auto) }
    }
}
