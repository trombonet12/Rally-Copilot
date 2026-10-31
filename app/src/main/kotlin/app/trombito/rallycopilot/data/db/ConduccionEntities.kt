package app.trombito.rallycopilot.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Ajustes de conducción: una sola fila (`id` = [FILA_UNICA]). Los enums se guardan por nombre. */
@Entity(tableName = "ajustes_conduccion")
data class AjustesConduccionEntity(
    @PrimaryKey val id: Int = FILA_UNICA,
    val nivelZoom: Int,
    val icono: String,
    val orientacion: String,
    val capa: String,
    val relieve: Boolean,
    @ColumnInfo(defaultValue = "'PLACA'") val estiloManiobras: String = "PLACA",
)

/** Recorrido grabado. `finMs` es null mientras se graba; solo los terminados (>= 1 km) se conservan. */
@Entity(tableName = "recorrido")
data class RecorridoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inicioMs: Long,
    val finMs: Long?,
    val distanciaM: Double,
    val destinoNombre: String?,
)

/** Punto de un recorrido; `orden` empieza en 0. Se borra en cascada con su recorrido. */
@Entity(
    tableName = "recorrido_punto",
    primaryKeys = ["recorridoId", "orden"],
    foreignKeys = [
        ForeignKey(
            entity = RecorridoEntity::class,
            parentColumns = ["id"],
            childColumns = ["recorridoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RecorridoPuntoEntity(
    val recorridoId: Long,
    val orden: Int,
    val lat: Double,
    val lon: Double,
    val tiempoMs: Long,
    val velocidadMs: Double?,
)
