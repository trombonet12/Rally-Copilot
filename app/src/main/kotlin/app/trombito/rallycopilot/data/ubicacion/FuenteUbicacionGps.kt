package app.trombito.rallycopilot.data.ubicacion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posiciones del `GPS_PROVIDER` a 1 Hz sin Google Play Services. Solo registra el listener mientras alguien recoge el
 * flujo. Sin permiso, sin proveedor GPS o ante un fallo del sistema el flujo termina sin error. Nunca se escriben
 * coordenadas en el log.
 */
@Singleton
class FuenteUbicacionGps
    @Inject
    constructor(
        @ApplicationContext private val contexto: Context,
    ) : FuenteUbicacion {
        override fun tienePermiso(): Boolean =
            PERMISOS.any { contexto.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

        override fun fijaciones(): Flow<Fijacion> =
            callbackFlow {
                val manager = contexto.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                if (manager == null || !tienePermiso() || GPS !in manager.allProviders) {
                    close()
                    return@callbackFlow
                }
                val listener = OyenteGps { fijacion -> trySend(fijacion) }
                val registrado = registrar(manager, listener)
                if (registrado) {
                    ultimaReciente(manager)?.aFijacion()?.let { trySend(it) }
                } else {
                    close()
                }
                awaitClose { if (registrado) manager.removeUpdates(listener) }
            }

        private fun registrar(
            manager: LocationManager,
            listener: LocationListener,
        ): Boolean =
            try {
                manager.requestLocationUpdates(GPS, INTERVALO_MS, DISTANCIA_MIN_M, listener, Looper.getMainLooper())
                true
            } catch (e: SecurityException) {
                Log.w(ETIQUETA, "Sin permiso de ubicación al registrar el GPS: ${e.javaClass.simpleName}")
                false
            } catch (e: IllegalArgumentException) {
                Log.w(ETIQUETA, "Proveedor GPS no disponible: ${e.javaClass.simpleName}")
                false
            }

        /** Última posición conocida si tiene menos de 10 s; null si no hay, es vieja o falla el sistema. */
        private fun ultimaReciente(manager: LocationManager): Location? =
            try {
                manager.getLastKnownLocation(GPS)?.takeIf {
                    SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos in 0..EDAD_MAX_NS
                }
            } catch (e: SecurityException) {
                Log.w(ETIQUETA, "Sin permiso para la última posición: ${e.javaClass.simpleName}")
                null
            }

        private companion object {
            const val ETIQUETA = "FuenteUbicacionGps"
            const val GPS = LocationManager.GPS_PROVIDER
            const val INTERVALO_MS = 1000L
            const val DISTANCIA_MIN_M = 0f
            const val EDAD_MAX_NS = 10_000_000_000L
            val PERMISOS = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

/** Listener que convierte cada [Location] en [Fijacion] y descarta las no válidas. */
private class OyenteGps(
    private val alRecibir: (Fijacion) -> Unit,
) : LocationListener {
    override fun onLocationChanged(location: Location) {
        location.aFijacion()?.let(alRecibir)
    }

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit
}

private const val NS_POR_MS = 1_000_000L

/** Null si la latitud o la longitud no son válidas (PuntoGeo las exige en rango). */
internal fun Location.aFijacion(): Fijacion? {
    val valida = latitude in -90.0..90.0 && longitude in -180.0..180.0
    return if (valida) {
        Fijacion(
            punto = PuntoGeo(latitude, longitude, if (hasAltitude()) altitude else null),
            // Reloj monótono: `time` es la hora UTC del GNSS y en el emulador puede repetirse.
            tiempoMs = elapsedRealtimeNanos / NS_POR_MS,
            velocidadMs = if (hasSpeed()) speed.toDouble() else null,
            rumboGrados = if (hasBearing()) bearing.toDouble() else null,
            precisionM = if (hasAccuracy()) accuracy.toDouble() else null,
        )
    } else {
        null
    }
}
