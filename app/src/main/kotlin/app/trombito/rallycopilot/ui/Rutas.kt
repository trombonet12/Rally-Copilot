package app.trombito.rallycopilot.ui

import kotlinx.serialization.Serializable

/** Pantalla de mapa (modo libre provisional hasta que existan los tramos). */
@Serializable
data object RutaMapa

@Serializable
data object RutaMapasOffline

@Serializable
data object RutaAjustes

@Serializable
data object RutaAcercaDe

/** Lista de «Mis tramos». */
@Serializable
data object RutaMisTramos

/** Detalle de un tramo guardado. */
@Serializable
data class RutaTramo(
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
