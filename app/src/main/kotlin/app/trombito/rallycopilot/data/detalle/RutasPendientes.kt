package app.trombito.rallycopilot.data.detalle

import app.trombito.rallycopilot.motor.precarga.Tesela
import app.trombito.rallycopilot.motor.precarga.TeselasPrecarga
import java.io.File
import java.io.IOException

/**
 * Corredores de rutas planificadas que aún no se han bajado, uno por archivo en [carpeta]: las teselas z18 ya
 * calculadas (`x,y` separadas por espacios). La descarga automática los consume y los borra al terminar; si se corta,
 * siguen ahí. Se guardan como máximo [MAX_ARCHIVOS] (los más viejos se descartan).
 */
class RutasPendientes(
    private val carpeta: File,
) {
    fun guardar(teselas: Collection<Tesela>): File? {
        if (teselas.isEmpty()) return null
        return try {
            carpeta.mkdirs()
            val archivo = File(carpeta, "ruta-${System.nanoTime()}.txt")
            archivo.writeText(teselas.joinToString(" ") { "${it.x},${it.y}" })
            recortar()
            archivo
        } catch (_: IOException) {
            null
        }
    }

    /** Pares (archivo, teselas) pendientes; un archivo ilegible se borra. */
    fun leer(): List<Pair<File, Set<Tesela>>> =
        archivos().mapNotNull { f ->
            val teselas = parsear(f)
            if (teselas == null) f.delete()
            teselas?.let { f to it }
        }

    fun borrar(archivo: File) {
        archivo.delete()
    }

    fun borrarTodas() {
        carpeta.deleteRecursively()
    }

    private fun archivos(): List<File> =
        carpeta.listFiles { f -> f.isFile && f.name.startsWith("ruta-") }?.sortedBy { it.name } ?: emptyList()

    private fun recortar() {
        archivos().dropLast(MAX_ARCHIVOS).forEach { it.delete() }
    }

    private fun parsear(archivo: File): Set<Tesela>? =
        try {
            archivo
                .readText()
                .split(' ')
                .filter { it.isNotEmpty() }
                .mapTo(HashSet()) { par ->
                    val (x, y) = par.split(',').map { it.toInt() }
                    require(x >= 0 && y >= 0)
                    Tesela(TeselasPrecarga.ZOOM_CORREDOR, x, y)
                }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IndexOutOfBoundsException) {
            null
        }

    companion object {
        const val MAX_ARCHIVOS = 20
    }
}
