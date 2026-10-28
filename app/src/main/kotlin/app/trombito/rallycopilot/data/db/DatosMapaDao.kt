package app.trombito.rallycopilot.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Dao
interface AjustesMapasDao {
    @Query("SELECT * FROM ajustes_mapas WHERE id = $FILA_UNICA")
    fun observarAjustes(): Flow<AjustesMapasEntity?>

    @Query("SELECT * FROM ajustes_mapas WHERE id = $FILA_UNICA")
    suspend fun ajustes(): AjustesMapasEntity?

    @Upsert
    suspend fun guardarAjustes(ajustes: AjustesMapasEntity)
}

@Dao
abstract class DatosMapaDao {
    @Query("SELECT * FROM archivo_instalado ORDER BY id")
    abstract fun observarArchivos(): Flow<List<ArchivoInstaladoEntity>>

    @Query("SELECT * FROM estado_datos WHERE id = $FILA_UNICA")
    abstract fun observarEstado(): Flow<EstadoDatosEntity?>

    @Upsert
    abstract suspend fun guardarEstado(estado: EstadoDatosEntity)

    @Upsert
    abstract suspend fun guardarArchivos(archivos: List<ArchivoInstaladoEntity>)

    @Query("DELETE FROM archivo_instalado WHERE id IN (:ids)")
    abstract suspend fun borrarArchivos(ids: List<String>)

    @Transaction
    open suspend fun establecerError(motivo: String?) {
        val actual = estado() ?: EstadoDatosEntity()
        guardarEstado(actual.copy(ultimoError = motivo))
    }

    @Transaction
    open suspend fun establecerAviso(version: String?) {
        val actual = estado() ?: EstadoDatosEntity()
        guardarEstado(actual.copy(avisoActualizado = version))
    }

    /** Registra una instalación completa de una vez: archivos nuevos, ids borrados, versión y (si procede) aviso. */
    @Transaction
    open suspend fun registrarInstalacion(
        archivos: List<ArchivoInstaladoEntity>,
        idsBorrados: List<String>,
        version: String,
        avisar: Boolean,
    ) {
        if (idsBorrados.isNotEmpty()) borrarArchivos(idsBorrados)
        if (archivos.isNotEmpty()) guardarArchivos(archivos)
        val actual = estado() ?: EstadoDatosEntity()
        guardarEstado(
            actual.copy(
                versionInstalada = version,
                ultimoError = null,
                avisoActualizado = if (avisar) version else actual.avisoActualizado,
            ),
        )
    }

    /** Olvida todo lo instalado (los ajustes se conservan). */
    @Transaction
    open suspend fun borrarInstalacion() {
        borrarArchivos(archivos().map { it.id })
        guardarEstado(EstadoDatosEntity())
    }
}

/** Lectura puntual de los archivos instalados. */
suspend fun DatosMapaDao.archivos(): List<ArchivoInstaladoEntity> = observarArchivos().first()

/** Lectura puntual del estado (null si todavía no se ha escrito). */
suspend fun DatosMapaDao.estado(): EstadoDatosEntity? = observarEstado().first()
