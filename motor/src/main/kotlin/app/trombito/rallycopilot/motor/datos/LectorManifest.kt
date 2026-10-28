package app.trombito.rallycopilot.motor.datos

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** El manifest descargado no es utilizable (JSON roto, esquema desconocido o datos que no son de fiar). */
class ManifestInvalido(
    mensaje: String,
    causa: Throwable? = null,
) : Exception(mensaje, causa)

/** Lee y valida `manifest.json` (esquema 1, generado por `scripts/manifest.py` de rally-copilot-data). */
object LectorManifest {
    /** Ids que la app necesita siempre. */
    val IDS_OBLIGATORIOS = listOf("vectorial", "estilo", "vias", "satelite")

    private const val ESQUEMA_SOPORTADO = 1
    private const val PREFIJO_URL = "https://github.com/"
    private val SHA256 = Regex("^[0-9a-f]{64}$")
    private val formato = Json { ignoreUnknownKeys = true }

    /** @throws ManifestInvalido si el JSON no se puede leer o incumple alguna regla de validez. */
    fun leer(json: String): ManifestDatos {
        val manifest = decodificar(json)
        exigir(manifest.esquema == ESQUEMA_SOPORTADO) { "Esquema no soportado: ${manifest.esquema}" }
        val ids = manifest.archivos.map { it.id }
        val repetidos = ids.filter { id -> ids.count { it == id } > 1 }.toSet()
        exigir(repetidos.isEmpty()) { "Ids repetidos: ${repetidos.joinToString()}" }
        val faltan = IDS_OBLIGATORIOS - ids.toSet()
        exigir(faltan.isEmpty()) { "Faltan archivos obligatorios: ${faltan.joinToString()}" }
        manifest.archivos.forEach(::validarArchivo)
        return manifest
    }

    private fun decodificar(texto: String): ManifestDatos =
        try {
            formato.decodeFromString<ManifestDatos>(texto)
        } catch (e: SerializationException) {
            throw ManifestInvalido("Manifest ilegible: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw ManifestInvalido("Manifest ilegible: ${e.message}", e)
        }

    private fun validarArchivo(a: ArchivoDatos) {
        exigir(a.bytes > 0) { "bytes no positivo en '${a.id}': ${a.bytes}" }
        exigir(SHA256.matches(a.sha256)) { "sha256 mal formado en '${a.id}'" }
        exigir(a.url.startsWith(PREFIJO_URL)) { "url no permitida en '${a.id}': ${a.url}" }
        exigir(nombreSeguro(a.nombre)) { "nombre no permitido en '${a.id}': ${a.nombre}" }
    }

    private fun nombreSeguro(nombre: String): Boolean =
        nombre.isNotBlank() && '/' !in nombre && '\\' !in nombre && ".." !in nombre

    private fun exigir(
        condicion: Boolean,
        mensaje: () -> String,
    ) {
        if (!condicion) throw ManifestInvalido(mensaje())
    }
}
