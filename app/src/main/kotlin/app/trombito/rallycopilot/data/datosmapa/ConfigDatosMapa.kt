package app.trombito.rallycopilot.data.datosmapa

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

const val URL_MANIFEST = "https://github.com/trombonet12/rally-copilot-data/releases/latest/download/manifest.json"

/**
 * Parámetros del paquete de datos. En producción los da `DatosMapaModule`; los tests cambian la URL del manifest, la
 * carpeta, el espacio libre y la forma de abrir conexiones (para apuntar a un servidor local).
 */
data class ConfigDatosMapa(
    val urlManifest: String,
    /** `filesDir/mapa/`: los datos instalados; las descargas en curso viven en `.nuevo/`. */
    val directorioMapa: File,
    val userAgent: String,
    val bytesLibres: () -> Long,
    val abrirConexion: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    val directorioNuevo: File get() = File(directorioMapa, NOMBRE_NUEVO)

    companion object {
        const val NOMBRE_NUEVO = ".nuevo"
    }
}
