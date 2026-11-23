package app.trombito.rallycopilot.data.copia

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/**
 * `archivo_instalado` y `estado_datos` registran los archivos de mapas que hay en el disco de ESTE dispositivo: si se
 * restauraran los de la copia, una instalación sin mapas los daría por instalados («dañados») y la siembra no los
 * volvería a poner. Antes de sustituir la base, la copia se queda con las filas de la base actual en esas dos tablas
 * (las columnas que tengan en común, por si la copia es de un esquema menor aún sin migrar).
 */
internal object RegistrosDelDispositivo {
    private val TABLAS = listOf("archivo_instalado", "estado_datos")

    fun conservar(
        copia: File,
        actual: SupportSQLiteDatabase,
    ) {
        SQLiteDatabase.openDatabase(copia.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.beginTransaction()
            try {
                TABLAS.filter { InspectorBase.existe(db, it) }.forEach { sustituir(actual, db, it) }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun sustituir(
        origen: SupportSQLiteDatabase,
        destino: SQLiteDatabase,
        tabla: String,
    ) {
        destino.execSQL("DELETE FROM `$tabla`")
        val columnasDestino =
            destino.rawQuery("PRAGMA table_info(`$tabla`)", null).use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(1)) }
            }
        origen.query("SELECT * FROM `$tabla`").use { c ->
            val columnas = c.columnNames.filter { it in columnasDestino }
            while (c.moveToNext()) destino.insertOrThrow(tabla, null, fila(c, columnas))
        }
    }

    private fun fila(
        c: Cursor,
        columnas: List<String>,
    ): ContentValues {
        val valores = ContentValues()
        for (nombre in columnas) {
            val i = c.getColumnIndexOrThrow(nombre)
            when (c.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> valores.putNull(nombre)
                Cursor.FIELD_TYPE_INTEGER -> valores.put(nombre, c.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> valores.put(nombre, c.getDouble(i))
                else -> valores.put(nombre, c.getString(i))
            }
        }
        return valores
    }
}
