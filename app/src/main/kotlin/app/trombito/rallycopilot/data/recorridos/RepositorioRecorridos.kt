package app.trombito.rallycopilot.data.recorridos

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Recorrido grabado y terminado (>= [RepositorioRecorridos.DISTANCIA_MINIMA_M]). [tiempoPrevistoS] es el tiempo de
 * Valhalla de la ruta con que se empezó a guiar (null: modo libre, reconocimiento o recorrido anterior a guardarlo).
 * [llegadaEstimadaS] es el tiempo total que la app enseñó al salir, ya con el ritmo aprendido (null: no se guardó).
 * [llegado]: guiado que llegó al destino sin cambiar de destino ni de paradas (solo esos enseñan el factor personal).
 */
data class Recorrido(
    val id: Long,
    val inicioMs: Long,
    val finMs: Long,
    val distanciaM: Double,
    val destinoNombre: String?,
    val tiempoPrevistoS: Double? = null,
    val llegado: Boolean = false,
    val llegadaEstimadaS: Double? = null,
)

/** Muestra de posición de un recorrido; [tiempoMs] en milisegundos epoch, [velocidadMs] en m/s si el GPS la da. */
data class PuntoRecorrido(
    val lat: Double,
    val lon: Double,
    val tiempoMs: Long,
    val velocidadMs: Double? = null,
)

/** Almacén de recorridos grabados (todavía no es contrato de dominio). */
@Suppress("TooManyFunctions") // el contrato del almacén: recorridos, puntos, previsto, llegada y calibración
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

    /**
     * Guarda el tiempo previsto (s, el de Valhalla puro) con que se empieza a guiar el recorrido en curso [id]; con él,
     * al cerrarlo, se aprende cuánto tarda Joan de más frente a lo previsto. Por defecto no hace nada.
     */
    suspend fun guardarTiempoPrevisto(
        id: Long,
        segundos: Double?,
    ) = Unit

    /**
     * Guarda el tiempo total estimado (s, con el ritmo aprendido) que se enseñó al salir en el recorrido en curso [id]:
     * el resumen compara la llegada con él. Por defecto no hace nada.
     */
    suspend fun guardarLlegadaEstimada(
        id: Long,
        segundos: Double?,
    ) = Unit

    /**
     * Marca el recorrido en curso [id] como llegado al destino que se pidió al empezar (sin cambios de destino ni de
     * paradas por el camino). Por defecto no hace nada.
     */
    suspend fun marcarLlegado(id: Long) = Unit

    /** Ids de los recorridos aún abiertos (en curso, o huérfanos si la app se cerró a mitad). */
    suspend fun idsEnCurso(): List<Long>

    /** Recorridos terminados, el más reciente primero. */
    fun listar(): Flow<List<Recorrido>>

    suspend fun puntos(id: Long): List<PuntoRecorrido>

    suspend fun borrar(id: Long)

    /**
     * Factor de calibración de las notas con los [MAX_CALIBRACION] recorridos terminados más recientes; null si no hay
     * curvas bastantes. Esta versión lo calcula leyendo los puntos (pesado: llamar en un dispatcher de cómputo); el
     * almacén de Room lo guarda al cerrar cada recorrido y aquí solo lee ese valor.
     */
    suspend fun factorCalibracion(): Double? {
        val ids = listar().first().take(MAX_CALIBRACION).map { it.id }
        return calcularFactorCalibracion(ids.map { puntos(it) })
    }

    companion object {
        const val DISTANCIA_MINIMA_M = 1_000.0

        /** Cuántos recorridos recientes entran en la calibración. */
        const val MAX_CALIBRACION = 30
    }
}
