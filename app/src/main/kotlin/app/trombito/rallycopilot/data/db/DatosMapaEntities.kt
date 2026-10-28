package app.trombito.rallycopilot.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.trombito.rallycopilot.motor.datos.LIMITE_POR_DEFECTO

/** Archivo del paquete de datos ya instalado en `filesDir/mapa/` (un registro por id del manifest). */
@Entity(tableName = "archivo_instalado")
data class ArchivoInstaladoEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    val sha256: String,
    val bytes: Long,
    val version: String,
)

/** Ajustes de Mapas offline: una sola fila (`id` = [FILA_UNICA]). */
@Entity(tableName = "ajustes_mapas")
data class AjustesMapasEntity(
    @PrimaryKey val id: Int = FILA_UNICA,
    val soloWifi: Boolean = true,
    val limiteBytes: Long = LIMITE_POR_DEFECTO,
)

/** Estado de los datos offline: una sola fila (`id` = [FILA_UNICA]). `ultimoError` guarda el nombre de MotivoError. */
@Entity(tableName = "estado_datos")
data class EstadoDatosEntity(
    @PrimaryKey val id: Int = FILA_UNICA,
    val versionInstalada: String? = null,
    val avisoActualizado: String? = null,
    val ultimoError: String? = null,
)

const val FILA_UNICA = 1
