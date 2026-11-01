package app.trombito.rallycopilot.domain.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.SentidoCamino
import app.trombito.rallycopilot.motor.velocidad.Fijacion

/**
 * Vía por la que se circula. [limiteKmh] es null cuando OSM no tiene `maxspeed`: entonces no se muestra límite.
 * [trazado] es su geometría (la misma lista en todas las fijaciones sobre la vía; vacía si no se conoce) y [sentido]
 * cómo se circula por ella respecto a ese orden; sirven para ajustar el marcador y el giro del mapa al camino.
 */
data class ViaActual(
    val nombre: String?,
    val ref: String?,
    val limiteKmh: Int?,
    val trazado: List<PuntoGeo> = emptyList(),
    val sentido: SentidoCamino = SentidoCamino.AMBOS,
)

/** Vía más cercana a la posición (archivo de vías del paquete de datos, `IndiceVias` + `SeguidorVia` del motor). */
interface ProveedorViaActual {
    /** Null si no hay datos instalados o no hay vía circulable a menos de 25 m. */
    suspend fun viaEn(fijacion: Fijacion): ViaActual?
}
