package app.trombito.rallycopilot.domain.datos

import app.trombito.rallycopilot.motor.datos.LIMITE_POR_DEFECTO
import kotlinx.coroutines.flow.Flow

/** Capa base del mapa. */
enum class CapaMapa { VECTORIAL, SATELITE }

/** Ajustes de Mapas offline. Por defecto, solo WiFi y 2 GB (decisión de Joan). */
data class AjustesMapas(
    val soloWifi: Boolean = true,
    val limiteBytes: Long = LIMITE_POR_DEFECTO,
)

data class DatosInstalados(
    val version: String,
    val bytes: Long,
    val conSatelite: Boolean,
)

data class ProgresoDescarga(
    val bytesHechos: Long,
    val bytesTotales: Long,
    /** La descarga espera red (o WiFi si «solo WiFi» está activado). */
    val esperandoRed: Boolean,
)

enum class MotivoError { SIN_RED, SUPERA_LIMITE, SIN_ESPACIO, HASH_INCORRECTO, MANIFEST_INVALIDO, DESCONOCIDO }

/**
 * Estado de los datos offline. Los instalados siguen sirviendo mientras se descarga una versión nueva: la descarga
 * se escribe aparte y solo sustituye a los archivos viejos cuando todos están verificados.
 */
data class EstadoDatosMapa(
    val instalados: DatosInstalados? = null,
    val descarga: ProgresoDescarga? = null,
    val error: MotivoError? = null,
    /** Versión recién instalada por una actualización automática, hasta que el usuario cierra el aviso. */
    val avisoActualizado: String? = null,
)

/** Estilos MapLibre listos para `Style.Builder().fromJson`, con las rutas locales ya resueltas. */
data class EstilosMapa(
    val vectorial: String,
    /** Null si el satélite no está instalado. */
    val satelite: String?,
)

/** Paquete de datos de Mallorca (repo `rally-copilot-data`): comprobación, descarga, verificación y borrado. */
interface RepositorioDatosMapa {
    val estado: Flow<EstadoDatosMapa>
    val ajustes: Flow<AjustesMapas>

    suspend fun guardarAjustes(ajustes: AjustesMapas)

    /** Al abrir la app: si hay red, lee el manifest y, si hay datos nuevos, encola su descarga. */
    fun comprobarActualizacion()

    /** Descarga (o reanuda) el paquete completo; la pide el usuario desde Mapas offline. */
    fun descargar()

    fun cancelar()

    suspend fun borrar()

    suspend fun descartarAviso()

    /** Null si no hay datos instalados. */
    suspend fun estilos(): EstilosMapa?
}
