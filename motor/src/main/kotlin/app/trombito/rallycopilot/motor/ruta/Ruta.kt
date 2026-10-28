package app.trombito.rallycopilot.motor.ruta

import app.trombito.rallycopilot.motor.geo.PuntoGeo

/**
 * Maniobra de una ruta. Se ejecuta en el punto [inicioIndice] de la polilínea y abarca hasta [finIndice]
 * (convención de Valhalla: la maniobra i termina donde empieza la i+1). [vias]: refs y nombres de la vía posterior.
 */
data class Maniobra(
    val tipo: TipoManiobra,
    val instruccionValhalla: String,
    val vias: List<String>,
    val inicioIndice: Int,
    val finIndice: Int,
    val longitudM: Double,
    val tiempoS: Double,
    val salidaRotonda: Int?,
)

data class Ruta(
    val puntos: List<PuntoGeo>,
    val maniobras: List<Maniobra>,
    val longitudM: Double,
    val tiempoS: Double,
)

/** La respuesta de Valhalla no es una ruta utilizable (JSON roto, error del servidor o datos incoherentes). */
class RutaInvalida(
    mensaje: String,
    causa: Throwable? = null,
) : Exception(mensaje, causa)
