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
    @ColumnInfo(defaultValue = "'NORMAL'") val anticipacion: String = "NORMAL",
    @ColumnInfo(defaultValue = "0") val notasSimplificadas: Boolean = false,
    @ColumnInfo(defaultValue = "'COMPLETO'") val modoCopiloto: String = "COMPLETO",
    val voz: String? = null,
    // El valor por defecto de la columna sigue en 0 (SQLite no lo cambia sin rehacer la tabla); el de dominio es true.
    @ColumnInfo(defaultValue = "0") val activarConCopiloto: Boolean = true,
    @ColumnInfo(defaultValue = "100") val volumenVoz: Int = 100,
    @ColumnInfo(defaultValue = "'OSCURO'") val tema: String = "OSCURO",
    @ColumnInfo(defaultValue = "1") val cronometrar: Boolean = true,
    @ColumnInfo(defaultValue = "0") val notasGrandes: Boolean = false,
    @ColumnInfo(defaultValue = "1") val avisarCruces: Boolean = true,
    @ColumnInfo(defaultValue = "1") val avisarSalidasAutopista: Boolean = true,
    @ColumnInfo(defaultValue = "1") val avisarRotondas: Boolean = true,
    @ColumnInfo(defaultValue = "0") val salidaManual: Boolean = false,
    // Casa («volver a casa»): las dos columnas a la vez o ninguna; sin valor por defecto en SQL (nulas).
    val casaLat: Double? = null,
    val casaLon: Double? = null,
    // Familias de avisos que se dicen por voz y velocidad de habla (v18); por defecto, todo encendido y normal.
    @ColumnInfo(defaultValue = "1") val avisarRadares: Boolean = true,
    @ColumnInfo(defaultValue = "1") val avisarIncidencias: Boolean = true,
    @ColumnInfo(defaultValue = "1") val avisarVelocidad: Boolean = true,
    @ColumnInfo(defaultValue = "'NORMAL'") val velocidadHabla: String = "NORMAL",
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

/** Guiado en curso, para ofrecer «Seguir a <destino>» si el proceso muere. Una sola fila (`id` = [FILA_UNICA]). */
@Entity(tableName = "guiado_pendiente")
data class GuiadoPendienteEntity(
    @PrimaryKey val id: Int = FILA_UNICA,
    val destinoNombre: String,
    val lat: Double,
    val lon: Double,
    val perfil: String,
    val desdeMs: Long,
    /** Paradas intermedias pendientes en JSON (`[{"n":..,"lat":..,"lon":..}]`); vacío si no hay. */
    @ColumnInfo(defaultValue = "''") val paradas: String = "",
)
