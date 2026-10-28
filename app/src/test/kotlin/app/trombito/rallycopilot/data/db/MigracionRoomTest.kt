package app.trombito.rallycopilot.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Migración v1 -> v2. No hay `room-testing` (MigrationTestHelper) en el proyecto: la base v1 se crea con el SQL
 * exportado en `schemas/.../1.json` y Room la abre como v2; al abrir valida el esquema resultante contra `2.json`
 * (identity hash y tablas), así que un SQL de migración distinto del esquema falla aquí.
 */
@RunWith(RobolectricTestRunner::class)
class MigracionRoomTest {
    private val contexto = ApplicationProvider.getApplicationContext<Context>()
    private var base: RallyDatabase? = null

    @After
    fun cerrar() {
        base?.close()
        contexto.deleteDatabase(NOMBRE)
    }

    private fun esquema(version: Int): JsonObject {
        val archivo = File("schemas/app.trombito.rallycopilot.data.db.RallyDatabase/$version.json")
        return Json
            .parseToJsonElement(archivo.readText())
            .jsonObject
            .getValue("database")
            .jsonObject
    }

    private fun crearBaseV1(): File {
        val archivo = contexto.getDatabasePath(NOMBRE)
        archivo.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(archivo, null)
        val esquemaV1 = esquema(1)
        for (entidad in esquemaV1.getValue("entities").jsonArray) {
            val sql =
                entidad.jsonObject
                    .getValue("createSql")
                    .jsonPrimitive.content
            db.execSQL(
                sql.replace(
                    "\${TABLE_NAME}",
                    entidad.jsonObject
                        .getValue("tableName")
                        .jsonPrimitive.content,
                ),
            )
        }
        esquemaV1.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
        db.execSQL("INSERT INTO archivo_instalado VALUES ('mapa', 'mapa.pmtiles', '${"a".repeat(64)}', 123, 'v1')")
        db.execSQL("INSERT INTO ajustes_mapas VALUES (1, 0, 777)")
        db.execSQL("INSERT INTO estado_datos VALUES (1, 'v1', NULL, 'SIN_RED')")
        db.version = 1
        db.close()
        return archivo
    }

    @Test
    fun `de v1 a v2 conserva los datos y crea las tablas nuevas`() =
        runBlocking {
            crearBaseV1()

            val nueva =
                Room
                    .databaseBuilder(contexto, RallyDatabase::class.java, NOMBRE)
                    .addMigrations(MIGRATION_1_2)
                    .allowMainThreadQueries()
                    .build()
            base = nueva

            val archivos = nueva.datosMapaDao().observarArchivos().first()
            assertEquals(listOf("mapa"), archivos.map { it.id })
            assertEquals(123L, archivos.single().bytes)
            assertEquals(false, nueva.ajustesMapasDao().ajustes()?.soloWifi)
            assertEquals(777L, nueva.ajustesMapasDao().ajustes()?.limiteBytes)
            assertEquals("SIN_RED", nueva.datosMapaDao().estado()?.ultimoError)

            assertEquals(null, nueva.ajustesConduccionDao().observar().first())
            val id =
                nueva.recorridosDao().insertar(
                    RecorridoEntity(inicioMs = 1, finMs = null, distanciaM = 0.0, destinoNombre = null),
                )
            nueva.recorridosDao().anadirPuntos(id, listOf(RecorridoPuntoEntity(id, 0, 39.5, 2.9, 10, null)))
            assertEquals(1, nueva.recorridosDao().puntos(id).size)
            assertNotNull(nueva.openHelper.writableDatabase)
            assertEquals(2, nueva.openHelper.writableDatabase.version)
        }

    private companion object {
        const val NOMBRE = "migracion-test.db"
    }
}
