package app.trombito.rallycopilot.domain.navegacion

import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import kotlinx.coroutines.flow.Flow

/**
 * Guiado que empezó en [desdeMs] (ms epoch) y no se cerró: si Android mató la app, se ofrece «Seguir a <destino>».
 * [paradas] son las intermedias que faltaban por alcanzar, en orden.
 */
data class GuiadoPendiente(
    val destino: Destino,
    val perfil: PerfilRuta,
    val desdeMs: Long,
    val paradas: List<Destino> = emptyList(),
)

/**
 * Recuerda el destino del guiado en curso. Se guarda al empezar y se borra al terminar, llegar o pasar a modo libre;
 * si el proceso muere la fila queda. Los pendientes de más de 12 h se ignoran.
 */
interface RepositorioGuiadoPendiente {
    /** El guiado pendiente vigente, o null si no hay ninguno o ha caducado. */
    val pendiente: Flow<GuiadoPendiente?>

    /** Guarda [destino], [perfil] y las [paradas] pendientes como el guiado en curso, sustituyendo al anterior. */
    suspend fun guardar(
        destino: Destino,
        perfil: PerfilRuta,
        paradas: List<Destino> = emptyList(),
    )

    suspend fun borrar()
}
