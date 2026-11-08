package app.trombito.rallycopilot.domain.audio

/**
 * `BLUETOOTH_CONNECT`: hace falta desde Android 12 (API 31) para abrir el canal de comunicación del intercom. Antes no
 * existe y no hace falta (SCO solo pide `MODIFY_AUDIO_SETTINGS`, que se concede al instalar). Si se deniega, la voz y
 * el micrófono siguen por el móvil. Aquí solo la parte pura (nombre del permiso y versión); comprobarlo en el sistema
 * es cosa de quien tenga un `Context`.
 */
object PermisoIntercom {
    const val PERMISO = "android.permission.BLUETOOTH_CONNECT"

    /** API 31, Android 12 (`Build.VERSION_CODES.S`). */
    const val SDK_MINIMO = 31

    /** `true` si en la versión [sdk] de Android hay que pedirlo en tiempo de ejecución. */
    fun hayQuePedir(sdk: Int): Boolean = sdk >= SDK_MINIMO
}
