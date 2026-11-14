package app.trombito.rallycopilot.domain.tramos

import app.trombito.rallycopilot.motor.vias.SentidoVia

/** Qué es un tramo guardado: el trayecto entero de un viaje (`VIAJE`) o un trozo de una carretera y sentido (`VIA`). */
enum class TipoTramo { VIAJE, VIA }

/**
 * Lo que sitúa un tramo `VIA` en su carretera: [viaRef] es el id estable de la carretera (la `ref` normalizada, p. ej.
 * `ma15`, o el nombre normalizado con prefijo `~` si no tiene), [pkDesde] y [pkHasta] los kilómetros de salida y meta
 * (null sin hitos), [sentido] hacia dónde crece el kilómetro, [destino] el lugar hacia el que se va (null si no se
 * sabe) y [recorridoOrigenId] el viaje del que salió.
 */
data class DatosVia(
    val viaRef: String,
    val pkDesde: Double?,
    val pkHasta: Double?,
    val sentido: SentidoVia,
    val destino: String?,
    val recorridoOrigenId: Long?,
)
