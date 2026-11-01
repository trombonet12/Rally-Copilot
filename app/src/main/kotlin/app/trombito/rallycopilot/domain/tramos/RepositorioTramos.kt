package app.trombito.rallycopilot.domain.tramos

import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.tramos.Tramo
import kotlinx.coroutines.flow.Flow

/** Tramo guardado con su resumen: [pasadas] hechas y [mejorTotalMs] (null si aún no tiene ninguna). */
data class TramoGuardado(
    val tramo: Tramo,
    val nombre: String?,
    val creadoEnMs: Long,
    val pasadas: Int,
    val mejorTotalMs: Long?,
)

/** Un tramo listo para cronometrar y su mejor pasada hasta ahora (null si no tiene ninguna). */
data class TramoCronometrable(
    val tramo: Tramo,
    val mejor: Pasada?,
)

/**
 * Tramos cronometrables y sus pasadas. El [Tramo.id] es estable y lo da quien lo crea; las pasadas se identifican por
 * un número que da el almacén. La mejor pasada de un tramo es la de menor tiempo total (a igualdad, la primera).
 */
interface RepositorioTramos {
    /** Todos los tramos, el más reciente primero, con su número de pasadas y mejor tiempo. */
    fun observar(): Flow<List<TramoGuardado>>

    /** La mejor pasada del tramo, cada vez que cambia; null si no hay tramo o pasadas. */
    fun observarMejorPasada(tramoId: String): Flow<Pasada?>

    /** Todos los tramos con su mejor pasada completa, para cronometrar (lectura única). */
    suspend fun cronometrables(): List<TramoCronometrable>

    suspend fun mejorPasada(tramoId: String): Pasada?

    /** Guarda un tramo nuevo con su primera pasada ([inicioMs]: epoch de su salida), en una sola transacción. */
    suspend fun crear(
        tramo: Tramo,
        nombre: String?,
        creadoEnMs: Long,
        primera: Pasada,
        inicioMs: Long,
    )

    /** Guarda una pasada de un tramo existente y devuelve su número; falla si el tramo no existe. */
    suspend fun guardarPasada(
        pasada: Pasada,
        inicioMs: Long,
    ): Long

    /** Borra el tramo y, en cascada, sus pasadas. */
    suspend fun borrar(tramoId: String)
}
