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
interface GuiadoPendienteDao {
    @Query("SELECT * FROM guiado_pendiente WHERE id = $FILA_UNICA")
    fun observar(): Flow<GuiadoPendienteEntity?>

    @Upsert
    suspend fun guardar(guiado: GuiadoPendienteEntity)

    @Query("DELETE FROM guiado_pendiente")
    suspend fun borrar()
}

@Suppress("TooManyFunctions") // recorridos, sus puntos y el factor de calibración que se guarda al cerrarlos
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

    /** Ids de los [limite] recorridos terminados más recientes (el orden de [observarTerminados]). */
    @Query("SELECT id FROM recorrido WHERE finMs IS NOT NULL ORDER BY inicioMs DESC, id DESC LIMIT :limite")
    abstract suspend fun idsTerminadosRecientes(limite: Int): List<Long>

    @Query("SELECT * FROM calibracion WHERE id = $FILA_UNICA")
    abstract suspend fun calibracion(): CalibracionEntity?

    @Upsert
    abstract suspend fun guardarCalibracion(calibracion: CalibracionEntity)

    @Query("SELECT id FROM recorrido WHERE finMs IS NULL ORDER BY id")
    abstract suspend fun idsEnCurso(): List<Long>

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
