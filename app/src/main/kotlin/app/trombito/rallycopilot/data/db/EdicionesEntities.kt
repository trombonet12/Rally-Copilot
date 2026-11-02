package app.trombito.rallycopilot.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Edición de notas de un tramo. Con [tramoId], [posicionM] son metros sobre la traza del tramo; sin él es una edición
 * pendiente (dictada sin tramo activo): [posicionM] va sobre la hoja de la conducción y [lat] y [lon] la sitúan para
 * anclarla al tramo que se cree al cerrar la conducción. [accion] es el tipo (ver `CodecEdiciones`), [nota] la nota
 * codificada (null si la acción no lleva) y [marcaTiempo] ms epoch (ordena la aplicación). Se borra con su tramo.
 */
@Entity(
    tableName = "edicion_nota",
    foreignKeys = [
        ForeignKey(
            entity = TramoEntity::class,
            parentColumns = ["id"],
            childColumns = ["tramoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tramoId"])],
)
data class EdicionNotaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tramoId: String?,
    val posicionM: Double,
    val lat: Double?,
    val lon: Double?,
    val accion: String,
    val nota: String?,
    val marcaTiempo: Long,
)

@Dao
interface EdicionesDao {
    @Insert
    suspend fun insertar(edicion: EdicionNotaEntity): Long

    @Update
    suspend fun actualizar(edicion: EdicionNotaEntity)

    @Query("SELECT * FROM edicion_nota WHERE tramoId = :tramoId ORDER BY marcaTiempo, id")
    fun observarDeTramo(tramoId: String): Flow<List<EdicionNotaEntity>>

    @Query("SELECT * FROM edicion_nota WHERE tramoId = :tramoId ORDER BY marcaTiempo, id")
    suspend fun deTramo(tramoId: String): List<EdicionNotaEntity>

    @Query("SELECT * FROM edicion_nota WHERE tramoId IS NULL ORDER BY marcaTiempo, id")
    fun observarPendientes(): Flow<List<EdicionNotaEntity>>

    @Query("SELECT * FROM edicion_nota WHERE tramoId IS NULL ORDER BY marcaTiempo, id")
    suspend fun pendientes(): List<EdicionNotaEntity>

    @Query("DELETE FROM edicion_nota WHERE tramoId IS NULL")
    suspend fun borrarPendientes()

    @Query("DELETE FROM edicion_nota WHERE tramoId = :tramoId")
    suspend fun borrarDeTramo(tramoId: String)
}
