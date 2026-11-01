package app.trombito.rallycopilot.data.datosmapa

import android.util.Log
import app.trombito.rallycopilot.motor.datos.LectorManifest
import app.trombito.rallycopilot.motor.datos.ManifestDatos
import app.trombito.rallycopilot.motor.datos.ManifestInvalido
import java.io.IOException

/**
 * Lo que publica la release de datos: `manifest.json` (obligatorio) más `manifest-extra.json` (opcional, con el
 * satélite z17, que va aparte para que las apps viejas no lo bajen).
 *
 * @property manifest el principal con los archivos extra de satélite añadidos.
 * @property extraDisponible false si el extra falta o no se pudo leer: lo que dependa de él se deja como está.
 */
internal class ManifestRemoto(
    val manifest: ManifestDatos,
    val extraDisponible: Boolean,
)

/**
 * Descarga el manifest principal (un fallo se propaga: [IOException] o [ManifestInvalido]) y el extra (un fallo se
 * ignora). Del extra solo se aceptan ids de satélite, y nunca uno que ya esté en el principal ni con su mismo nombre.
 */
internal fun ClienteHttp.leerManifestRemoto(config: ConfigDatosMapa): ManifestRemoto {
    val principal = LectorManifest.leer(texto(config.urlManifest))
    val extra =
        try {
            LectorManifest.leerExtra(texto(config.urlManifestExtra))
        } catch (e: IOException) {
            Log.w(ETIQUETA_LOG, "Sin manifest extra (${e.javaClass.simpleName}); se sigue sin él")
            null
        } catch (e: ManifestInvalido) {
            Log.w(ETIQUETA_LOG, "Manifest extra no válido; se sigue sin él", e)
            null
        } ?: return ManifestRemoto(principal, extraDisponible = false)
    val ids = principal.archivos.map { it.id }.toSet()
    val nombres = principal.archivos.map { it.nombre }.toSet()
    val nuevos =
        extra.archivos.filter { it.id.startsWith(PREFIJO_SATELITE) && it.id !in ids && it.nombre !in nombres }
    return ManifestRemoto(principal.copy(archivos = principal.archivos + nuevos), extraDisponible = true)
}

private const val ETIQUETA_LOG = "ManifestRemoto"
