package app.trombito.rallycopilot.domain.copia

import java.io.InputStream
import java.io.OutputStream

/** Copia de seguridad de los datos del usuario (tramos, recorridos y ajustes) a un zip y de vuelta. */
interface CopiaDatos {
    /** Escribe el zip en [salida] (sin cerrarla) y devuelve lo que contiene. */
    suspend fun exportar(salida: OutputStream): ResumenCopia

    /**
     * Valida el zip de [entrada] y, si es bueno, sustituye los datos actuales. Tras volver bien la base queda cerrada:
     * hay que reiniciar la app ([ReiniciadorApp]). Si falla, lanza [CopiaInvalidaException] y no toca nada.
     */
    suspend fun importar(entrada: InputStream): ResumenCopia
}

/** Qué lleva una copia: versión del esquema de la base, de la app, fecha y recuentos. */
data class ResumenCopia(
    val esquema: Int,
    val versionName: String,
    val versionCode: Long,
    val fechaMs: Long,
    val recorridos: Int,
    val tramos: Int,
    val pasadas: Int,
)

enum class MotivoCopiaInvalida {
    /** No se pudo leer o escribir el archivo elegido. */
    NO_SE_PUEDE_ABRIR,

    /** No es un zip de Rally Copilot (entradas inesperadas, rutas raras, sin base o sin manifiesto). */
    ARCHIVO_INVALIDO,

    /** Pasa del tamaño máximo admitido. */
    DEMASIADO_GRANDE,

    /** La copia es de una versión más nueva de la app. */
    ESQUEMA_FUTURO,

    /** La base de datos del zip está estropeada. */
    BASE_DANADA,

    /** La base no es de Rally Copilot o no casa con esta versión. */
    INCOMPATIBLE,
}

class CopiaInvalidaException(
    val motivo: MotivoCopiaInvalida,
    mensaje: String = motivo.name,
    causa: Throwable? = null,
) : Exception(mensaje, causa)

/** Reinicia el proceso de la app de forma limpia (tras sustituir la base). */
fun interface ReiniciadorApp {
    fun reiniciar()
}
