package app.trombito.rallycopilot.domain.navegacion

import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.EstadoRuta
import app.trombito.rallycopilot.motor.ruta.Ruta
import kotlinx.coroutines.flow.StateFlow

data class Destino(
    val nombre: String,
    val punto: PuntoGeo,
)

/** Estado de la conducción, publicado por el servicio en primer plano y observado por la UI. */
sealed interface EstadoNavegacion {
    data object Inactiva : EstadoNavegacion

    /** Modo libre (sin destino): sigue la vía y graba el recorrido. */
    data class Libre(
        val desdeMs: Long,
        val distanciaM: Double,
    ) : EstadoNavegacion

    data class Guiando(
        val destino: Destino,
        val perfil: PerfilRuta,
        val ruta: Ruta,
        val progreso: EstadoRuta?,
        val recalculando: Boolean,
    ) : EstadoNavegacion

    data class Llegada(
        val destino: Destino,
    ) : EstadoNavegacion

    data class Error(
        val motivo: String,
    ) : EstadoNavegacion
}

/**
 * Arranca y para la conducción. Empezar lanza el servicio en primer plano de ubicación (solo desde la app visible: lo
 * pide la UI tras una acción del usuario). La ruta inicial ya viene calculada por la vista previa; los recálculos los
 * hace el servicio al salirse de la ruta.
 */
interface ControlNavegacion {
    val estado: StateFlow<EstadoNavegacion>

    fun empezarLibre()

    fun empezar(
        destino: Destino,
        perfil: PerfilRuta,
        ruta: Ruta,
    )

    fun terminar()
}
