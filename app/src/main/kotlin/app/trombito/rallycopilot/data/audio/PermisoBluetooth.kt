package app.trombito.rallycopilot.data.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.trombito.rallycopilot.domain.audio.PermisoIntercom

/**
 * `BLUETOOTH_CONNECT`: hace falta desde Android 12 (API 31) para abrir el canal de comunicación del intercom. Antes no
 * existe y no hace falta (SCO solo pide `MODIFY_AUDIO_SETTINGS`, que se concede al instalar). Si se deniega, la voz y
 * el micrófono siguen por el móvil.
 */
object PermisoBluetooth {
    const val PERMISO = Manifest.permission.BLUETOOTH_CONNECT // = PermisoIntercom.PERMISO

    /** `true` si en esta versión de Android hay que pedirlo en tiempo de ejecución. */
    fun hayQuePedir(sdk: Int = Build.VERSION.SDK_INT): Boolean = PermisoIntercom.hayQuePedir(sdk)

    /** Permiso concedido, o no necesario en esta versión. */
    fun concedido(contexto: Context): Boolean =
        !hayQuePedir() || ContextCompat.checkSelfPermission(contexto, PERMISO) == PackageManager.PERMISSION_GRANTED
}
