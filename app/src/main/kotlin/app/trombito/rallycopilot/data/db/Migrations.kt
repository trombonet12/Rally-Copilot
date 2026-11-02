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

/** v5 -> v6: paradas intermedias pendientes del guiado pendiente (la fila existente queda sin paradas). */
val MIGRATION_5_6: Migration =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `guiado_pendiente` ADD COLUMN `paradas` TEXT NOT NULL DEFAULT ''")
        }
    }

/**
 * v6 -> v7: tramos cronometrables y sus pasadas. Solo tablas nuevas; no toca ningún dato. El SQL es el `createSql` de
 * `schemas/.../7.json`.
 */
val MIGRATION_6_7: Migration =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `tramo` (`id` TEXT NOT NULL, `nombre` TEXT, `traza` TEXT NOT NULL, " +
                    "`longitudM` REAL NOT NULL, `sectores` TEXT NOT NULL, `microsectores` TEXT NOT NULL, " +
                    "`creadoEn` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `pasada` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`tramoId` TEXT NOT NULL, `inicio` INTEGER NOT NULL, `totalMs` INTEGER NOT NULL, " +
                    "`marcas` TEXT NOT NULL, FOREIGN KEY(`tramoId`) REFERENCES `tramo`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_pasada_tramoId_totalMs` ON `pasada` (`tramoId`, `totalMs`)",
            )
        }
    }

/**
 * v7 -> v8: tramos con nombre automático, favorito y último uso, y pasadas con el recorrido que las grabó. Las pasadas
 * existentes quedan sin recorrido (no se pueden recortar sus puntos). El último uso de cada tramo es el mayor entre
 * su creación y la salida de su última pasada. El SQL es el `createSql` de `schemas/.../8.json`.
 */
val MIGRATION_7_8: Migration =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tramo` ADD COLUMN `nombreAutomatico` TEXT")
            db.execSQL("ALTER TABLE `tramo` ADD COLUMN `favorito` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `tramo` ADD COLUMN `ultimoUso` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "UPDATE `tramo` SET `ultimoUso` = MAX(`creadoEn`, " +
                    "COALESCE((SELECT MAX(`inicio`) FROM `pasada` WHERE `pasada`.`tramoId` = `tramo`.`id`), 0))",
            )
            db.execSQL(
                "ALTER TABLE `pasada` ADD COLUMN `recorridoId` INTEGER REFERENCES `recorrido`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL",
            )
            db.execSQL("ALTER TABLE `pasada` ADD COLUMN `puntosDesdeMs` INTEGER")
            db.execSQL("ALTER TABLE `pasada` ADD COLUMN `puntosHastaMs` INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pasada_recorridoId` ON `pasada` (`recorridoId`)")
        }
    }

/**
 * v8 -> v9: ajuste «Activar con "Copiloto"» (desactivado en la fila existente) y la tabla `marca` de «marca aquí». El
 * SQL es el `createSql` de `schemas/.../9.json`.
 */
val MIGRATION_8_9: Migration =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `activarConCopiloto` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `marca` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`posicionM` REAL NOT NULL, `tiempoMs` INTEGER NOT NULL, `tramoId` TEXT, `lat` REAL, `lon` REAL)",
            )
        }
    }

/**
 * v9 -> v10: la tabla `edicion_nota` de las ediciones de notas ligadas al tramo (o pendientes de anclar). El SQL es el
 * `createSql` de `schemas/.../10.json`.
 */
val MIGRATION_9_10: Migration =
    object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `edicion_nota` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`tramoId` TEXT, `posicionM` REAL NOT NULL, `lat` REAL, `lon` REAL, `accion` TEXT NOT NULL, " +
                    "`nota` TEXT, `marcaTiempo` INTEGER NOT NULL, FOREIGN KEY(`tramoId`) REFERENCES `tramo`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_edicion_nota_tramoId` ON `edicion_nota` (`tramoId`)")
        }
    }
