import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

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

// Tareas de descarga y generación de assets (voz, reconocimiento y datos de mapas): gradle/descargas.gradle.kts. Va fuera
// de app/ porque lint analiza cada *.gradle.kts bajo el módulo y falla con un script aplicado.
apply(from = rootProject.file("gradle/descargas.gradle.kts"))

androidComponents.onVariants { variante ->
    @Suppress("UNCHECKED_CAST")
    val registrar = extra["registrarAssetsDescargados"] as (String) -> List<TaskProvider<Task>>
    registrar(variante.name).forEach { tarea ->
        variante.sources.assets?.addGeneratedSourceDirectory(tarea) { it.property("outputDir") as DirectoryProperty }
    }
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
