package app.trombito.rallycopilot.ui.mapa

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private const val ETIQUETA = "CamaraUltimaConocida"
private val PROVEEDORES =
    listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)

/** Zoom de conducción con el que arranca el mapa en la última posición conocida (el del seguimiento). */
internal const val ZOOM_ARRANQUE = 15.0

/**
 * Cámara de arranque del mapa mientras no llega la primera fijación: la última posición conocida del sistema (la más
 * reciente de GPS, red o pasivo) con zoom de conducción. Null sin permiso de ubicación o sin ninguna posición
 * conocida; entonces el mapa enseña la isla entera.
 */
internal fun camaraUltimaConocida(contexto: Context): CamaraMapa? {
    val permitido =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(contexto, it) == PackageManager.PERMISSION_GRANTED
        }
    val manager = contexto.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (!permitido || manager == null) return null
    val ultima =
        PROVEEDORES
            .mapNotNull { proveedor ->
                try {
                    manager.getLastKnownLocation(proveedor)
                } catch (e: SecurityException) {
                    Log.w(ETIQUETA, "Sin permiso para la última posición: ${e.javaClass.simpleName}")
                    null
                } catch (e: IllegalArgumentException) {
                    Log.w(ETIQUETA, "Proveedor $proveedor sin última posición: ${e.javaClass.simpleName}")
                    null
                }
            }.maxByOrNull { it.time }
    return ultima?.let { CamaraMapa(it.latitude, it.longitude, ZOOM_ARRANQUE) }
}

/** Esta cámara, pero sin cámara guardada arranca en la última posición conocida (si la hay). */
@Composable
internal fun CamaraVistaMapa.conArranque(): CamaraVistaMapa {
    val contexto = LocalContext.current
    val arranque = remember { camaraUltimaConocida(contexto) }
    return if (inicial == null && arranque != null) copy(inicial = arranque) else this
}
