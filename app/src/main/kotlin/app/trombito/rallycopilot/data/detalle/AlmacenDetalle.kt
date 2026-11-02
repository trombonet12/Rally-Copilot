package app.trombito.rallycopilot.data.detalle

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import app.trombito.rallycopilot.motor.precarga.Tesela
import app.trombito.rallycopilot.motor.precarga.TeselasPrecarga
import java.io.Closeable
import java.io.File

/** Nombre del MBTiles escribible con el detalle z18–19, dentro de `filesDir/mapa/`. */
const val NOMBRE_DETALLE = "satelite-detalle.mbtiles"

/**
 * MBTiles escribible con las teselas PNOA de z18–19. Esquema estándar (`tiles` con `tile_row` TMS y `metadata`) más
 * `sin_cobertura`, donde se anotan las teselas de mar que el IGN sirve como PNG transparente para no pedirlas otra vez
 * (MapLibre solo lee `tiles` y `metadata`). Guardar es idempotente: una tesela repetida no cambia nada. Una sola
 * instancia escribe cada vez; las lecturas de estadísticas pueden abrir otra.
 */
class AlmacenDetalle(
    val archivo: File,
) : Closeable {
    private val base: SQLiteDatabase

    init {
        archivo.parentFile?.mkdirs()
        base = SQLiteDatabase.openOrCreateDatabase(archivo, null)
        base.execSQL("CREATE TABLE IF NOT EXISTS metadata (name TEXT PRIMARY KEY, value TEXT)")
        base.execSQL(
            "CREATE TABLE IF NOT EXISTS tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, " +
                "tile_data BLOB, PRIMARY KEY (zoom_level, tile_column, tile_row))",
        )
        base.execSQL(
            "CREATE TABLE IF NOT EXISTS sin_cobertura (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, " +
                "PRIMARY KEY (zoom_level, tile_column, tile_row))",
        )
        escribirMetadatos()
    }

    private fun escribirMetadatos() {
        val b = TeselasPrecarga.MALLORCA
        val metadatos =
            mapOf(
                "name" to "PNOA Mallorca detalle",
                "format" to "jpg",
                "type" to "baselayer",
                "minzoom" to TeselasPrecarga.ZOOM_MIN_ZONA.toString(),
                "maxzoom" to TeselasPrecarga.ZOOM_MAX_ZONA.toString(),
                "bounds" to "${b.oeste},${b.sur},${b.este},${b.norte}",
                "attribution" to "PNOA CC-BY 4.0 scne.es",
            )
        base.beginTransaction()
        try {
            metadatos.forEach { (nombre, valor) ->
                base.execSQL("INSERT OR REPLACE INTO metadata VALUES (?, ?)", arrayOf(nombre, valor))
            }
            base.setTransactionSuccessful()
        } finally {
            base.endTransaction()
        }
    }

    /** Claves (ver [clave]) de las teselas resueltas: guardadas o de mar. No lee los blobs. */
    fun resueltas(): Set<Long> {
        val claves = HashSet<Long>()
        for (tabla in listOf("tiles", "sin_cobertura")) {
            base.rawQuery("SELECT zoom_level, tile_column, tile_row FROM $tabla", null).use { c ->
                while (c.moveToNext()) {
                    val z = c.getInt(0)
                    claves += clave(Tesela(z, c.getInt(1), (1 shl z) - 1 - c.getInt(2)))
                }
            }
        }
        return claves
    }

    /** Guarda las teselas con datos y anota las de [sinCobertura], todo en una transacción. */
    fun guardar(
        conDatos: Map<Tesela, ByteArray>,
        sinCobertura: Collection<Tesela> = emptyList(),
    ) {
        base.beginTransaction()
        try {
            conDatos.forEach { (t, bytes) ->
                val valores =
                    ContentValues().apply {
                        put("zoom_level", t.z)
                        put("tile_column", t.x)
                        put("tile_row", t.filaTms)
                        put("tile_data", bytes)
                    }
                base.insertWithOnConflict("tiles", null, valores, SQLiteDatabase.CONFLICT_IGNORE)
            }
            sinCobertura.forEach { t ->
                val valores =
                    ContentValues().apply {
                        put("zoom_level", t.z)
                        put("tile_column", t.x)
                        put("tile_row", t.filaTms)
                    }
                base.insertWithOnConflict("sin_cobertura", null, valores, SQLiteDatabase.CONFLICT_IGNORE)
            }
            base.setTransactionSuccessful()
        } finally {
            base.endTransaction()
        }
    }

    /** Teselas con datos guardadas. */
    fun contarTeselas(): Long = contar(base)

    /** Bytes que ocupa el archivo en disco. */
    fun bytes(): Long = archivo.length()

    override fun close() = base.close()

    companion object {
        /** Clave única de una tesela XYZ: zoom (6 bits), x (28) e y (28). */
        fun clave(t: Tesela): Long = (t.z.toLong() shl 56) or (t.x.toLong() shl 28) or t.y.toLong()

        /** Teselas con datos del archivo [archivo], abriéndolo solo para leer; 0 si no existe o no se puede leer. */
        fun contarEn(archivo: File): Long {
            if (!archivo.isFile) return 0
            return try {
                val base = SQLiteDatabase.openDatabase(archivo.path, null, SQLiteDatabase.OPEN_READONLY)
                base.use { contar(it) }
            } catch (_: SQLiteException) {
                0
            }
        }

        private fun contar(base: SQLiteDatabase): Long =
            base.rawQuery("SELECT COUNT(*) FROM tiles", null).use { if (it.moveToFirst()) it.getLong(0) else 0 }
    }
}
