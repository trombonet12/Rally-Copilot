package app.trombito.rallycopilot.domain.tramos

/** Un tramo escrito como archivo GPX listo para compartir: [nombreArchivo] con extensión y [contenido] en UTF-8. */
class TramoExportado(
    val nombreArchivo: String,
    val contenido: String,
)

/** Exporta un tramo guardado con su traza y sus notas (las editadas por el usuario prevalecen). */
fun interface ExportaTramo {
    /** Null si el tramo no existe o no se pudo leer. */
    suspend fun exportar(tramoId: String): TramoExportado?
}
