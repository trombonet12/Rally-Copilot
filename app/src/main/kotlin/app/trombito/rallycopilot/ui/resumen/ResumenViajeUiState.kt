package app.trombito.rallycopilot.ui.resumen

import app.trombito.rallycopilot.domain.resumen.ViajeResumido

/**
 * Resumen del viaje. Mientras [cargando] se enseña la espera; sin [cargando], [viaje] nulo es «no hay resumen» (el
 * recorrido ya no existe) y [error] dice que falló la lectura. [estiloMapaJson] es el estilo MapLibre offline (null si
 * aún no hay datos de mapa instalados).
 */
data class ResumenViajeUiState(
    val cargando: Boolean = true,
    val viaje: ViajeResumido? = null,
    val estiloMapaJson: String? = null,
    val error: Boolean = false,
)
