// Descargas verificadas y assets generados (voz, reconocimiento y datos de mapas). Se aplica desde app/build.gradle.kts
// con `apply(from = rootProject.file("gradle/descargas.gradle.kts"))`; el SHA-256 de sherpa-onnx y la etiqueta de los
// datos viven en app/datos-incluidos.properties. Fuera de app/ porque lint falla al analizar un *.gradle.kts bajo el módulo.
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import javax.inject.Inject

// Descargas verificadas (URL + SHA-256 fijo) de lo que no va en el Version Catalog ni en git:
//  - Caché en .descargas/ (raíz del repo, sin versionar): si el archivo está y su hash coincide, no hay red.
//  - Si el hash no coincide, el build falla (el archivo descargado se descarta).
//  - Genera: la dependencia sherpa-onnx (AAR) y los assets de voz (voz/**) y de datos incluidos (datos/**).
object Descargas {
    const val REPO_DATOS = "https://github.com/trombonet12/rally-copilot-data/releases/download/"

    fun sha256(archivo: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        archivo.inputStream().use { entrada ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val leidos = entrada.read(buffer)
                if (leidos < 0) break
                digest.update(buffer, 0, leidos)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Devuelve [destino] con el contenido verificado: de la caché si el hash coincide, o descargándolo. */
    fun asegurar(
        url: String,
        sha256: String,
        destino: File,
    ): File {
        require(Regex("[0-9a-f]{64}").matches(sha256)) { "SHA-256 mal formado para $url" }
        if (destino.isFile) {
            if (sha256(destino) == sha256) return destino
            destino.delete()
        }
        destino.parentFile.mkdirs()
        val parcial = File(destino.parentFile, destino.name + ".parte")
        for (intento in 1..3) {
            try {
                descargar(url, parcial)
                break
            } catch (e: IOException) {
                parcial.delete()
                if (intento == 3) throw GradleException("No se pudo descargar $url: ${e.message}", e)
            }
        }
        val obtenido = sha256(parcial)
        if (obtenido != sha256) {
            parcial.delete()
            throw GradleException("SHA-256 incorrecto para $url: esperado $sha256, obtenido $obtenido")
        }
        Files.move(parcial.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return destino
    }

    private fun descargar(
        url: String,
        destino: File,
    ) {
        val conexion = URI(url).toURL().openConnection() as HttpURLConnection
        conexion.connectTimeout = 30_000
        conexion.readTimeout = 60_000
        conexion.instanceFollowRedirects = true
        try {
            val codigo = conexion.responseCode
            if (codigo != 200) throw IOException("HTTP $codigo")
            conexion.inputStream.use { entrada ->
                destino.outputStream().use { salida -> entrada.copyTo(salida) }
            }
        } finally {
            conexion.disconnect()
        }
    }
}

/** Descarga un archivo a la caché verificando su SHA-256. */
abstract class DescargaVerificada : DefaultTask() {
    @get:Input abstract val url: Property<String>

    @get:Input abstract val sha256: Property<String>

    @get:OutputFile abstract val archivo: RegularFileProperty

    @TaskAction
    fun descargar() {
        Descargas.asegurar(url.get(), sha256.get(), archivo.get().asFile)
    }
}

/**
 * Voz: extrae del tar.bz2 de Dave el contrato voz/davefx y voz/espeak-ng-data (recortada a lo que usa es_ES: los
 * diccionarios de los demás idiomas, 17 de los 17,2 MiB, no se empaquetan; se conserva `en_dict` por si una palabra
 * inglesa cambia de idioma). Al tocar el filtro, subir CopiaEspeak.VERSION. La voz Sharvard NO va en el APK: se baja de
 * la release de datos al elegirla (ids `voz-sharvard*`, ver `Complemento` del motor).
 */
abstract class GenerarAssetsVoz : DefaultTask() {
    @get:InputFile abstract val davefx: RegularFileProperty

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @get:Inject abstract val archivos: ArchiveOperations

    @get:Inject abstract val sistema: FileSystemOperations

    @TaskAction
    fun generar() {
        val destino = outputDir.get().asFile
        sistema.delete { delete(destino) }
        sistema.copy {
            into(destino)
            includeEmptyDirs = false
            from(archivos.tarTree(davefx.get().asFile)) {
                include("*/es_ES-davefx-medium.onnx", "*/tokens.txt")
                eachFile { path = "voz/davefx/" + if (name.endsWith(".onnx")) "model.onnx" else name }
            }
            from(archivos.tarTree(davefx.get().asFile)) {
                include("*/espeak-ng-data/**")
                exclude { elemento ->
                    val nombre = elemento.file.name
                    nombre.endsWith("_dict") && nombre != "es_dict" && nombre != "en_dict"
                }
                eachFile { path = "voz/" + path.substringAfter('/') }
            }
        }
        val archivosVoz = listOf("model.onnx", "tokens.txt").map { File(destino, "voz/davefx/$it") }
        check(archivosVoz.all { it.isFile } && File(destino, "voz/espeak-ng-data").isDirectory) {
            "La voz extraída no cumple el contrato voz/davefx/{model.onnx,tokens.txt} y voz/espeak-ng-data"
        }
    }
}

/** Reconocimiento de voz: descomprime el zip del modelo Vosk en asr/vosk-model (el zip lleva una carpeta raíz). */
abstract class GenerarAssetsAsr : DefaultTask() {
    @get:InputFile abstract val modelo: RegularFileProperty

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @get:Inject abstract val archivos: ArchiveOperations

    @get:Inject abstract val sistema: FileSystemOperations

    @TaskAction
    fun generar() {
        val destino = outputDir.get().asFile
        sistema.delete { delete(destino) }
        sistema.copy {
            into(File(destino, "asr/vosk-model"))
            includeEmptyDirs = false
            from(archivos.zipTree(modelo.get().asFile)) {
                eachFile { path = path.substringAfter('/') }
            }
        }
        val raiz = File(destino, "asr/vosk-model")
        val esperados = listOf("am/final.mdl", "graph/Gr.fst", "graph/HCLr.fst", "conf/model.conf")
        check(esperados.all { File(raiz, it).isFile }) {
            "El modelo Vosk extraído no tiene am/final.mdl, graph/Gr.fst, graph/HCLr.fst y conf/model.conf"
        }
    }
}

/**
 * Datos de mapas: baja el manifest (hash fijo) y todos sus archivos salvo los bajo demanda (ids que empiezan por un
 * prefijo de `bajoDemanda` en datos-incluidos.properties: satélite, relieve, curvas y voz Sharvard, que se descargan
 * al elegirlos); los deja en datos/<nombre>.asset (más datos/manifest-incluido.json, con los nombres reales).
 */
abstract class GenerarAssetsDatos : DefaultTask() {
    @get:Input abstract val etiqueta: Property<String>

    @get:Input abstract val bajoDemanda: ListProperty<String>

    @get:Input abstract val manifestSha256: Property<String>

    @get:Internal abstract val cacheDir: DirectoryProperty

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generar() {
        val cache = cacheDir.get().asFile
        val sha = manifestSha256.get()
        val base = Descargas.REPO_DATOS + etiqueta.get() + "/"
        val manifest = Descargas.asegurar(base + "manifest.json", sha, File(cache, "${sha.take(12)}-manifest.json"))

        @Suppress("UNCHECKED_CAST")
        val json = JsonSlurper().parse(manifest, "UTF-8") as MutableMap<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val todos = json["archivos"] as List<Map<String, Any?>>
        val prefijos = bajoDemanda.get()
        check(prefijos.isNotEmpty()) { "datos-incluidos.properties: 'bajoDemanda' no puede quedar vacío" }
        val incluidos = todos.filter { entrada -> prefijos.none { (entrada["id"] as String).startsWith(it) } }
        check(incluidos.isNotEmpty()) { "El manifest no lista archivos que incluir" }

        val destino = outputDir.get().asFile
        destino.deleteRecursively()
        val carpeta = File(destino, "datos").also { it.mkdirs() }
        for (entrada in incluidos) {
            val nombre = entrada["nombre"] as String
            val url = entrada["url"] as String
            check(!nombre.contains('/') && !nombre.contains('\\') && nombre != ".." && nombre.isNotBlank()) {
                "Nombre de archivo no válido en el manifest: $nombre"
            }
            check(url.startsWith(Descargas.REPO_DATOS)) { "URL fuera del repo de datos: $url" }
            val hash = entrada["sha256"] as String
            val archivo = Descargas.asegurar(url, hash, File(cache, "${hash.take(12)}-$nombre"))
            // Sufijo .asset en todos: AGP descomprime los assets *.gz y les quita la extensión (rompe sha256 y nombre).
            Files.copy(archivo.toPath(), File(carpeta, "$nombre.asset").toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        json["archivos"] = incluidos
        File(
            carpeta,
            "manifest-incluido.json",
        ).writeText(JsonOutput.prettyPrint(JsonOutput.toJson(json)) + "\n", Charsets.UTF_8)
    }
}

val directorioDescargas = rootProject.layout.projectDirectory.dir(".descargas")

// --- Datos de mapas incluidos ---
val datosIncluidos =
    Properties().apply {
        providers
            .fileContents(layout.projectDirectory.file("datos-incluidos.properties"))
            .asText
            .get()
            .reader()
            .use { load(it) }
    }

fun datosProp(clave: String): String =
    datosIncluidos.getProperty(clave)?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("datos-incluidos.properties: falta la clave '$clave'")

// --- sherpa-onnx (AAR oficial de la release de GitHub) ---
// Versión y SHA-256 viven solo en datos-incluidos.properties (un único punto de edición, ARQ-29).
val sherpaVersionVerificada = datosProp("sherpaVersion")
val sherpaSha256 = datosProp("sherpaSha256")

val descargarSherpaOnnx =
    tasks.register<DescargaVerificada>("descargarSherpaOnnx") {
        group = "descargas"
        description = "Descarga y verifica el AAR de sherpa-onnx $sherpaVersionVerificada."
        url.set(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersionVerificada/sherpa-onnx-$sherpaVersionVerificada.aar",
        )
        sha256.set(sherpaSha256)
        archivo.set(directorioDescargas.file("${sherpaSha256.take(12)}-sherpa-onnx-$sherpaVersionVerificada.aar"))
    }
dependencies.add("implementation", files(descargarSherpaOnnx.flatMap { it.archivo }))

// --- Voces Piper es_ES int8 ---
fun registrarVoz(
    nombre: String,
    sha256: String,
) = tasks.register<DescargaVerificada>("descargarVoz${nombre.replaceFirstChar { it.uppercase() }}") {
    group = "descargas"
    description = "Descarga y verifica la voz $nombre (Piper es_ES int8)."
    val archivoTar = "vits-piper-es_ES-$nombre-medium-int8.tar.bz2"
    url.set("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$archivoTar")
    this.sha256.set(sha256)
    archivo.set(directorioDescargas.file("${sha256.take(12)}-$archivoTar"))
}

val descargarVozDavefx = registrarVoz("davefx", "8bb8ac1cefb727caec9bd9c6c3185c673c8b42c53bd29bb25d5a7715dac37125")
// Sharvard va en la release de datos (rally-copilot-data, scripts/voz.py), con este mismo SHA-256 del tar.bz2 de sherpa-onnx.

// --- Reconocimiento de voz offline (Vosk): modelo pequeño español, bajado y verificado como las voces ---
val voskModeloSha256 = "09b239888f633ef2f0b4e09736e3d9936acfd810bc65d53fad45261762c6511f"
val descargarModeloVosk =
    tasks.register<DescargaVerificada>("descargarModeloVosk") {
        group = "descargas"
        description = "Descarga y verifica vosk-model-small-es-0.42 (Apache-2.0)."
        url.set("https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip")
        sha256.set(voskModeloSha256)
        archivo.set(directorioDescargas.file("${voskModeloSha256.take(12)}-vosk-model-small-es-0.42.zip"))
    }

// --- Assets generados por variante ---
// Los tipos del plugin de Android no se ven desde un script aplicado, así que el registro por variante (Variant API)
// queda en app/build.gradle.kts, que llama a esta función con el nombre de la variante y recibe las tareas creadas.
fun registrarAssetsDescargados(nombreVariante: String): List<TaskProvider<*>> {
    val sufijo = nombreVariante.replaceFirstChar { it.uppercase() }
    val voz =
        tasks.register<GenerarAssetsVoz>("generarAssetsVoz$sufijo") {
            group = "descargas"
            description = "Genera los assets voz/** de $nombreVariante."
            davefx.set(descargarVozDavefx.flatMap { it.archivo })
        }
    val asr =
        tasks.register<GenerarAssetsAsr>("generarAssetsAsr$sufijo") {
            group = "descargas"
            description = "Genera los assets de asr de $nombreVariante (modelo Vosk descomprimido)."
            modelo.set(descargarModeloVosk.flatMap { it.archivo })
        }
    val datos =
        tasks.register<GenerarAssetsDatos>("generarAssetsDatos$sufijo") {
            group = "descargas"
            description = "Genera los assets datos/** de $nombreVariante (manifest filtrado sin lo bajo demanda)."
            etiqueta.set(datosProp("etiqueta"))
            bajoDemanda.set(datosProp("bajoDemanda").split(',').map { it.trim() }.filter { it.isNotEmpty() })
            manifestSha256.set(datosProp("manifestSha256"))
            cacheDir.set(directorioDescargas)
        }
    return listOf(voz, asr, datos)
}

extra["registrarAssetsDescargados"] = ::registrarAssetsDescargados
