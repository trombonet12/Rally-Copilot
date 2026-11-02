package app.trombito.rallycopilot.ui

import kotlinx.serialization.Serializable

/** Pantalla de mapa (modo libre provisional hasta que existan los tramos). */
@Serializable
data object RutaMapa

@Serializable
data object RutaMapasOffline

/** Elegir una zona del mapa para descargarla a z18-19. */
@Serializable
data object RutaZonaDetalle

@Serializable
data object RutaAjustes

@Serializable
data object RutaAcercaDe

/** Lista de «Mis tramos». */
@Serializable
data object RutaMisTramos

/** Mapa en modo dibujo: tocar puntos y guardar la ruta como tramo. */
@Serializable
data object RutaDibujar

/** Detalle de un tramo guardado. */
@Serializable
data class RutaTramo(
    val tramoId: String,
)

/** Edición de las notas y los sectores de un tramo guardado. */
@Serializable
data class RutaEditarTramo(
    val tramoId: String,
)

/** Análisis de una pasada de un tramo. */
@Serializable
data class RutaAnalisis(
    val tramoId: String,
    val pasadaId: Long,
)

/** Perfil del piloto. */
@Serializable
data object RutaPerfil
