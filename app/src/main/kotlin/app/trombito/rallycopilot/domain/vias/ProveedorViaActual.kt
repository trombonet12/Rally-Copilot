package app.trombito.rallycopilot.domain.vias

import app.trombito.rallycopilot.motor.velocidad.Fijacion

/** Vía por la que se circula. [limiteKmh] es null cuando OSM no tiene `maxspeed`: entonces no se muestra límite. */
data class ViaActual(
    val nombre: String?,
    val ref: String?,
    val limiteKmh: Int?,
)

/** Vía más cercana a la posición (archivo de vías del paquete de datos, `IndiceVias` + `SeguidorVia` del motor). */
interface ProveedorViaActual {
    /** Null si no hay datos instalados o no hay vía circulable a menos de 25 m. */
    suspend fun viaEn(fijacion: Fijacion): ViaActual?
}
