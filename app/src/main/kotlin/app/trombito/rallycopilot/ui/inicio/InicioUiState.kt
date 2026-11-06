package app.trombito.rallycopilot.ui.inicio

import app.trombito.rallycopilot.motor.geo.PuntoGeo

/** La última salida cronometrada: el tramo, lo que tardó esta vez, su mejor tiempo y la silueta del tramo. */
data class UltimaSalida(
    val tramoId: String,
    val nombre: String,
    val tiempoMs: Long,
    val mejorMs: Long,
    val traza: List<PuntoGeo> = emptyList(),
)

/** Estado del inicio: [ultimaSalida] es nula mientras no haya ninguna pasada (entonces sale la bienvenida). */
data class InicioUiState(
    val ultimaSalida: UltimaSalida? = null,
)
