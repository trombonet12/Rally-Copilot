package app.trombito.rallycopilot.ui.mapa

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Estados del permiso de ubicación que la pantalla de mapa distingue. */
enum class EstadoPermisoUbicacion {
    /** Ubicación precisa concedida: el GPS puede usarse. */
    CONCEDIDO,

    /** Sin pedir aún o rechazado una vez: se explica para qué sirve y se puede volver a pedir. */
    EXPLICAR,

    /** Rechazado sin posibilidad de que el sistema vuelva a preguntar: solo se arregla en ajustes. */
    DENEGADO_PERMANENTE,

    /** El usuario solo concedió la ubicación aproximada, insuficiente para el GPS. */
    SOLO_APROXIMADA,
}

/**
 * Deduce el estado. Android no dice «denegado para siempre»: se infiere cuando ya se pidió y el sistema ya no ofrece
 * la explicación ([mostrarRationale] falso).
 */
fun estadoPermisoUbicacion(
    precisa: Boolean,
    aproximada: Boolean,
    yaPedido: Boolean,
    mostrarRationale: Boolean,
): EstadoPermisoUbicacion =
    when {
        precisa -> EstadoPermisoUbicacion.CONCEDIDO
        aproximada -> EstadoPermisoUbicacion.SOLO_APROXIMADA
        yaPedido && !mostrarRationale -> EstadoPermisoUbicacion.DENEGADO_PERMANENTE
        else -> EstadoPermisoUbicacion.EXPLICAR
    }

/** Estado del permiso más las acciones que lo cambian; se reevalúa al volver de los ajustes (ON_RESUME). */
class PermisoUbicacionUi(
    val estado: EstadoPermisoUbicacion,
    val pedir: () -> Unit,
    val abrirAjustes: () -> Unit,
)

private val PERMISOS_UBICACION =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

@Composable
fun rememberPermisoUbicacion(): PermisoUbicacionUi {
    val contexto = LocalContext.current
    var yaPedido by rememberSaveable { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val lanzador =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            yaPedido = true
            revision++
        }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }

    // `revision` fuerza la relectura del sistema tras pedir o volver de los ajustes.
    val estado =
        remember(revision, yaPedido) {
            estadoPermisoUbicacion(
                precisa = contexto.concedido(Manifest.permission.ACCESS_FINE_LOCATION),
                aproximada = contexto.concedido(Manifest.permission.ACCESS_COARSE_LOCATION),
                yaPedido = yaPedido,
                mostrarRationale = contexto.mostrarRationale(Manifest.permission.ACCESS_FINE_LOCATION),
            )
        }
    return PermisoUbicacionUi(
        estado = estado,
        pedir = { lanzador.launch(PERMISOS_UBICACION) },
        abrirAjustes = { contexto.abrirAjustesDeLaApp() },
    )
}

private fun Context.concedido(permiso: String): Boolean =
    checkSelfPermission(permiso) == PackageManager.PERMISSION_GRANTED

private fun Context.mostrarRationale(permiso: String): Boolean =
    buscarActividad()?.shouldShowRequestPermissionRationale(permiso) == true

private fun Context.buscarActividad(): Activity? {
    var actual: Context? = this
    while (actual is ContextWrapper) {
        if (actual is Activity) return actual
        actual = actual.baseContext
    }
    return null
}

internal fun Context.abrirAjustesDeLaApp() {
    val intento =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intento)
}
