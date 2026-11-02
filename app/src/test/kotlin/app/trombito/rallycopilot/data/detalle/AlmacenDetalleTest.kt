package app.trombito.rallycopilot.data.detalle

import android.database.sqlite.SQLiteDatabase
import app.trombito.rallycopilot.motor.precarga.Tesela
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AlmacenDetalleTest {
    @get:Rule val carpeta = TemporaryFolder()

    private val archivo: File get() = File(carpeta.root, "d.mbtiles")
    private val t1 = Tesela(18, 133_000, 100_000)
    private val t2 = Tesela(18, 133_001, 100_000)
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)

    @Test
    fun `crea un mbtiles estandar con metadata y la fila en TMS`() {
        AlmacenDetalle(archivo).use { it.guardar(mapOf(t1 to jpeg)) }

        val base = SQLiteDatabase.openDatabase(archivo.path, null, SQLiteDatabase.OPEN_READONLY)
        base.use {
            val meta = mutableMapOf<String, String>()
            it.rawQuery("SELECT name, value FROM metadata", null).use { c ->
                while (c.moveToNext()) meta[c.getString(0)] = c.getString(1)
            }
            assertEquals("jpg", meta["format"])
            assertEquals("18", meta["minzoom"])
            assertEquals("19", meta["maxzoom"])
            it.rawQuery("SELECT zoom_level, tile_column, tile_row, tile_data FROM tiles", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(18, c.getInt(0))
                assertEquals(133_000, c.getInt(1))
                assertEquals(262_143 - 100_000, c.getInt(2))
                assertEquals(jpeg.toList(), c.getBlob(3).toList())
            }
        }
    }

    @Test
    fun `guardar es idempotente y no duplica ni cambia la tesela`() {
        AlmacenDetalle(archivo).use {
            it.guardar(mapOf(t1 to jpeg))
            it.guardar(mapOf(t1 to byteArrayOf(9, 9)), listOf(t2))
            it.guardar(mapOf(t1 to jpeg), listOf(t2))
            assertEquals(1L, it.contarTeselas())
        }
        AlmacenDetalle(archivo).use { assertEquals(1L, it.contarTeselas()) }
    }

    @Test
    fun `resueltas incluye las guardadas y las de mar pero no cuenta las de mar como teselas`() {
        AlmacenDetalle(archivo).use {
            it.guardar(mapOf(t1 to jpeg), listOf(t2))
            assertEquals(setOf(AlmacenDetalle.clave(t1), AlmacenDetalle.clave(t2)), it.resueltas())
            assertEquals(1L, it.contarTeselas())
        }
    }

    @Test
    fun `las claves distinguen zoom columna y fila`() {
        val claves =
            listOf(
                t1,
                t2,
                Tesela(19, 133_000, 100_000),
                Tesela(18, 133_000, 100_001),
            ).map(AlmacenDetalle::clave)
        assertEquals(4, claves.toSet().size)
    }

    @Test
    fun `contarEn lee sin crear el archivo y da cero si no existe`() {
        assertEquals(0L, AlmacenDetalle.contarEn(archivo))
        assertFalse(archivo.exists())
        AlmacenDetalle(archivo).use { it.guardar(mapOf(t1 to jpeg, t2 to jpeg)) }
        assertEquals(2L, AlmacenDetalle.contarEn(archivo))
    }
}
