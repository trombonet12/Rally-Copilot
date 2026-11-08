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

/**
 * v10 -> v11: volumen propio de la voz del copiloto (100 % en la fila existente) y tema de la interfaz (oscuro). El
 * SQL es el `createSql` de `schemas/.../11.json`.
 */
val MIGRATION_10_11: Migration =
    object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `volumenVoz` INTEGER NOT NULL DEFAULT 100")
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `tema` TEXT NOT NULL DEFAULT 'OSCURO'")
        }
    }

/**
 * v11 -> v12: «Cronometrar trayectos» (activado en la fila existente) y modo de notas grandes (desactivado). El SQL es
 * el `createSql` de `schemas/.../12.json`.
 */
val MIGRATION_11_12: Migration =
    object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `cronometrar` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `notasGrandes` INTEGER NOT NULL DEFAULT 0")
        }
    }

/**
 * v12 -> v13: qué indicaciones se dicen y se ven: avisos de cruce, salidas de autopista y rotondas (todos activados en
 * la fila existente). El SQL es el `createSql` de `schemas/.../13.json`.
 */
val MIGRATION_12_13: Migration =
    object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `avisarCruces` INTEGER NOT NULL DEFAULT 1")
            db.execSQL(
                "ALTER TABLE `ajustes_conduccion` ADD COLUMN `avisarSalidasAutopista` INTEGER NOT NULL DEFAULT 1",
            )
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `avisarRotondas` INTEGER NOT NULL DEFAULT 1")
        }
    }

/**
 * v13 -> v14: salida del cronómetro manual o automática (automática, 0, en la fila existente). El SQL es el
 * `createSql` de `schemas/.../14.json`.
 */
val MIGRATION_13_14: Migration =
    object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `salidaManual` INTEGER NOT NULL DEFAULT 0")
        }
    }

/**
 * v14 -> v15: etiqueta de las marcas (`radar` para «avisa radar aquí»; las existentes quedan sin etiqueta) y la palabra
 * «Copiloto» activada por defecto (decisión de Joan, 2026-11-06): la fila de ajustes existente pasa a activada. El SQL
 * de la columna es el `createSql` de `schemas/.../15.json`.
 */
val MIGRATION_14_15: Migration =
    object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `marca` ADD COLUMN `etiqueta` TEXT")
            db.execSQL("UPDATE `ajustes_conduccion` SET `activarConCopiloto` = 1")
        }
    }

/**
 * v15 -> v16: casa de Joan en los ajustes («volver a casa»), dos columnas nulas (latitud y longitud); sin casa, ambas
 * `NULL`. El SQL es el `createSql` de `schemas/.../16.json`.
 */
val MIGRATION_15_16: Migration =
    object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `casaLat` REAL")
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `casaLon` REAL")
        }
    }

/**
 * v16 -> v17: la tabla `marca` gana la clave foránea `tramoId -> tramo(id)` con borrado en cascada y su índice (antes
 * las marcas de un tramo borrado quedaban huérfanas para siempre), y nace `calibracion`, el factor de calibración de
 * las notas guardado al cerrar cada recorrido. SQLite no añade claves foráneas a una tabla existente: se reconstruye
 * `marca` copiando solo las marcas sin tramo o con un tramo que existe (las huérfanas se descartan). El SQL es el
 * `createSql` de `schemas/.../17.json`.
 */
val MIGRATION_16_17: Migration =
    object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `marca_nueva` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`posicionM` REAL NOT NULL, `tiempoMs` INTEGER NOT NULL, `tramoId` TEXT, `lat` REAL, `lon` REAL, " +
                    "`etiqueta` TEXT, FOREIGN KEY(`tramoId`) REFERENCES `tramo`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "INSERT INTO `marca_nueva` (`id`, `posicionM`, `tiempoMs`, `tramoId`, `lat`, `lon`, `etiqueta`) " +
                    "SELECT `id`, `posicionM`, `tiempoMs`, `tramoId`, `lat`, `lon`, `etiqueta` FROM `marca` " +
                    "WHERE `tramoId` IS NULL OR `tramoId` IN (SELECT `id` FROM `tramo`)",
            )
            db.execSQL("DROP TABLE `marca`")
            db.execSQL("ALTER TABLE `marca_nueva` RENAME TO `marca`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_marca_tramoId` ON `marca` (`tramoId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `calibracion` (`id` INTEGER NOT NULL, `clave` TEXT NOT NULL, " +
                    "`factor` REAL, PRIMARY KEY(`id`))",
            )
        }
    }

/**
 * v17 -> v18: los interruptores de avisos por voz (radares, incidencias, velocidad: encendidos para quien actualiza)
 * y la velocidad de habla del copiloto (`NORMAL`). Cuatro columnas con valor por defecto en SQL, el `createSql` de
 * `schemas/.../18.json`.
 */
val MIGRATION_17_18: Migration =
    object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `avisarRadares` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `avisarIncidencias` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `avisarVelocidad` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `velocidadHabla` TEXT NOT NULL DEFAULT 'NORMAL'")
        }
    }

/**
 * v18 -> v19: avisos de puentes (columna `avisarPuentes`, apagada), cruces apagados también en la fila existente
 * (decisión de Joan, 2026-11-08: el valor por defecto de la columna sigue en 1, SQLite no lo cambia sin rehacer la
 * tabla) y la tabla `subtramo` con sus índices. El SQL es el `createSql` de `schemas/.../19.json`.
 */
val MIGRATION_18_19: Migration =
    object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `avisarPuentes` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `ajustes_conduccion` SET `avisarCruces` = 0")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `subtramo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`recorridoId` INTEGER, `tramoRefId` TEXT NOT NULL, `desdeM` REAL NOT NULL, " +
                    "`hastaM` REAL NOT NULL, `longitudM` REAL NOT NULL, `inicio` INTEGER NOT NULL, " +
                    "`tiempoMs` INTEGER NOT NULL, `mejorMs` INTEGER, `latDesde` REAL NOT NULL, " +
                    "`lonDesde` REAL NOT NULL, `latHasta` REAL NOT NULL, `lonHasta` REAL NOT NULL, " +
                    "FOREIGN KEY(`recorridoId`) REFERENCES `recorrido`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`tramoRefId`) REFERENCES `tramo`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtramo_recorridoId` ON `subtramo` (`recorridoId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtramo_tramoRefId` ON `subtramo` (`tramoRefId`)")
        }
    }

/**
 * v19 -> v20: ajustes del intercom del casco. `audioCasco` (1: automático, la voz y el micrófono van por el casco si lo
 * hay) y `escuchaCascoSiempre` (0: con casco, «Copiloto» no escucha sin parar, solo al pulsar el micrófono, para que la
 * música del intercom no pase a calidad de llamada). El SQL es el `createSql` de `schemas/.../20.json`.
 */
val MIGRATION_19_20: Migration =
    object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `audioCasco` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `ajustes_conduccion` ADD COLUMN `escuchaCascoSiempre` INTEGER NOT NULL DEFAULT 0")
        }
    }

/**
 * Todas las migraciones, en orden, de la v1 a la versión actual de [RallyDatabase]. Es la única lista: la usan la base
 * de producción y los tests; una migración nueva se añade aquí y `MigracionRoomTest` vigila que la cadena no tenga
 * huecos hasta `RallyDatabase.version`.
 */
val TODAS_LAS_MIGRACIONES: Array<Migration> =
    arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
        MIGRATION_19_20,
    )
