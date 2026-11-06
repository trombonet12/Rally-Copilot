package app.trombito.rallycopilot.domain.ubicacion

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

/** Lo máximo que se espera a una fijación nueva (bajo la conducción llega al instante con la última conocida). */
const val ESPERA_POSICION_MS = 3_000L

/** La posición actual: la primera fijación que llegue en [esperaMs], o `null` si no hay (sin permiso o sin señal). */
suspend fun FuenteUbicacion.posicionActual(esperaMs: Long = ESPERA_POSICION_MS): PuntoGeo? =
    withTimeoutOrNull(esperaMs) { fijaciones().firstOrNull() }?.punto
