package app.trombito.rallycopilot.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tramo cronometrable. [traza] es una polilínea codificada (precisión 1e-6), [sectores] y [microsectores] los cortes en
 * metros separados por comas (vacío si no hay) y [creadoEn] milisegundos epoch.
 */
@Entity(tableName = "tramo")
data class TramoEntity(
    @PrimaryKey val id: String,
    val nombre: String?,
    val traza: String,
    val longitudM: Double,
    val sectores: String,
    val microsectores: String,
    val creadoEn: Long,
)

/**
 * Pasada completa de un tramo; se borra en cascada con él. [inicio] es el instante epoch de la salida y [marcas] la
 * curva distancia-tiempo codificada (ver `CodecTramos`).
 */
@Entity(
    tableName = "pasada",
    foreignKeys = [
        ForeignKey(
            entity = TramoEntity::class,
            parentColumns = ["id"],
            childColumns = ["tramoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tramoId", "totalMs"])],
)
data class PasadaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tramoId: String,
    val inicio: Long,
    val totalMs: Long,
    val marcas: String,
)

/** Resumen de las pasadas de un tramo (consulta, no tabla). */
data class ResumenPasadas(
    val tramoId: String,
    val pasadas: Int,
    val mejorMs: Long,
)
