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

/** v2 -> v3: estilo de los iconos de maniobra en los ajustes de conducción (la fila existente queda en PLACA). */
val MIGRATION_2_3: Migration =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `estiloManiobras` TEXT NOT NULL DEFAULT 'PLACA'")
        }
    }

/**
 * v3 -> v4: anticipación de las notas y notas simplificadas en los ajustes de conducción (la fila existente queda en
 * NORMAL y sin simplificar) y tabla del guiado pendiente. El SQL es el `createSql` de `schemas/.../4.json`.
 */
val MIGRATION_3_4: Migration =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `anticipacion` TEXT NOT NULL DEFAULT 'NORMAL'")
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `notasSimplificadas` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `guiado_pendiente` (`id` INTEGER NOT NULL, " +
                    "`destinoNombre` TEXT NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, " +
                    "`perfil` TEXT NOT NULL, `desdeMs` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
        }
    }

/**
 * v4 -> v5: modo y voz del copiloto en los ajustes de conducción (la fila existente queda en COMPLETO y sin voz
 * elegida). El SQL es el `createSql` de `schemas/.../5.json`.
 */
val MIGRATION_4_5: Migration =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `modoCopiloto` TEXT NOT NULL DEFAULT 'COMPLETO'")
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `voz` TEXT")
        }
    }
