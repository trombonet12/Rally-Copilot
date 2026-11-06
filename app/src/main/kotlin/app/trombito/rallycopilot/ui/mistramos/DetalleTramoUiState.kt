package app.trombito.rallycopilot.ui.mistramos

import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.motor.geo.PuntoGeo

/** Una pasada del detalle: [deltaMejorMs] es su tiempo menos el de la mejor (0 en la mejor). */
data class FilaPasada(
    val id: Long,
    val inicioMs: Long,
    val totalMs: Long,
    val deltaMejorMs: Long,
    val esMejor: Boolean,
)

/** Detalle de un tramo. Con [cargando] falso y [tramo] nulo, el tramo ya no existe. */
data class DetalleTramoUiState(
    val cargando: Boolean = true,
    val tramo: ResumenTramo? = null,
    val traza: List<PuntoGeo> = emptyList(),
    /** Estilo MapLibre offline para dibujar la traza sobre el mapa; null si aún no hay datos de mapa instalados. */
    val estiloMapaJson: String? = null,
    val pasadas: List<FilaPasada> = emptyList(),
    val renombrando: Boolean = false,
)
