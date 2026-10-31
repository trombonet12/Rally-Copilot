package app.trombito.rallycopilot.domain.navegacion

import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import kotlinx.coroutines.flow.Flow

/** Guiado que empezó en [desdeMs] (ms epoch) y no se cerró: si Android mató la app, se ofrece «Seguir a <destino>». */
data class GuiadoPendiente(
    val destino: Destino,
    val perfil: PerfilRuta,
    val desdeMs: Long,
)

/**
 * Recuerda el destino del guiado en curso. Se guarda al empezar y se borra al terminar, llegar o pasar a modo libre;
 * si el proceso muere la fila queda. Los pendientes de más de 12 h se ignoran.
 */
interface RepositorioGuiadoPendiente {
    /** El guiado pendiente vigente, o null si no hay ninguno o ha caducado. */
    val pendiente: Flow<GuiadoPendiente?>

    /** Guarda [destino] y [perfil] como el guiado en curso, sustituyendo al anterior. */
    suspend fun guardar(
        destino: Destino,
        perfil: PerfilRuta,
    )

    suspend fun borrar()
}
