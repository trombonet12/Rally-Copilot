package app.trombito.rallycopilot.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v21 -> v22 (llegada estimada del guiado): los recorridos de Joan se conservan tal cual y la columna nueva nace nula
 * (no se sabe qué estimación se enseñó al salir). Nombre de base corto (MAX_PATH en Windows).
 */
@RunWith(RobolectricTestRunner::class)
class MigracionRoomV22Test {
    private val contexto = ApplicationProvider.getApplicationContext<Context>()
    private val antigua = BaseAntigua(contexto)
    private var base: RallyDatabase? = null

    @After
    fun cerrar() {
        base?.close()
        base = null
        contexto.deleteDatabase(BaseAntigua.NOMBRE)
    }

    private fun sembrarV21() {
        val archivo = antigua.crear(21)
        SQLiteDatabase.openDatabase(archivo.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO recorrido (id, inicioMs, finMs, distanciaM, destinoNombre, tiempoPrevistoS, " +
                    "viasTroceadas, llegado) VALUES (1, 1000, 9000, 12345.5, 'Sóller', 1800.5, 1, 1)",
            )
            db.execSQL(
                "INSERT INTO recorrido (id, inicioMs, finMs, distanciaM, destinoNombre, tiempoPrevistoS, " +
                    "viasTroceadas, llegado) VALUES (2, 20000, NULL, 0.0, NULL, NULL, 0, 0)",
            )
        }
    }

    private fun migrar(): RallyDatabase =
        Room
            .databaseBuilder(contexto, RallyDatabase::class.java, BaseAntigua.NOMBRE)
            .addMigrations(*TODAS_LAS_MIGRACIONES)
            .allowMainThreadQueries()
            .build()
            .also { base = it }

    @Test
    fun `los recorridos se conservan y la llegada estimada nace nula`() =
        runBlocking {
            sembrarV21()
            val recorridos = migrar().recorridosDao()

            assertEquals(
                RecorridoEntity(
                    1,
                    1000,
                    9000,
                    12345.5,
                    "Sóller",
                    tiempoPrevistoS = 1800.5,
                    viasTroceadas = true,
                    llegado = true,
                    llegadaEstimadaS = null,
                ),
                recorridos.recorrido(1),
            )
            assertEquals(null, recorridos.recorrido(2)?.finMs)
        }

    @Test
    fun `la llegada estimada se puede escribir y leer en la base migrada sin tocar el tiempo previsto`() =
        runBlocking {
            sembrarV21()
            val recorridos = migrar().recorridosDao()

            assertEquals(1, recorridos.guardarLlegadaEstimada(1, 2_100.25))
            assertEquals(2_100.25, recorridos.recorrido(1)?.llegadaEstimadaS)
            assertEquals(1800.5, recorridos.recorrido(1)?.tiempoPrevistoS)
            assertEquals(0, recorridos.guardarLlegadaEstimada(999, 1.0))
        }

    @Test
    fun `la base queda en la v22`() =
        runBlocking {
            sembrarV21()
            val db = migrar().openHelper.writableDatabase

            assertEquals(RallyDatabase.VERSION, db.version)
            assertEquals(22, db.version)
        }
}
