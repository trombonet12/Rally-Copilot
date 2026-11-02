package app.trombito.rallycopilot.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Marca dejada con la orden de voz «marca aquí». [posicionM] son metros sobre la traza del tramo activo (o sobre la
 * hoja de notas si no hay tramo); [tramoId] es el tramo activo (o null); [lat] y [lon] son la coordenada si se conoce.
 */
@Entity(tableName = "marca")
data class MarcaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val posicionM: Double,
    val tiempoMs: Long,
    val tramoId: String?,
    val lat: Double?,
    val lon: Double?,
)

@Dao
interface MarcasDao {
    @Insert
    suspend fun insertar(marca: MarcaEntity): Long

    @Query("SELECT * FROM marca ORDER BY tiempoMs, id")
    suspend fun todas(): List<MarcaEntity>

    @Query("SELECT * FROM marca WHERE tramoId = :tramoId ORDER BY posicionM, id")
    suspend fun deTramo(tramoId: String): List<MarcaEntity>
}
