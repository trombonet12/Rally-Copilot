package app.trombito.rallycopilot.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AjustesConduccionDao {
    @Query("SELECT * FROM ajustes_conduccion WHERE id = $FILA_UNICA")
    fun observar(): Flow<AjustesConduccionEntity?>

    @Upsert
    suspend fun guardar(ajustes: AjustesConduccionEntity)
}

@Dao
abstract class RecorridosDao {
    @Insert
    abstract suspend fun insertar(recorrido: RecorridoEntity): Long

    @Insert
    abstract suspend fun insertarPuntos(puntos: List<RecorridoPuntoEntity>)

    @Query("SELECT MAX(orden) FROM recorrido_punto WHERE recorridoId = :id")
    abstract suspend fun ultimoOrden(id: Long): Int?

    @Query("SELECT * FROM recorrido WHERE id = :id")
    abstract suspend fun recorrido(id: Long): RecorridoEntity?

    @Query("SELECT * FROM recorrido_punto WHERE recorridoId = :id ORDER BY orden")
    abstract suspend fun puntos(id: Long): List<RecorridoPuntoEntity>

    @Query("SELECT * FROM recorrido WHERE finMs IS NOT NULL ORDER BY inicioMs DESC, id DESC")
    abstract fun observarTerminados(): Flow<List<RecorridoEntity>>

    @Query("UPDATE recorrido SET finMs = :finMs, distanciaM = :distanciaM WHERE id = :id")
    abstract suspend fun cerrar(
        id: Long,
        finMs: Long,
        distanciaM: Double,
    )

    @Query("DELETE FROM recorrido WHERE id = :id")
    abstract suspend fun borrar(id: Long)

    /** Añade los puntos a continuación de los existentes: el `orden` que traigan se ignora y se reasigna. */
    @Transaction
    open suspend fun anadirPuntos(
        id: Long,
        puntos: List<RecorridoPuntoEntity>,
    ) {
        val primero = (ultimoOrden(id) ?: -1) + 1
        insertarPuntos(puntos.mapIndexed { i, p -> p.copy(recorridoId = id, orden = primero + i) })
    }
}
