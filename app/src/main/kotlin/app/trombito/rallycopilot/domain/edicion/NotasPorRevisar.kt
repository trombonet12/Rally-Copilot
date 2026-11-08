package app.trombito.rallycopilot.domain.edicion

/**
 * Una nota que Joan marcó como mal cantada («esta nota estaba mal»): [posicionM] son metros sobre la traza del tramo
 * y [texto] lo que decía la nota en la hoja del tramo, o `null` si ya no se puede reconstruir (se quitó o se movió).
 */
data class NotaPorRevisar(
    val posicionM: Double,
    val tiempoMs: Long,
    val texto: String?,
)

/** Las notas por revisar de un tramo guardado: las marcas `nota-mal` con el texto de su nota si se sabe. */
fun interface ListaNotasPorRevisar {
    /** Por posición; vacía si el tramo no tiene ninguna (o no existe). */
    suspend fun de(tramoId: String): List<NotaPorRevisar>
}
