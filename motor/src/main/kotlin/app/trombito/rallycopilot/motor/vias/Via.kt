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
)
