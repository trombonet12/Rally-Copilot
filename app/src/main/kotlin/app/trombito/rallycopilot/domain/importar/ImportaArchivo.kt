package app.trombito.rallycopilot.domain.importar

/** Por qué no se pudo importar un archivo. */
enum class MotivoFalloImportar {
    /** No se pudo abrir el documento (o no es un `content://`). */
    NO_SE_PUEDE_ABRIR,

    /** Pasa de el tope de 20 MB. */
    DEMASIADO_GRANDE,

    /** No parece un GPX ni un KML. */
    FORMATO_DESCONOCIDO,

    /** El XML está roto o truncado. */
    ARCHIVO_DANADO,

    /** Es un GPX/KML válido pero sin una traza de al menos dos puntos. */
    SIN_TRAZA,

    /** La traza mide menos de un kilómetro. */
    CORTO,

    /** No se pudo guardar en la base de datos. */
    NO_GUARDADO,
}

/** Resultado de importar un archivo. */
sealed interface ResultadoImportar {
    data class Importado(
        val tramoId: String,
        val nombre: String?,
    ) : ResultadoImportar

    data class Fallo(
        val motivo: MotivoFalloImportar,
    ) : ResultadoImportar
}

/** Importa un archivo GPX o KML como tramo. [uri] es el `content://` que entrega el sistema (como texto). */
fun interface ImportaArchivo {
    suspend fun importar(uri: String): ResultadoImportar
}
