package app.trombito.rallycopilot.data.ruta

import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Una forma de pedir la ruta a Valhalla, que no tiene coste de curvatura: se prueban varias combinaciones de las
 * opciones de `costing_options.auto` y se elige entre los resultados (ver [SelectorPerfiles]). `null` = valor por
 * defecto de Valhalla (no se envía): [useHighways] y [useTolls] van de 0 a 1 y [topSpeedKmh] de 10 a 252 km/h (evita
 * vías más rápidas).
 */
internal data class VarianteRuta(
    val useHighways: Double? = null,
    val useTolls: Double? = null,
    val topSpeedKmh: Int? = null,
) {
    companion object {
        /** Sin opciones: la ruta más rápida de Valhalla. */
        val RAPIDA = VarianteRuta()

        /** Autovías a medio gas (0,3) y sin peajes. */
        val POCAS_AUTOVIAS = VarianteRuta(useHighways = 0.3, useTolls = 0.0)

        /** Sin autovías ni peajes y sin vías de más de 70 km/h: la sinuosa de siempre. */
        val SINUOSA = VarianteRuta(useHighways = 0.0, useTolls = 0.0, topSpeedKmh = 70)

        /** Como [SINUOSA] pero evitando vías de más de 50 km/h: baja a las carreteras más pequeñas. */
        val MUY_SINUOSA = VarianteRuta(useHighways = 0.0, useTolls = 0.0, topSpeedKmh = 50)

        /** La variante «de toda la vida» de cada perfil, para pedir una ruta suelta. */
        fun de(perfil: PerfilRuta): VarianteRuta =
            when (perfil) {
                PerfilRuta.MAS_RAPIDA -> RAPIDA
                PerfilRuta.EQUILIBRADA -> POCAS_AUTOVIAS
                PerfilRuta.SINUOSA -> SINUOSA
            }
    }
}

/**
 * Petición `route` de Valhalla (docs: valhalla/docs/api/turn-by-turn/api-reference.md, Valhalla 3.6+). Las [paradas]
 * van entre el origen y el destino, en orden. Las opciones de `costing_options.auto` salen de la [VarianteRuta].
 * `exclude_tunnels` es una «hard exclusion» experimental que el motor solo acepta con
 * `service_limits.allow_hard_exclusions`; `FabricaMotorValhalla` lo activa. No se usa `alternates`: solo vale con dos
 * locations y [LectorRutaValhalla] lee una única ruta.
 */
internal object PeticionRuta {
    fun json(
        origen: PuntoGeo,
        destino: PuntoGeo,
        perfil: PerfilRuta,
        excluirTuneles: Boolean = false,
        paradas: List<PuntoGeo> = emptyList(),
    ): String = json(origen, destino, VarianteRuta.de(perfil), excluirTuneles, paradas)

    fun json(
        origen: PuntoGeo,
        destino: PuntoGeo,
        variante: VarianteRuta,
        excluirTuneles: Boolean = false,
        paradas: List<PuntoGeo> = emptyList(),
    ): String =
        buildJsonObject {
            // Todas las locations son de tipo break (por defecto): cada parada cierra una leg con su «has llegado».
            put("locations", JsonArray((listOf(origen) + paradas + destino).map(::punto)))
            put("costing", "auto")
            opciones(variante, excluirTuneles)?.let { put("costing_options", it) }
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
        variante: VarianteRuta,
        excluirTuneles: Boolean,
    ): JsonObject? {
        if (variante == VarianteRuta.RAPIDA && !excluirTuneles) return null
        val auto =
            buildJsonObject {
                variante.useHighways?.let { put("use_highways", it) }
                variante.useTolls?.let { put("use_tolls", it) }
                variante.topSpeedKmh?.let { put("top_speed", it) }
                if (excluirTuneles) put("exclude_tunnels", JsonPrimitive(true))
            }
        return buildJsonObject { put("auto", auto) }
    }
}
