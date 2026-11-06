package app.trombito.rallycopilot.domain.alertas

import app.trombito.rallycopilot.motor.alertas.Radar
import app.trombito.rallycopilot.motor.geo.PuntoGeo

/** Un tramo de radar para dibujar: [trazado] va de la entrada a la salida (sobre la vía si hay geometría). */
data class TramoEnMapa(
    val id: String,
    val limiteKmh: Int?,
    val trazado: List<PuntoGeo>,
)

/** Todos los radares de los datos y los trazados de sus tramos, para pintarlos siempre en el mapa. */
data class RadaresEnMapa(
    val radares: List<Radar> = emptyList(),
    val tramos: List<TramoEnMapa> = emptyList(),
)

/** Da los [RadaresEnMapa] de los datos instalados (vacío si no hay archivo de radares). */
interface ProveedorRadaresMapa {
    suspend fun radaresEnMapa(): RadaresEnMapa
}
