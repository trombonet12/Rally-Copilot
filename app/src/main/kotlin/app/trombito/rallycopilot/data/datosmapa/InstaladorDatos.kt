package app.trombito.rallycopilot.data.datosmapa

import app.trombito.rallycopilot.motor.datos.ArchivoDatos
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

/**
 * Gestiona `filesDir/mapa/`: la zona de descarga `.nuevo/` y la instalación atómica. Mientras no estén verificados
 * todos los archivos del plan no se toca ningún archivo instalado, así los datos viejos siguen sirviendo.
 */
class InstaladorDatos(
    private val config: ConfigDatosMapa,
) {
    private val mapa: File get() = config.directorioMapa
    private val nuevo: File get() = config.directorioNuevo
    private val extraido: File get() = File(nuevo, "extraido")

    fun parcial(archivo: ArchivoDatos): File = File(nuevo, "${archivo.nombre}.part")

    /** Archivo ya verificado a la espera de la instalación; lleva el hash en el nombre por si cambia el manifest. */
    fun verificado(archivo: ArchivoDatos): File = File(nuevo, "${archivo.nombre}.${archivo.sha256}.ok")

    fun estaVerificado(archivo: ArchivoDatos): Boolean =
        verificado(archivo).let { it.isFile && it.length() == archivo.bytes }

    fun marcarVerificado(archivo: ArchivoDatos) {
        Files.move(parcial(archivo).toPath(), verificado(archivo).toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    /** Bytes ya presentes en `.nuevo/` para estos archivos (parciales o verificados): espacio ya gastado. */
    fun bytesEnCurso(archivos: List<ArchivoDatos>): Long =
        archivos.sumOf { a ->
            val ok = verificado(a)
            val parte = parcial(a)
            when {
                ok.isFile -> ok.length()
                parte.isFile -> parte.length()
                else -> 0L
            }
        }

    /**
     * Instala los [archivos] (todos ya verificados) y borra los [nombresABorrar]. El zip se descomprime primero en una
     * carpeta temporal: si es inseguro o falla, no se ha tocado nada. Después solo hay renombrados.
     * [nombresDelManifest] son los nombres de todos los archivos del manifest (también los que no se descargan ahora):
     * ninguna entrada de un zip puede tener uno de esos nombres ni los de [archivos].
     */
    fun instalar(
        archivos: List<ArchivoDatos>,
        nombresABorrar: List<String>,
        nombresDelManifest: Collection<String> = emptyList(),
    ) {
        mapa.mkdirs()
        extraido.deleteRecursively()
        val (zips, sueltos) = archivos.partition { it.nombre.endsWith(".zip", ignoreCase = true) }
        val reservados = (nombresDelManifest + archivos.map { it.nombre }).map { normalizar(it) }.toSet()
        zips.forEach {
            comprobarZip(verificado(it), reservados)
            ExtractorZip.extraer(verificado(it), extraido)
        }
        sueltos.forEach { mover(verificado(it), File(mapa, it.nombre)) }
        if (extraido.isDirectory) {
            extraido.walkTopDown().filter { it.isFile }.forEach { mover(it, File(mapa, it.relativeTo(extraido).path)) }
        }
        nombresABorrar.filter(::nombreSeguro).forEach { File(mapa, it).delete() }
        nuevo.deleteRecursively()
    }

    /** Borra todo `mapa/` (instalado y en curso). */
    fun borrarTodo() {
        mapa.deleteRecursively()
    }

    private fun nombreSeguro(nombre: String) =
        nombre.isNotBlank() && '/' !in nombre && '\\' !in nombre && ".." !in nombre

    private fun mover(
        origen: File,
        destino: File,
    ) {
        destino.parentFile?.mkdirs()
        Files.move(origen.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

private const val MAX_ENTRADAS_ZIP = 2000

/** Rechaza un zip con demasiadas entradas o con entradas que pisarían archivos del manifest. */
private fun comprobarZip(
    zip: File,
    reservados: Set<String>,
) {
    var cuenta = 0
    ZipFile(zip).use { zf ->
        for (entrada in zf.entries()) {
            cuenta++
            if (cuenta > MAX_ENTRADAS_ZIP) throw ZipInseguroException("Zip con más de $MAX_ENTRADAS_ZIP entradas")
            if (!entrada.isDirectory && normalizar(entrada.name) in reservados) {
                throw ZipInseguroException("El zip trae un archivo reservado del manifest: ${entrada.name}")
            }
        }
    }
}

/** Ruta sin `.` ni segmentos vacíos y en minúsculas, para comparar nombres como los resuelve el sistema. */
private fun normalizar(nombre: String): String =
    nombre
        .split('/', '\\')
        .filter { it.isNotEmpty() && it != "." }
        .joinToString("/")
        .lowercase()
