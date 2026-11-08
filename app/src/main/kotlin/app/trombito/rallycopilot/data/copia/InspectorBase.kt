package app.trombito.rallycopilot.data.copia

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.sqlite.db.SupportSQLiteDatabase
import app.trombito.rallycopilot.domain.copia.CopiaInvalidaException
import app.trombito.rallycopilot.domain.copia.MotivoCopiaInvalida
import java.io.File

/** De una base de la copia: versión del esquema (`user_version`) e identidad de Room (null si no es de Room). */
internal data class IdentidadBase(
    val version: Int,
    val hash: String?,
)

/**
 * Abre con SQLite (solo lectura) las bases de una copia para comprobarlas o contarlas: `integrity_check`, versión,
 * identidad de Room y estructura ([EstructuraRoom]).
 */
internal object InspectorBase {
    private const val CONSULTA_HASH = "SELECT identity_hash FROM room_master_table WHERE id = 42"

    fun abrir(archivo: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(archivo.path, null, SQLiteDatabase.OPEN_READONLY)

    /**
     * Comprueba `integrity_check` y la estructura y lee versión e identidad; cualquier fallo de SQLite es una base
     * estropeada. Si la copia tiene la misma versión que la base [actual], sus tablas deben tener las mismas columnas.
     */
    fun inspeccionar(
        archivo: File,
        actual: SupportSQLiteDatabase? = null,
    ): IdentidadBase =
        try {
            abrir(archivo).use { db ->
                if (integridad(db) != "ok") estropeada(null)
                EstructuraRoom.verificar(db, actual)
                IdentidadBase(db.version, hashDeRoom(db))
            }
        } catch (e: SQLiteException) {
            estropeada(e)
        }

    private fun integridad(db: SQLiteDatabase): String? =
        db.rawQuery("PRAGMA integrity_check", null).use { if (it.moveToFirst()) it.getString(0) else null }

    /** Filas de [tabla]; 0 si la tabla no existe (una copia antigua no tiene las tablas nuevas). */
    fun contar(
        db: SQLiteDatabase,
        tabla: String,
    ): Int {
        val existe =
            db
                .rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(tabla))
                .use { it.moveToFirst() }
        if (!existe) return 0
        return db.rawQuery("SELECT COUNT(*) FROM $tabla", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /** Identidad de la base abierta por Room en la app. */
    fun hashDeRoom(db: SupportSQLiteDatabase): String? =
        db.query(CONSULTA_HASH).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun hashDeRoom(db: SQLiteDatabase): String? {
        val hayTabla =
            db
                .rawQuery("SELECT 1 FROM sqlite_master WHERE name = 'room_master_table'", null)
                .use { it.moveToFirst() }
        if (!hayTabla) return null
        return db.rawQuery(CONSULTA_HASH, null).use { if (it.moveToFirst()) it.getString(0) else null }
    }

    private fun estropeada(causa: Throwable?): Nothing =
        throw CopiaInvalidaException(MotivoCopiaInvalida.BASE_DANADA, "la base está estropeada", causa)
}

/**
 * Lo único que puede contener la base de una copia: lo que Room crea en alguna versión del esquema. Sin triggers ni
 * vistas, sin tablas ni índices desconocidos. El identity_hash de Room no basta: está en `app/schemas` del repo
 * público.
 */
internal object EstructuraRoom {
    /** Tablas de Room en cualquiera de las versiones del esquema (unión de `app/schemas`; lo vigila un test). */
    val TABLAS_ROOM =
        setOf(
            "ajustes_conduccion",
            "ajustes_mapas",
            "archivo_instalado",
            "calibracion",
            "edicion_nota",
            "estado_datos",
            "guiado_pendiente",
            "marca",
            "pasada",
            "recorrido",
            "recorrido_punto",
            "subtramo",
            "tramo",
        )

    /** Índices de Room en cualquiera de las versiones del esquema. */
    val INDICES_ROOM =
        setOf(
            "index_edicion_nota_tramoId",
            "index_marca_tramoId",
            "index_pasada_recorridoId",
            "index_pasada_tramoId_totalMs",
            "index_subtramo_recorridoId",
            "index_subtramo_tramoRefId",
        )

    /** Tablas que crean SQLite, Android o Room por su cuenta. */
    private val TABLAS_INTERNAS = setOf("sqlite_sequence", "room_master_table", "android_metadata")
    private const val PREFIJO_AUTOINDICE = "sqlite_autoindex_"

    fun verificar(
        db: SQLiteDatabase,
        actual: SupportSQLiteDatabase?,
    ) {
        // Una copia de una versión más nueva puede traer tablas nuevas: se rechaza después como esquema futuro.
        if (actual != null && db.version > actual.version) return
        verificarEstructura(db)
        if (actual != null && db.version == actual.version) compararColumnas(db, actual)
    }

    private fun compararColumnas(
        copia: SQLiteDatabase,
        actual: SupportSQLiteDatabase,
    ) {
        if (columnasDe { copia.rawQuery(it, null) } != columnasDe { actual.query(it) }) {
            incompatible("las tablas no casan con las de esta versión")
        }
    }

    /** Rechaza triggers, vistas y cualquier tabla o índice que Room no cree. */
    private fun verificarEstructura(db: SQLiteDatabase) {
        db.rawQuery("SELECT type, name FROM sqlite_master", null).use { c ->
            while (c.moveToNext()) {
                val tipo = c.getString(0)
                val nombre = c.getString(1)
                val admitido =
                    when (tipo) {
                        "table" -> nombre in TABLAS_ROOM || nombre in TABLAS_INTERNAS
                        "index" -> nombre in INDICES_ROOM || nombre.startsWith(PREFIJO_AUTOINDICE)
                        else -> false // trigger, view u otra cosa
                    }
                if (!admitido) incompatible("contiene un $tipo que Room no crea")
            }
        }
    }

    /** Por tabla de Room: columnas (nombre, tipo, not null, clave primaria) sin importar el orden. */
    private fun columnasDe(consulta: (String) -> Cursor): Map<String, Set<String>> {
        val existentes =
            consulta("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
        return existentes.filter { it in TABLAS_ROOM }.associateWith { tabla ->
            consulta("PRAGMA table_info(`$tabla`)").use { c ->
                buildSet {
                    while (c.moveToNext()) {
                        add("${c.getString(1)}|${c.getString(2).uppercase()}|${c.getInt(3)}|${c.getInt(5)}")
                    }
                }
            }
        }
    }

    private fun incompatible(motivo: String): Nothing =
        throw CopiaInvalidaException(MotivoCopiaInvalida.INCOMPATIBLE, motivo)
}
