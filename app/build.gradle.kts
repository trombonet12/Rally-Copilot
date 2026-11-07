import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.androidx.room)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Firma release solo si existen las variables RALLY_* (scripts/android-release.mjs las carga de ~/.rallycopilot).
val releaseStoreFile: String? = System.getenv("RALLY_KEYSTORE_FILE")
val releaseStorePassword: String? = System.getenv("RALLY_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("RALLY_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("RALLY_KEY_PASSWORD")
// `-Prally.sinFirma=true` (solo CI): compila el release SIN firmar; el workflow firma después con apksigner/jarsigner
// y solo ese paso ve el keystore, así los plugins y dependencias del build no tocan la clave (auditoría SEG-1).
val releaseSinFirma: Boolean = providers.gradleProperty("rally.sinFirma").orNull == "true"
val hasReleaseSigning =
    !releaseSinFirma &&
        listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

// Versión de la app: major/minor en version.properties; patch y versionCode salen de git (ver version.properties).
val versionProps =
    Properties().apply {
        val file = rootProject.file("version.properties")
        check(file.isFile) { "Falta version.properties en la raíz del repo (versionMajor, versionMinor)" }
        providers
            .fileContents(rootProject.layout.projectDirectory.file("version.properties"))
            .asText
            .get()
            .reader()
            .use { load(it) }
    }

fun versionProp(key: String): String =
    versionProps.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("version.properties: falta la clave '$key'")

/** Salida de git (compatible con la configuration cache) o null si git no está o el comando falla. */
fun gitOutput(vararg args: String): String? =
    try {
        val result =
            providers.exec {
                workingDir = rootProject.projectDir
                commandLine(listOf("git") + args)
                isIgnoreExitValue = true
            }
        if (result.result.get().exitValue == 0) {
            result.standardOutput.asText
                .get()
                .trim()
                .takeIf { it.isNotEmpty() }
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }

val gitPatch: Int? =
    gitOutput("log", "-1", "--format=%H", "--", "version.properties")
        ?.let { gitOutput("rev-list", "--count", "$it..HEAD") }
        ?.toIntOrNull()
val gitCount: Int? = gitOutput("rev-list", "--count", "HEAD")?.toIntOrNull()
if (gitPatch == null || gitCount == null) {
    logger.warn("No se pudo leer git: versión con patch 0 y versionCode 1")
}
val appVersionName = "${versionProp("versionMajor")}.${versionProp("versionMinor")}.${gitPatch ?: 0}"
val appVersionCode = gitCount ?: 1

tasks.register("imprimirVersion") {
    group = "help"
    description = "Imprime 'versionName versionCode' en una línea."
    val linea = "$appVersionName $appVersionCode"
    doLast { println(linea) }
}

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
 * Voces: extrae de los tar.bz2 el contrato voz/davefx, voz/sharvard y voz/espeak-ng-data (una sola copia, recortada a
 * lo que usa es_ES: los diccionarios de los demás idiomas, 17 de los 17,2 MiB, no se empaquetan; se conserva `en_dict`
 * por si una palabra inglesa cambia de idioma). Al tocar el filtro, subir CopiaEspeak.VERSION.
 */
abstract class GenerarAssetsVoz : DefaultTask() {
    @get:InputFile abstract val davefx: RegularFileProperty

    @get:InputFile abstract val sharvard: RegularFileProperty

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
            for ((nombre, tar) in listOf("davefx" to davefx.get().asFile, "sharvard" to sharvard.get().asFile)) {
                from(archivos.tarTree(tar)) {
                    include("*/es_ES-$nombre-medium.onnx", "*/tokens.txt")
                    eachFile { path = "voz/$nombre/" + if (name.endsWith(".onnx")) "model.onnx" else name }
                }
            }
            // Una única copia de espeak-ng-data (las dos voces traen la misma).
            from(archivos.tarTree(davefx.get().asFile)) {
                include("*/espeak-ng-data/**")
                exclude { elemento ->
                    val nombre = elemento.file.name
                    nombre.endsWith("_dict") && nombre != "es_dict" && nombre != "en_dict"
                }
                eachFile { path = "voz/" + path.substringAfter('/') }
            }
        }
        val modelos = listOf("davefx", "sharvard").map { File(destino, "voz/$it/model.onnx") }
        val tokens = listOf("davefx", "sharvard").map { File(destino, "voz/$it/tokens.txt") }
        check((modelos + tokens).all { it.isFile } && File(destino, "voz/espeak-ng-data").isDirectory) {
            "Las voces extraídas no cumplen el contrato voz/{davefx,sharvard}/{model.onnx,tokens.txt} y voz/espeak-ng-data"
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
 * Datos de mapas: baja el manifest (hash fijo) y todos sus archivos salvo el satélite; los deja en datos/<nombre>.asset
 * (más datos/manifest-incluido.json, con los nombres reales).
 */
abstract class GenerarAssetsDatos : DefaultTask() {
    @get:Input abstract val etiqueta: Property<String>

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
        val incluidos = todos.filter { !(it["id"] as String).startsWith("satelite") }
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

// --- sherpa-onnx (AAR oficial de la release de GitHub) ---
val sherpaVersionVerificada = "1.13.8"
val sherpaSha256 = "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
val sherpaVersionCatalogo = libs.versions.sherpaOnnx.get()
check(sherpaVersionCatalogo == sherpaVersionVerificada) {
    "libs.versions.sherpaOnnx=$sherpaVersionCatalogo, pero la URL y el SHA-256 de app/descargas.gradle.kts son de " +
        "$sherpaVersionVerificada: actualízalos juntos"
}

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
val descargarVozSharvard = registrarVoz("sharvard", "bf5507cbdedea650365bd6175b3164a8ecca5847533ddf97afcba9d76ea599d1")

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

// --- Assets generados por variante ---
androidComponents.onVariants { variante ->
    val sufijo = variante.name.replaceFirstChar { it.uppercase() }
    val voz =
        tasks.register<GenerarAssetsVoz>("generarAssetsVoz$sufijo") {
            group = "descargas"
            description = "Genera los assets voz/** de ${variante.name}."
            davefx.set(descargarVozDavefx.flatMap { it.archivo })
            sharvard.set(descargarVozSharvard.flatMap { it.archivo })
        }
    variante.sources.assets?.addGeneratedSourceDirectory(voz, GenerarAssetsVoz::outputDir)

    val asr =
        tasks.register<GenerarAssetsAsr>("generarAssetsAsr$sufijo") {
            group = "descargas"
            description = "Genera los assets de asr de ${variante.name} (modelo Vosk descomprimido)."
            modelo.set(descargarModeloVosk.flatMap { it.archivo })
        }
    variante.sources.assets?.addGeneratedSourceDirectory(asr, GenerarAssetsAsr::outputDir)

    val datos =
        tasks.register<GenerarAssetsDatos>("generarAssetsDatos$sufijo") {
            group = "descargas"
            description = "Genera los assets datos/** de ${variante.name} (manifest filtrado sin satélite)."
            etiqueta.set(datosProp("etiqueta"))
            manifestSha256.set(datosProp("manifestSha256"))
            cacheDir.set(directorioDescargas)
        }
    variante.sources.assets?.addGeneratedSourceDirectory(datos, GenerarAssetsDatos::outputDir)
}

android {
    namespace = "app.trombito.rallycopilot"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.trombito.rallycopilot"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            // Móviles actuales (arm64) y el emulador trombito-36 (x86_64), que usa el debug (auditoría REN-1).
            ndk {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
        }
        release {
            // Solo arm64-v8a: el x86_64 (~65 MiB) únicamente sirve al emulador, que usa el debug (auditoría REN-1).
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // El JNI de sherpa-onnx (única biblioteca que se carga) no depende de la envoltura C++ (auditoría REN-27).
            excludes += "**/libsherpa-onnx-cxx-api.so"
        }
    }

    // Modelos y datos que se leen tal cual (mmap/copia): comprimirlos solo alarga el build y el arranque.
    androidResources {
        // Los datos van como datos/<nombre>.asset; el .tar sí se comprime.
        noCompress += listOf("onnx", "pmtiles.asset", "gz.asset")
    }
}

// Robolectric (corre con el targetSdk 36; se compila contra 37) necesita abrir internals del JDK 21.
tasks.withType<Test>().configureEach {
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.lang=ALL-UNNAMED")
    // Con ~1 850 tests Robolectric en una sola JVM, los 512 MB por defecto se quedan cortos (OutOfMemoryError).
    maxHeapSize = "2g"
}

room {
    schemaDirectory("$projectDir/schemas")
}

ktlint {
    version.set(libs.versions.ktlint.get())
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

dependencies {
    implementation(project(":motor"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.vosk.android)
    // JNA como AAR (lleva libjnidispatch.so para Android); Vosk la declara sin el empaquetado nativo.
    implementation("${libs.jna.get().module}:${libs.jna.get().version}@aar")

    implementation(libs.maplibre.android)
    implementation(libs.valhalla.mobile)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.work.testing)
}

// Un release sin firma no sirve para instalar ni actualizar: falla al ejecutar (no al configurar, para no romper debug),
// salvo que se pida expresamente con -Prally.sinFirma=true (CI, que firma después fuera de Gradle).
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    doFirst {
        check(
            hasReleaseSigning || releaseSinFirma,
        ) { "Release sin firma: faltan las variables RALLY_* (usa node scripts/android-release.mjs)" }
    }
}

// La caché de build restauraba un componente Hilt viejo tras cambiar el constructor de un ViewModel (NoSuchMethodError
// en ejecución, visto en Sous Chef). El componente se regenera siempre.
tasks.matching { it.name.startsWith("hiltJavaCompile") }.configureEach { outputs.cacheIf { false } }
