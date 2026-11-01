package app.trombito.rallycopilot.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions") // un DAO por agregado: tramos, pasadas, mejores y la transacción de crear
@Dao
abstract class TramosDao {
    @Insert
    abstract suspend fun insertarTramo(tramo: TramoEntity)

    @Insert
    abstract suspend fun insertarPasada(pasada: PasadaEntity): Long

    @Query("SELECT * FROM tramo ORDER BY creadoEn DESC, id")
    abstract fun observarTramos(): Flow<List<TramoEntity>>

    @Query("SELECT * FROM tramo ORDER BY creadoEn, id")
    abstract suspend fun tramos(): List<TramoEntity>

    @Query("SELECT * FROM tramo WHERE id = :id")
    abstract suspend fun tramo(id: String): TramoEntity?

    @Query("SELECT tramoId, COUNT(*) AS pasadas, MIN(totalMs) AS mejorMs FROM pasada GROUP BY tramoId")
    abstract fun observarResumenes(): Flow<List<ResumenPasadas>>

    @Query("SELECT * FROM pasada WHERE tramoId = :tramoId ORDER BY totalMs, id LIMIT 1")
    abstract suspend fun mejorPasada(tramoId: String): PasadaEntity?

    @Query("SELECT * FROM pasada WHERE tramoId = :tramoId ORDER BY totalMs, id LIMIT 1")
    abstract fun observarMejorPasada(tramoId: String): Flow<PasadaEntity?>

    @Query(
        "SELECT p.* FROM pasada p WHERE p.id = " +
            "(SELECT q.id FROM pasada q WHERE q.tramoId = p.tramoId ORDER BY q.totalMs, q.id LIMIT 1)",
    )
    abstract suspend fun mejoresPasadas(): List<PasadaEntity>

    @Query("DELETE FROM tramo WHERE id = :id")
    abstract suspend fun borrar(id: String)

    @Transaction
    open suspend fun crear(
        tramo: TramoEntity,
        primera: PasadaEntity,
    ): Long {
        insertarTramo(tramo)
        return insertarPasada(primera.copy(tramoId = tramo.id))
    }
}
