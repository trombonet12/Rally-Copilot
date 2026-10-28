package app.trombito.rallycopilot.domain.ubicacion

import app.trombito.rallycopilot.motor.velocidad.Fijacion
import kotlinx.coroutines.flow.Flow

/**
 * Posiciones GPS a ~1 Hz sin Google Play Services (`LocationManager.GPS_PROVIDER`). El flujo solo emite mientras
 * alguien lo recoge (el mapa visible en H1; el servicio en primer plano en H2) y requiere el permiso de ubicación.
 */
interface FuenteUbicacion {
    fun fijaciones(): Flow<Fijacion>

    fun tienePermiso(): Boolean
}
