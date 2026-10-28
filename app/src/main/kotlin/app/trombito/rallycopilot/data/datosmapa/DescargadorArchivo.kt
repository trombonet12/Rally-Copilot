package app.trombito.rallycopilot.data.datosmapa

import app.trombito.rallycopilot.motor.datos.ArchivoDatos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.security.MessageDigest

/** El archivo descargado no coincide con el SHA-256 (o el tamaño) del manifest. El `.part` ya se ha borrado. */
class HashIncorrectoException(
    mensaje: String,
) : Exception(mensaje)

/**
 * Descarga un archivo a un `.part`, reanudable con `Range`, y lo verifica (bytes y SHA-256). Un corte de red lanza
 * [IOException] y conserva el `.part`; un hash incorrecto lo borra y lanza [HashIncorrectoException].
 */
class DescargadorArchivo(
    private val http: ClienteHttp,
) {
    /** [alProgreso] recibe los bytes que ya tiene el `.part` (incluido lo que ya había antes de reanudar). */
    suspend fun descargar(
        archivo: ArchivoDatos,
        parcial: File,
        alProgreso: suspend (Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        parcial.parentFile?.mkdirs()
        var primerIntento = true
        while (true) {
            var inicio = if (parcial.isFile) parcial.length() else 0L
            if (inicio > archivo.bytes) {
                parcial.delete()
                inicio = 0
            }
            if (inicio < archivo.bytes) {
                inicio = transferir(archivo, parcial, inicio, alProgreso)
            } else {
                alProgreso(inicio)
            }
            val tamano = parcial.length()
            if (tamano < archivo.bytes) {
                throw IOException("Descarga incompleta de ${archivo.nombre}: $tamano de ${archivo.bytes}")
            }
            if (tamano == archivo.bytes && sha256(parcial) == archivo.sha256) return@withContext
            parcial.delete()
            // Un .part viejo de otra versión del archivo da un hash malo al reanudar: se reintenta una vez desde cero.
            if (inicio == 0L || !primerIntento) {
                throw HashIncorrectoException("SHA-256 incorrecto en ${archivo.nombre}")
            }
            primerIntento = false
        }
    }

    /** Descarga desde [desde] (o desde cero si el servidor ignora el Range). Devuelve el offset real de partida. */
    private suspend fun transferir(
        archivo: ArchivoDatos,
        parcial: File,
        desde: Long,
        alProgreso: suspend (Long) -> Unit,
    ): Long {
        val con = http.abrir(archivo.url, desde)
        try {
            val inicio = inicioDe(con, desde, archivo.nombre)
            if (inicio == null) {
                // El .part no encaja con lo que ofrece el servidor: empezar de nuevo.
                parcial.delete()
                con.disconnect()
                return transferir(archivo, parcial, 0, alProgreso)
            }
            alProgreso(inicio)
            con.inputStream.use { entrada ->
                FileOutputStream(parcial, inicio > 0).use { salida -> volcar(entrada, salida, inicio, alProgreso) }
            }
            return inicio
        } finally {
            con.disconnect()
        }
    }

    /** Offset desde el que se escribe según la respuesta, o null si el Range no sirve y hay que empezar de cero. */
    private fun inicioDe(
        con: HttpURLConnection,
        desde: Long,
        nombre: String,
    ): Long? {
        val codigo = con.responseCode
        val es206 = codigo == ClienteHttp.HTTP_PARCIAL
        return when {
            es206 && desde > 0 && rangoCoincide(con.getHeaderField("Content-Range"), desde) -> desde
            codigo == ClienteHttp.HTTP_OK -> 0L
            desde > 0 && (codigo == ClienteHttp.HTTP_RANGO_NO_SATISFACIBLE || es206) -> null
            else -> throw IOException("HTTP $codigo descargando $nombre")
        }
    }

    private suspend fun volcar(
        entrada: InputStream,
        salida: OutputStream,
        inicio: Long,
        alProgreso: suspend (Long) -> Unit,
    ) {
        var hechos = inicio
        val buffer = ByteArray(TAMANO_BUFFER)
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = entrada.read(buffer)
            if (n < 0) return
            salida.write(buffer, 0, n)
            hechos += n
            alProgreso(hechos)
        }
    }

    private fun rangoCoincide(
        contentRange: String?,
        desde: Long,
    ): Boolean = contentRange != null && contentRange.trim().startsWith("bytes $desde-")

    companion object {
        private const val TAMANO_BUFFER = 64 * 1024

        fun sha256(archivo: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            archivo.inputStream().use { entrada ->
                val buffer = ByteArray(TAMANO_BUFFER)
                while (true) {
                    val n = entrada.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
