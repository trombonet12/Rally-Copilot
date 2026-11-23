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
    // La columna conserva el valor por defecto 1 en SQL (SQLite no lo cambia sin rehacer la tabla); desde la v19 el de
    // dominio es false y la migración apaga la fila existente.
    @ColumnInfo(defaultValue = "1") val avisarCruces: Boolean = false,
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
    // Avisos de puentes y túneles (v19); apagados de base.
    @ColumnInfo(defaultValue = "0") val avisarPuentes: Boolean = false,
    // Intercom del casco (v20): «Audio por el casco» automático (1) y «Escucha con casco» solo al pulsar (0).
    @ColumnInfo(defaultValue = "1") val audioCasco: Boolean = true,
    @ColumnInfo(defaultValue = "0") val escuchaCascoSiempre: Boolean = false,
)

/**
 * Recorrido grabado. `finMs` es null mientras se graba; solo los terminados (>= 1 km) se conservan. Desde la v21,
 * [tiempoPrevistoS] es el tiempo que se previó al salir (null: no se conoce) y [viasTroceadas] marca que el
 * recorrido ya se troceó en tramos de vía (false: pendiente, también para los anteriores a la v21). [llegado] marca
 * el guiado que llegó al destino sin cambiar de destino ni de paradas: solo esos enseñan el factor personal. Desde la
 * v22, [llegadaEstimadaS] es el tiempo total que la app enseñó al salir (ya con el ritmo aprendido de Joan; null: no
 * se guardó), con el que el resumen compara la llegada.
 */
@Entity(tableName = "recorrido")
data class RecorridoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inicioMs: Long,
    val finMs: Long?,
    val distanciaM: Double,
    val destinoNombre: String?,
    val tiempoPrevistoS: Double? = null,
    @ColumnInfo(defaultValue = "0") val viasTroceadas: Boolean = false,
    @ColumnInfo(defaultValue = "0") val llegado: Boolean = false,
    val llegadaEstimadaS: Double? = null,
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
