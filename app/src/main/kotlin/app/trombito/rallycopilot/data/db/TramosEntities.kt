package app.trombito.rallycopilot.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tramo cronometrable. [traza] es una polilínea codificada (precisión 1e-6), [sectores] y [microsectores] los cortes en
 * metros separados por comas (vacío si no hay) y [creadoEn] milisegundos epoch. [nombre] es el que pone el usuario y
 * [nombreAutomatico] el que se compone al crearlo; [ultimoUso] (epoch) es la salida de la última pasada.
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
    val nombreAutomatico: String? = null,
    @ColumnInfo(defaultValue = "0") val favorito: Boolean = false,
    @ColumnInfo(defaultValue = "0") val ultimoUso: Long = 0,
)

/**
 * Pasada completa de un tramo; se borra en cascada con él. [inicio] es el instante epoch de la salida y [marcas] la
 * curva distancia-tiempo codificada (ver `CodecTramos`). [recorridoId] es la conducción que la grabó (null si se
 * borró o no se conserva) y [puntosDesdeMs]..[puntosHastaMs] el intervalo, en el reloj de los puntos de ese recorrido
 * (`recorrido_punto.tiempoMs`, no epoch), que ocupa la pasada: sirve para recortar sus puntos.
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
        ForeignKey(
            entity = RecorridoEntity::class,
            parentColumns = ["id"],
            childColumns = ["recorridoId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["tramoId", "totalMs"]), Index(value = ["recorridoId"])],
)
data class PasadaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tramoId: String,
    val inicio: Long,
    val totalMs: Long,
    val marcas: String,
    val recorridoId: Long? = null,
    val puntosDesdeMs: Long? = null,
    val puntosHastaMs: Long? = null,
)

/**
 * Resultado de un trozo cronometrado de un tramo guardado durante una conducción (subtramo). [desdeM]..[hastaM] son
 * metros sobre la traza del tramo de referencia [tramoRefId] y [longitudM] su longitud; [inicio] es la entrada (epoch
 * ms), [tiempoMs] lo que se tardó y [mejorMs] el mejor tiempo previo en ese trozo (null si era el primero). Las cuatro
 * coordenadas son los extremos del trozo. Se borra en cascada con su recorrido y con su tramo de referencia.
 */
@Entity(
    tableName = "subtramo",
    foreignKeys = [
        ForeignKey(
            entity = RecorridoEntity::class,
            parentColumns = ["id"],
            childColumns = ["recorridoId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TramoEntity::class,
            parentColumns = ["id"],
            childColumns = ["tramoRefId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["recorridoId"]), Index(value = ["tramoRefId"])],
)
data class SubtramoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recorridoId: Long?,
    val tramoRefId: String,
    val desdeM: Double,
    val hastaM: Double,
    val longitudM: Double,
    val inicio: Long,
    val tiempoMs: Long,
    val mejorMs: Long?,
    val latDesde: Double,
    val lonDesde: Double,
    val latHasta: Double,
    val lonHasta: Double,
)

/** Una pasada reducida a lo que necesita el detector de subtramos: su tramo, su tiempo total y su curva (consulta). */
data class FilaMarcasPasada(
    val tramoId: String,
    val totalMs: Long,
    val marcas: String,
)

/** Fila de la lista de tramos: el tramo con el resumen de sus pasadas (consulta, no tabla). */
data class FilaResumenTramo(
    val id: String,
    val nombre: String?,
    val nombreAutomatico: String?,
    val longitudM: Double,
    val favorito: Boolean,
    val ultimoUso: Long,
    val pasadas: Int,
    val mejorMs: Long?,
)

/** Punto de un recorrido (consulta de recorte, sin el id del recorrido). */
data class FilaPuntoPasada(
    val lat: Double,
    val lon: Double,
    val tiempoMs: Long,
    val velocidadMs: Double?,
)

/** Totales de los recorridos terminados (consulta, no tabla). */
data class TotalesRecorridos(
    val recorridos: Int,
    val distanciaM: Double,
    val primeroMs: Long?,
    val ultimoMs: Long?,
)

/** Cuántos tramos y pasadas hay (consulta, no tabla). */
data class ContadoresTramos(
    val tramos: Int,
    val pasadas: Int,
)
