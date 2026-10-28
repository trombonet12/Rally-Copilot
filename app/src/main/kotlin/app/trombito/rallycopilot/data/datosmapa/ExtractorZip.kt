package app.trombito.rallycopilot.data.datosmapa

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** El zip trae una entrada que se saldría de la carpeta de destino (zip-slip) o es desproporcionada. */
class ZipInseguroException(
    mensaje: String,
) : Exception(mensaje)

/** Descomprime un zip rechazando rutas absolutas, `..` (zip-slip), la carpeta `.nuevo` y tamaños desorbitados. */
object ExtractorZip {
    private const val TAMANO_BUFFER = 64 * 1024
    private const val MAX_DESCOMPRIMIDO = 512L * 1024 * 1024

    /** @throws ZipInseguroException si alguna entrada no es segura; puede haber extraído parte antes de fallar. */
    fun extraer(
        zip: File,
        destino: File,
    ) {
        destino.mkdirs()
        val raiz = destino.canonicalFile
        var total = 0L
        ZipFile(zip).use { zf ->
            for (entrada in zf.entries()) {
                total += extraerEntrada(zf, entrada, raiz, MAX_DESCOMPRIMIDO - total)
            }
        }
    }

    private fun extraerEntrada(
        zip: ZipFile,
        entrada: ZipEntry,
        raiz: File,
        presupuesto: Long,
    ): Long {
        val objetivo = destinoSeguro(raiz, entrada.name)
        if (entrada.isDirectory) {
            objetivo.mkdirs()
            return 0
        }
        objetivo.parentFile?.mkdirs()
        return zip.getInputStream(entrada).use { origen ->
            objetivo.outputStream().use { salida -> copiar(origen, salida, presupuesto) }
        }
    }

    private fun destinoSeguro(
        raiz: File,
        nombre: String,
    ): File {
        if (!rutaRelativaSegura(nombre)) throw ZipInseguroException("Entrada no permitida en el zip: $nombre")
        val objetivo = File(raiz, nombre).canonicalFile
        if (!objetivo.path.startsWith(raiz.path + File.separator)) {
            throw ZipInseguroException("Entrada fuera de la carpeta de destino: $nombre")
        }
        return objetivo
    }

    private fun rutaRelativaSegura(nombre: String): Boolean {
        val absoluta = nombre.startsWith("/") || nombre.startsWith("\\")
        if (nombre.isBlank() || absoluta || ':' in nombre) return false
        val segmentos = nombre.split('/', '\\')
        return ".." !in segmentos && segmentos.first() != ConfigDatosMapa.NOMBRE_NUEVO
    }

    private fun copiar(
        origen: InputStream,
        salida: OutputStream,
        presupuesto: Long,
    ): Long {
        val buffer = ByteArray(TAMANO_BUFFER)
        var total = 0L
        while (true) {
            val n = origen.read(buffer)
            if (n < 0) return total
            total += n
            if (total > presupuesto) throw ZipInseguroException("Zip descomprimido demasiado grande")
            salida.write(buffer, 0, n)
        }
    }
}
