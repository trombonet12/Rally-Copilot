import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Motor puro (geometría, notas de rally, cronometraje, sectores, deltas, GPX/KML): sin Android, sin disco, sin red.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

ktlint {
    version.set(libs.versions.ktlint.get())
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

// Cobertura (QA-11). Mínimo de líneas solo en el motor (lógica crítica): 99,14 % medido el 2026-11-07, umbral en 90 (la decena
// por debajo). Si baja de ahí, `koverVerify` (y `check`) falla. Informes: build/reports/kover/{report.xml,html}.
kover {
    reports {
        verify {
            rule("Cobertura mínima de líneas del motor") {
                minBound(90)
            }
        }
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
