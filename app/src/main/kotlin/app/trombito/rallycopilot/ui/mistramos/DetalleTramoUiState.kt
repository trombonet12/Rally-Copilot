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

/**
 * Un subtramo guardado del detalle: el trozo de [desdeM] a [hastaM] (metros desde la salida) recorrido en [inicioMs]
 * en [tiempoMs]; [deltaMs] es su tiempo menos el mejor de entonces (null si era la primera vez en ese trozo).
 */
data class FilaSubtramo(
    val indice: Int,
    val inicioMs: Long,
    val desdeM: Double,
    val hastaM: Double,
    val tiempoMs: Long,
    val deltaMs: Long?,
)

/** Cómo va la exportación del tramo para compartirlo: [PREPARANDO] mientras se escribe el archivo. */
enum class EstadoCompartir { LIBRE, PREPARANDO, FALLO }

/** Detalle de un tramo. Con [cargando] falso y [tramo] nulo, el tramo ya no existe. */
data class DetalleTramoUiState(
    val cargando: Boolean = true,
    val tramo: ResumenTramo? = null,
    val traza: List<PuntoGeo> = emptyList(),
    /** Estilo MapLibre offline para dibujar la traza sobre el mapa; null si aún no hay datos de mapa instalados. */
    val estiloMapaJson: String? = null,
    val pasadas: List<FilaPasada> = emptyList(),
    /** Subtramos guardados de este tramo, el más reciente primero; vacío = no se enseña la sección. */
    val subtramos: List<FilaSubtramo> = emptyList(),
    val renombrando: Boolean = false,
    val compartir: EstadoCompartir = EstadoCompartir.LIBRE,
)
