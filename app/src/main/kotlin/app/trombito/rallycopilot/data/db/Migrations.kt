package app.trombito.rallycopilot.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val CREAR_TABLAS_V2 =
    listOf(
        "CREATE TABLE IF NOT EXISTS `ajustes_conduccion` (`id` INTEGER NOT NULL, `nivelZoom` INTEGER NOT NULL, " +
            "`icono` TEXT NOT NULL, `orientacion` TEXT NOT NULL, `capa` TEXT NOT NULL, " +
            "`relieve` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `recorrido` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`inicioMs` INTEGER NOT NULL, `finMs` INTEGER, `distanciaM` REAL NOT NULL, `destinoNombre` TEXT)",
        "CREATE TABLE IF NOT EXISTS `recorrido_punto` (`recorridoId` INTEGER NOT NULL, `orden` INTEGER NOT NULL, " +
            "`lat` REAL NOT NULL, `lon` REAL NOT NULL, `tiempoMs` INTEGER NOT NULL, `velocidadMs` REAL, " +
            "PRIMARY KEY(`recorridoId`, `orden`), FOREIGN KEY(`recorridoId`) REFERENCES `recorrido`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )",
    )

/**
 * v1 -> v2: ajustes de conducción y recorridos grabados. Solo tablas nuevas; no toca los datos de v1.
 * El SQL es el `createSql` de `schemas/.../2.json`.
 */
val MIGRATION_1_2: Migration =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            CREAR_TABLAS_V2.forEach(db::execSQL)
        }
    }
