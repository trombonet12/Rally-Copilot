package app.trombito.rallycopilot.motor.datos

import kotlinx.serialization.Serializable

/** Un archivo de datos de la release (mapa, estilo, vías, satélite) tal como lo describe `manifest.json`. */
@Serializable
data class ArchivoDatos(
    val id: String,
    val nombre: String,
    val url: String,
    val bytes: Long,
    val sha256: String,
    val version: String,
    val atribucion: String,
    val licencia: String,
)

/** Contenido de `manifest.json`: versión global de la release y sus archivos. */
@Serializable
data class ManifestDatos(
    val esquema: Int,
    val version: String,
    val generado: String,
    val archivos: List<ArchivoDatos>,
) {
    /** El archivo con ese id, o null si no está en el manifest. */
    fun archivo(id: String): ArchivoDatos? = archivos.firstOrNull { it.id == id }
}
