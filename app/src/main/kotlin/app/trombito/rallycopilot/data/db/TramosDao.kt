package app.trombito.rallycopilot.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Resultados de los subtramos cronometrados durante las conducciones. */
@Dao
interface SubtramosDao {
    @Insert
    suspend fun insertar(subtramo: SubtramoEntity): Long

    @Query("SELECT * FROM subtramo WHERE recorridoId = :recorridoId ORDER BY inicio, id")
    fun observarDeRecorrido(recorridoId: Long): Flow<List<SubtramoEntity>>

    /** Los subtramos de un tramo de referencia, el más reciente primero. */
    @Query("SELECT * FROM subtramo WHERE tramoRefId = :tramoId ORDER BY inicio DESC, id DESC")
    fun observarDeTramo(tramoId: String): Flow<List<SubtramoEntity>>

    /**
     * Los subtramos aún sin situar en una carretera y sin intentarlo (los anteriores a la v21 y los recién guardados).
     * Uno intentado sin éxito queda con `sentido = DESCONOCIDO` y sin `viaRef` ([marcarNoSituable]): no se reintenta.
     */
    @Query("SELECT * FROM subtramo WHERE viaRef IS NULL AND sentido IS NULL ORDER BY id")
    suspend fun sinVia(): List<SubtramoEntity>

    /** Marca un subtramo sin carretera como intentado sin éxito (sin cambiar el esquema); devuelve filas tocadas. */
    @Query(
        "UPDATE subtramo SET sentido = 'DESCONOCIDO' WHERE id = :id AND viaRef IS NULL AND sentido IS NULL",
    )
    suspend fun marcarNoSituable(id: Long): Int

    /** Sitúa el subtramo en su carretera; devuelve las filas tocadas. */
    @Query(
        "UPDATE subtramo SET viaRef = :viaRef, pkDesde = :pkDesde, pkHasta = :pkHasta, sentido = :sentido " +
            "WHERE id = :id",
    )
    suspend fun situarEnVia(
        id: Long,
        viaRef: String,
        pkDesde: Double?,
        pkHasta: Double?,
        sentido: String,
    ): Int
}

@Suppress("TooManyFunctions") // un DAO por agregado: tramos, pasadas, mejores y la transacción de crear
@Dao
abstract class TramosDao {
    @Insert
    abstract suspend fun insertarTramo(tramo: TramoEntity)

    @Insert
    abstract suspend fun insertarPasada(pasada: PasadaEntity): Long

    @Query("SELECT * FROM tramo ORDER BY creadoEn, id")
    abstract suspend fun tramos(): List<TramoEntity>

    @Query("SELECT * FROM tramo WHERE id = :id")
    abstract suspend fun tramo(id: String): TramoEntity?

    /** Los tramos de un [tipo] (`VIAJE` o `VIA`), por orden de creación. */
    @Query("SELECT * FROM tramo WHERE tipo = :tipo ORDER BY creadoEn, id")
    abstract suspend fun tramosPorTipo(tipo: String): List<TramoEntity>

    /** Los tramos de vía de la carretera [viaRef] recorridos en [sentido], por punto kilométrico de salida. */
    @Query(
        "SELECT * FROM tramo WHERE tipo = 'VIA' AND viaRef = :viaRef AND sentido = :sentido " +
            "ORDER BY pkDesde, creadoEn, id",
    )
    abstract suspend fun tramosViaDe(
        viaRef: String,
        sentido: String,
    ): List<TramoEntity>

    /** Los tramos de vía que aún no saben hacia dónde van (los anteriores a H31), por orden de creación. */
    @Query("SELECT * FROM tramo WHERE tipo = 'VIA' AND destino IS NULL ORDER BY creadoEn, id")
    abstract suspend fun tramosViaSinDestino(): List<TramoEntity>

    /**
     * Pone el [destino] y el nombre automático de un tramo de vía que no tenía destino. No toca `nombre` (el que
     * puso el usuario); devuelve las filas tocadas.
     */
    @Query(
        "UPDATE tramo SET destino = :destino, nombreAutomatico = :nombreAutomatico " +
            "WHERE id = :id AND tipo = 'VIA' AND destino IS NULL",
    )
    abstract suspend fun ponerDestinoVia(
        id: String,
        destino: String,
        nombreAutomatico: String?,
    ): Int

    /** Los tramos de vía de la carretera [viaRef] con cualquier sentido, por orden de creación. */
    @Query("SELECT * FROM tramo WHERE tipo = 'VIA' AND viaRef = :viaRef ORDER BY creadoEn, id")
    abstract suspend fun tramosViaDeCualquierSentido(viaRef: String): List<TramoEntity>

    @Query("SELECT * FROM pasada WHERE tramoId = :tramoId ORDER BY totalMs, id LIMIT 1")
    abstract suspend fun mejorPasada(tramoId: String): PasadaEntity?

    @Query("SELECT * FROM pasada WHERE tramoId = :tramoId ORDER BY totalMs, id LIMIT 1")
    abstract fun observarMejorPasada(tramoId: String): Flow<PasadaEntity?>

    @Query(
        "SELECT p.* FROM pasada p WHERE p.id = " +
            "(SELECT q.id FROM pasada q WHERE q.tramoId = p.tramoId ORDER BY q.totalMs, q.id LIMIT 1)",
    )
    abstract suspend fun mejoresPasadas(): List<PasadaEntity>

    @Query(
        "SELECT t.id, t.nombre, t.nombreAutomatico, t.longitudM, t.favorito, t.ultimoUso, " +
            "t.tipo, COUNT(p.id) AS pasadas, MIN(p.totalMs) AS mejorMs, CAST(AVG(p.totalMs) AS INTEGER) AS mediaMs, " +
            "t.viaRef, t.pkDesde, t.pkHasta, t.sentido, t.destino, t.recorridoOrigenId " +
            "FROM tramo t LEFT JOIN pasada p ON p.tramoId = t.id " +
            "GROUP BY t.id ORDER BY t.ultimoUso DESC, t.creadoEn DESC, t.id",
    )
    abstract fun observarFilasResumen(): Flow<List<FilaResumenTramo>>

    @Query("SELECT * FROM pasada WHERE tramoId = :tramoId ORDER BY inicio DESC, id DESC")
    abstract suspend fun pasadas(tramoId: String): List<PasadaEntity>

    /** Todas las pasadas (tramo, tiempo total y marcas) de golpe, para el detector de subtramos. */
    @Query("SELECT tramoId, totalMs, marcas FROM pasada ORDER BY tramoId, totalMs, id")
    abstract suspend fun todasLasMarcasDePasadas(): List<FilaMarcasPasada>

    /** Puntos del recorrido dentro del intervalo (inclusive) del reloj de los puntos, en orden. */
    @Query(
        "SELECT lat, lon, tiempoMs, velocidadMs FROM recorrido_punto " +
            "WHERE recorridoId = :recorridoId AND tiempoMs BETWEEN :desdeMs AND :hastaMs ORDER BY orden",
    )
    abstract suspend fun puntosEntre(
        recorridoId: Long,
        desdeMs: Long,
        hastaMs: Long,
    ): List<FilaPuntoPasada>

    @Query(
        "SELECT COUNT(*) AS recorridos, COALESCE(SUM(distanciaM), 0.0) AS distanciaM, MIN(inicioMs) AS primeroMs, " +
            "MAX(inicioMs) AS ultimoMs FROM recorrido WHERE finMs IS NOT NULL",
    )
    abstract fun observarTotalesRecorridos(): Flow<TotalesRecorridos>

    @Query("SELECT (SELECT COUNT(*) FROM tramo) AS tramos, (SELECT COUNT(*) FROM pasada) AS pasadas")
    abstract fun observarContadores(): Flow<ContadoresTramos>

    /** Pone [nombre] (null: vuelve al automático); devuelve las filas tocadas. */
    @Query("UPDATE tramo SET nombre = :nombre WHERE id = :id")
    abstract suspend fun renombrar(
        id: String,
        nombre: String?,
    ): Int

    @Query("UPDATE tramo SET sectores = :sectores, microsectores = :microsectores WHERE id = :id")
    abstract suspend fun guardarCortes(
        id: String,
        sectores: String,
        microsectores: String,
    ): Int

    @Query("UPDATE tramo SET favorito = :favorito WHERE id = :id")
    abstract suspend fun marcarFavorito(
        id: String,
        favorito: Boolean,
    ): Int

    /** El último uso solo avanza. */
    @Query("UPDATE tramo SET ultimoUso = MAX(ultimoUso, :ms) WHERE id = :id")
    abstract suspend fun registrarUso(
        id: String,
        ms: Long,
    )

    @Query("DELETE FROM tramo WHERE id = :id")
    abstract suspend fun borrar(id: String)

    @Transaction
    open suspend fun crear(
        tramo: TramoEntity,
        primera: PasadaEntity,
    ): Long {
        insertarTramo(tramo)
        return anadirPasada(primera.copy(tramoId = tramo.id))
    }

    /** Guarda la pasada y adelanta el último uso de su tramo a su salida. */
    @Transaction
    open suspend fun anadirPasada(pasada: PasadaEntity): Long {
        val id = insertarPasada(pasada)
        registrarUso(pasada.tramoId, pasada.inicio)
        return id
    }
}
