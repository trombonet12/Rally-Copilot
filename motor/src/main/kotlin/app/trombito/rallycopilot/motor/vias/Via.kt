package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo

/** Sentido de circulación de una vía respecto a su sentido de dibujo (etiqueta OSM `oneway`). */
enum class SentidoUnico { NO, ADELANTE, ATRAS }

/**
 * Vía circulable de OSM. Los límites son el texto crudo de OSM (ver [LimiteVelocidad]); `puntos` va en el sentido de
 * dibujo, que es el que referencian `maxspeed:forward` y `maxspeed:backward`.
 */
data class Via(
    val id: Long,
    val clase: String,
    val maxspeed: String?,
    val maxspeedAdelante: String?,
    val maxspeedAtras: String?,
    val sentidoUnico: SentidoUnico,
    val ref: String?,
    val nombre: String?,
    val puntos: List<PuntoGeo>,
    /** `junction` de OSM (`roundabout`, `circular`...); null si no lo tiene o los datos no traen esa columna. */
    val junction: String? = null,
    /** `bridge` de OSM (`yes`, `viaduct`...); null si no es un puente. */
    val bridge: String? = null,
    /** `tunnel` de OSM (`yes`, `culvert`...); null si no es un túnel. */
    val tunnel: String? = null,
    /** `layer` de OSM (nivel relativo al suelo); null si no está etiquetado. */
    val layer: Int? = null,
) {
    /** Es (parte de) una rotonda: `junction=roundabout` o `circular`. */
    val esRotonda: Boolean get() = junction == "roundabout" || junction == "circular"

    /**
     * Nivel de la vía respecto al suelo: su `layer` si lo trae; si no, 1 en un puente, -1 en un túnel y 0 en el resto
     * (un `culvert` es una alcantarilla: no cuenta como túnel). Dos vías de distinto nivel no se cruzan: una pasa por
     * encima de la otra.
     */
    val nivel: Int
        get() =
            layer ?: when {
                bridge != null && bridge != "no" -> 1
                tunnel != null && tunnel != "no" && tunnel != "culvert" -> -1
                else -> 0
            }
}
