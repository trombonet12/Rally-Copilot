package app.trombito.rallycopilot.data.recorridos

import kotlinx.coroutines.flow.Flow

/** Recorrido grabado y terminado (>= [RepositorioRecorridos.DISTANCIA_MINIMA_M]). */
data class Recorrido(
    val id: Long,
    val inicioMs: Long,
    val finMs: Long,
    val distanciaM: Double,
    val destinoNombre: String?,
)

/** Muestra de posición de un recorrido; [tiempoMs] en milisegundos epoch, [velocidadMs] en m/s si el GPS la da. */
data class PuntoRecorrido(
    val lat: Double,
    val lon: Double,
    val tiempoMs: Long,
    val velocidadMs: Double? = null,
)

/** Almacén de recorridos grabados (todavía no es contrato de dominio). */
interface RepositorioRecorridos {
    /** Abre un recorrido nuevo (en curso, no aparece en [listar]) y devuelve su id. */
    suspend fun empezar(destinoNombre: String? = null): Long

    /** Añade puntos al final del recorrido en curso [id], en el orden dado. */
    suspend fun anadirPuntos(
        id: Long,
        puntos: List<PuntoRecorrido>,
    )

    /** Cierra el recorrido. Devuelve null si recorre menos de 1 km: se descarta con todos sus puntos. */
    suspend fun terminar(id: Long): Recorrido?

    /** Recorridos terminados, el más reciente primero. */
    fun listar(): Flow<List<Recorrido>>

    suspend fun puntos(id: Long): List<PuntoRecorrido>

    suspend fun borrar(id: Long)

    companion object {
        const val DISTANCIA_MINIMA_M = 1_000.0
    }
}
