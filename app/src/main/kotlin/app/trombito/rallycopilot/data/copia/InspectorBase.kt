package app.trombito.rallycopilot.data.copia

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

/** Abre con SQLite las bases de una copia para comprobarlas o contarlas. */
internal object InspectorBase {
    private const val CONSULTA_HASH = "SELECT identity_hash FROM room_master_table WHERE id = 42"

    fun abrir(archivo: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(archivo.path, null, SQLiteDatabase.OPEN_READWRITE)

    /** Comprueba `integrity_check` y lee versión e identidad; cualquier fallo de SQLite es una base estropeada. */
    fun inspeccionar(archivo: File): IdentidadBase =
        try {
            abrir(archivo).use { db ->
                if (integridad(db) != "ok") estropeada(null)
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
