package app.trombito.rallycopilot.data.ubicacion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
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
 * Posiciones sin Google Play Services: el `GPS_PROVIDER` a 1 Hz más un proveedor rápido (el `FUSED_PROVIDER` de la
 * plataforma en API 31+ o, si no, el `NETWORK_PROVIDER`), unidos con [SelectorFijaciones]: el GPS manda mientras fije
 * y el rápido cubre los huecos.
 *
 * Causa raíz del bug «dentro de casa nunca encuentra la posición» (v0.3.1): solo se escuchaba el GPS, que bajo techo
 * no fija y en frío tarda minutos; sin fijación no había ni marcador. Google Maps sí localizaba porque usa también
 * Wi-Fi y móvil. Ahora la primera posición sale de la última conocida (GPS o rápido, la más reciente de menos de 2 min)
 * y el proveedor rápido da la posición aproximada hasta que el GPS fija. Las fijaciones del rápido no traen velocidad
 * ni rumbo (`velocidadMs = null`).
 *
 * Solo registra los listeners mientras alguien recoge el flujo. Sin permiso, sin ningún proveedor o ante un fallo del
 * sistema el flujo termina sin error; el fallo de un proveedor no tumba el otro. Nunca se escriben coordenadas en el
 * log.
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
                if (manager == null || !tienePermiso()) {
                    close()
                    return@callbackFlow
                }
                val disponibles = manager.allProviders
                val rapido = proveedorRapido(disponibles)
                val selector = SelectorFijaciones()
                val emitir = { fijacion: Fijacion, origen: OrigenFijacion ->
                    if (selector.aceptar(fijacion, origen)) trySend(fijacion)
                    Unit
                }
                val oyentes = mutableListOf<LocationListener>()
                if (GPS in disponibles) {
                    val oyente = OyenteGps { emitir(it, OrigenFijacion.GPS) }
                    if (registrar(manager, GPS, oyente)) oyentes += oyente
                }
                if (rapido != null) {
                    val oyente = OyenteGps { emitir(it, OrigenFijacion.RAPIDO) }
                    if (registrar(manager, rapido, oyente)) oyentes += oyente
                }
                if (oyentes.isEmpty()) {
                    close()
                } else {
                    ultimaReciente(manager, rapido)?.let { (location, origen) ->
                        location.aFijacion()?.let { emitir(it, origen) }
                    }
                }
                awaitClose { oyentes.forEach { manager.removeUpdates(it) } }
            }

        /** El fused de la plataforma (API 31+) si existe; si no, la red; si no, null. */
        private fun proveedorRapido(disponibles: List<String>): String? {
            val fused = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in disponibles
            return if (fused) {
                LocationManager.FUSED_PROVIDER
            } else {
                LocationManager.NETWORK_PROVIDER.takeIf { it in disponibles }
            }
        }

        private fun registrar(
            manager: LocationManager,
            proveedor: String,
            listener: LocationListener,
        ): Boolean =
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && proveedor == LocationManager.FUSED_PROVIDER) {
                    val peticion =
                        LocationRequest
                            .Builder(INTERVALO_MS)
                            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                            .build()
                    manager.requestLocationUpdates(proveedor, peticion, contexto.mainExecutor, listener)
                } else {
                    val hilo = Looper.getMainLooper()
                    manager.requestLocationUpdates(proveedor, INTERVALO_MS, DISTANCIA_MIN_M, listener, hilo)
                }
                true
            } catch (e: SecurityException) {
                Log.w(ETIQUETA, "Sin permiso de ubicación al registrar $proveedor: ${e.javaClass.simpleName}")
                false
            } catch (e: IllegalArgumentException) {
                Log.w(ETIQUETA, "Proveedor $proveedor no disponible: ${e.javaClass.simpleName}")
                false
            }

        /** La más reciente de las últimas conocidas del GPS y del [rapido] con menos de 2 min; null si no hay. */
        private fun ultimaReciente(
            manager: LocationManager,
            rapido: String?,
        ): Pair<Location, OrigenFijacion>? {
            val ahora = SystemClock.elapsedRealtimeNanos()
            val candidatas =
                listOfNotNull(
                    ultimaConocida(manager, GPS)?.let { it to OrigenFijacion.GPS },
                    rapido?.let { ultimaConocida(manager, it) }?.let { it to OrigenFijacion.RAPIDO },
                )
            return candidatas
                .filter { ahora - it.first.elapsedRealtimeNanos in 0..EDAD_MAX_NS }
                .maxByOrNull { it.first.elapsedRealtimeNanos }
        }

        private fun ultimaConocida(
            manager: LocationManager,
            proveedor: String,
        ): Location? =
            try {
                manager.getLastKnownLocation(proveedor)
            } catch (e: SecurityException) {
                Log.w(ETIQUETA, "Sin permiso para la última posición: ${e.javaClass.simpleName}")
                null
            } catch (e: IllegalArgumentException) {
                Log.w(ETIQUETA, "Proveedor $proveedor sin última posición: ${e.javaClass.simpleName}")
                null
            }

        private companion object {
            const val ETIQUETA = "FuenteUbicacionGps"
            const val GPS = LocationManager.GPS_PROVIDER
            const val INTERVALO_MS = 1000L
            const val DISTANCIA_MIN_M = 0f
            const val EDAD_MAX_NS = 120_000_000_000L
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
            rumboGrados = if (rumboFiable()) bearing.toDouble() else null,
            precisionM = if (hasAccuracy()) accuracy.toDouble() else null,
        )
    } else {
        null
    }
}

/** Peor precisión de rumbo (grados) que se acepta del receptor. */
private const val PRECISION_RUMBO_MAX_GRADOS = 20f

/**
 * El rumbo del receptor solo cuenta si su precisión es buena: el GPS del emulador da siempre 0 con ±30° y algunos
 * móviles dan rumbos sin sentido a poca velocidad. Sin rumbo fiable, el mapa usa el del movimiento.
 */
private fun Location.rumboFiable(): Boolean =
    hasBearing() && (!hasBearingAccuracy() || bearingAccuracyDegrees <= PRECISION_RUMBO_MAX_GRADOS)
