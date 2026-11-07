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

/** Resultado de leer un archivo sin guardarlo todavía. */
sealed interface ResultadoPreparar {
    /**
     * El archivo es una traza válida: [nombre] es el del archivo (null si no trae ninguno) y [longitudM] lo que mide;
     * [guardar] la guarda como tramo cuando se confirme (solo debe llamarse una vez).
     */
    class Lista(
        val nombre: String?,
        val longitudM: Double,
        val guardar: suspend () -> ResultadoImportar,
    ) : ResultadoPreparar

    data class Fallo(
        val motivo: MotivoFalloImportar,
    ) : ResultadoPreparar
}

/** Importa un archivo GPX o KML como tramo. [uri] es el `content://` que entrega el sistema (como texto). */
interface ImportaArchivo {
    /** Lee el archivo y comprueba que es una traza válida, sin guardar nada: para pedir confirmación antes. */
    suspend fun preparar(uri: String): ResultadoPreparar

    /** Lee el archivo y lo guarda como tramo sin preguntar. */
    suspend fun importar(uri: String): ResultadoImportar =
        when (val preparado = preparar(uri)) {
            is ResultadoPreparar.Lista -> preparado.guardar()
            is ResultadoPreparar.Fallo -> ResultadoImportar.Fallo(preparado.motivo)
        }
}
