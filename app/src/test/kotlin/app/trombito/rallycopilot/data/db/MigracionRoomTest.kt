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
 * Migraciones v1 -> ... -> v8. No hay `room-testing` (MigrationTestHelper) en el proyecto: la
 * base antigua se crea con el SQL exportado en `schemas/.../N.json` y Room la abre como v8; al abrir valida el esquema
 * resultante contra `8.json` (identity hash y tablas), así que un SQL de migración distinto del esquema falla aquí.
 */
@RunWith(RobolectricTestRunner::class)
class MigracionRoomTest {
    private val contexto = ApplicationProvider.getApplicationContext<Context>()
    private var base: RallyDatabase? = null

    /**
     * Nombre corto a propósito: Robolectric pone la base en un directorio temporal cuyo nombre lleva el del test, y en
     * Windows la ruta total (más `-journal`) pasaba de MAX_PATH (260) con los tests de nombre largo, dando
     * SQLITE_CANTOPEN de forma intermitente según los dígitos aleatorios del directorio. Cada test ya tiene el suyo.
     */
    private val nombreBase = "m.db"

    @After
    fun cerrar() {
        base?.close()
        base = null
        contexto.deleteDatabase(nombreBase)
    }

    private fun esquema(version: Int): JsonObject {
        val archivo = File("schemas/app.trombito.rallycopilot.data.db.RallyDatabase/$version.json")
        return Json
            .parseToJsonElement(archivo.readText())
            .jsonObject
            .getValue("database")
            .jsonObject
    }

    private fun crearBase(version: Int): File {
        val archivo = contexto.getDatabasePath(nombreBase)
        archivo.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(archivo, null)
        val esquemaAntiguo = esquema(version)
        for (entidad in esquemaAntiguo.getValue("entities").jsonArray) {
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
        for (entidad in esquemaAntiguo.getValue("entities").jsonArray) {
            val tabla =
                entidad.jsonObject
                    .getValue("tableName")
                    .jsonPrimitive.content
            entidad.jsonObject["indices"]?.jsonArray?.forEach { indice ->
                val sql =
                    indice.jsonObject
                        .getValue("createSql")
                        .jsonPrimitive.content
                db.execSQL(sql.replace("\${TABLE_NAME}", tabla))
            }
        }
        esquemaAntiguo.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
        db.execSQL("INSERT INTO archivo_instalado VALUES ('mapa', 'mapa.pmtiles', '${"a".repeat(64)}', 123, 'v1')")
        db.execSQL("INSERT INTO ajustes_mapas VALUES (1, 0, 777)")
        db.execSQL("INSERT INTO estado_datos VALUES (1, 'v1', NULL, 'SIN_RED')")
        if (version >= 2) {
            db.execSQL(
                "INSERT INTO ajustes_conduccion (id, nivelZoom, icono, orientacion, capa, relieve) " +
                    "VALUES (1, 2, 'CASCO', 'NORTE', 'SATELITE', 1)",
            )
        }
        db.version = version
        db.close()
        return archivo
    }

    @Test
    fun `de v1 a v8 conserva los datos y crea las tablas nuevas`() =
        runBlocking {
            crearBase(1)

            val nueva =
                Room
                    .databaseBuilder(contexto, RallyDatabase::class.java, nombreBase)
                    .addMigrations(*TODAS)
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
            assertEquals(8, nueva.openHelper.writableDatabase.version)
        }

    @Test
    fun `de v2 a v3 conserva los ajustes y la fila existente queda con la placa`() =
        runBlocking {
            crearBase(2)

            val nueva =
                Room
                    .databaseBuilder(contexto, RallyDatabase::class.java, nombreBase)
                    .addMigrations(*TODAS)
                    .allowMainThreadQueries()
                    .build()
            base = nueva

            val fila = nueva.ajustesConduccionDao().observar().first()
            assertEquals(
                AjustesConduccionEntity(
                    nivelZoom = 2,
                    icono = "CASCO",
                    orientacion = "NORTE",
                    capa = "SATELITE",
                    relieve = true,
                    estiloManiobras = "PLACA",
                ),
                fila,
            )
            assertEquals(8, nueva.openHelper.writableDatabase.version)
        }

    @Test
    fun `de v3 a v4 conserva los ajustes con anticipacion normal y crea el guiado pendiente`() =
        runBlocking {
            crearBase(3)

            val nueva =
                Room
                    .databaseBuilder(contexto, RallyDatabase::class.java, nombreBase)
                    .addMigrations(*TODAS)
                    .allowMainThreadQueries()
                    .build()
            base = nueva

            assertEquals(
                AjustesConduccionEntity(
                    nivelZoom = 2,
                    icono = "CASCO",
                    orientacion = "NORTE",
                    capa = "SATELITE",
                    relieve = true,
                    estiloManiobras = "PLACA",
                    anticipacion = "NORMAL",
                    notasSimplificadas = false,
                ),
                nueva.ajustesConduccionDao().observar().first(),
            )
            assertEquals(null, nueva.guiadoPendienteDao().observar().first())
            nueva.guiadoPendienteDao().guardar(GuiadoPendienteEntity(1, "Sóller", 39.77, 2.71, "SINUOSA", 5L))
            assertEquals(
                "Sóller",
                nueva
                    .guiadoPendienteDao()
                    .observar()
                    .first()
                    ?.destinoNombre,
            )
            assertEquals(8, nueva.openHelper.writableDatabase.version)
        }

    @Test
    fun `de v4 a v5 conserva los ajustes con el copiloto en completo y sin voz elegida`() =
        runBlocking {
            crearBase(4)

            val nueva =
                Room
                    .databaseBuilder(contexto, RallyDatabase::class.java, nombreBase)
                    .addMigrations(*TODAS)
                    .allowMainThreadQueries()
                    .build()
            base = nueva

            assertEquals(
                AjustesConduccionEntity(
                    nivelZoom = 2,
                    icono = "CASCO",
                    orientacion = "NORTE",
                    capa = "SATELITE",
                    relieve = true,
                    estiloManiobras = "PLACA",
                    anticipacion = "NORMAL",
                    notasSimplificadas = false,
                    modoCopiloto = "COMPLETO",
                    voz = null,
                ),
                nueva.ajustesConduccionDao().observar().first(),
            )
            nueva.ajustesConduccionDao().guardar(
                AjustesConduccionEntity(
                    nivelZoom = 0,
                    icono = "CASCO",
                    orientacion = "NORTE",
                    capa = "SATELITE",
                    relieve = false,
                    modoCopiloto = "NOTAS",
                    voz = "SHARVARD",
                ),
            )
            assertEquals(
                "SHARVARD",
                nueva
                    .ajustesConduccionDao()
                    .observar()
                    .first()
                    ?.voz,
            )
            assertEquals(8, nueva.openHelper.writableDatabase.version)
        }

    @Test
    fun `de v5 a v6 el guiado pendiente existente queda sin paradas y admite paradas nuevas`() =
        runBlocking {
            val archivo = crearBase(5)
            SQLiteDatabase.openDatabase(archivo.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL("INSERT INTO guiado_pendiente VALUES (1, 'Sóller', 39.77, 2.71, 'SINUOSA', 5)")
            }

            val nueva =
                Room
                    .databaseBuilder(contexto, RallyDatabase::class.java, nombreBase)
                    .addMigrations(*TODAS)
                    .allowMainThreadQueries()
                    .build()
            base = nueva

            val fila = checkNotNull(nueva.guiadoPendienteDao().observar().first())
            assertEquals(GuiadoPendienteEntity(1, "Sóller", 39.77, 2.71, "SINUOSA", 5L, paradas = ""), fila)
            val json = "[{\"n\":\"Lluc\",\"lat\":39.8,\"lon\":2.9}]"
            nueva.guiadoPendienteDao().guardar(fila.copy(paradas = json))
            assertEquals(
                json,
                nueva
                    .guiadoPendienteDao()
                    .observar()
                    .first()
                    ?.paradas,
            )
            assertEquals(8, nueva.openHelper.writableDatabase.version)
        }

    @Test
    fun `de v6 a v7 conserva los datos y crea tramo y pasada con borrado en cascada`() =
        runBlocking {
            val archivo = crearBase(6)
            SQLiteDatabase.openDatabase(archivo.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL("INSERT INTO recorrido VALUES (1, 100, 200, 1500.0, 'Sóller')")
            }

            val nueva =
                Room
                    .databaseBuilder(contexto, RallyDatabase::class.java, nombreBase)
                    .addMigrations(*TODAS)
                    .allowMainThreadQueries()
                    .build()
            base = nueva

            assertEquals(1500.0, nueva.recorridosDao().recorrido(1)?.distanciaM ?: 0.0, 0.0)
            assertEquals(emptyList<TramoEntity>(), nueva.tramosDao().tramos())
            nueva.tramosDao().crear(
                TramoEntity("t1", null, "_p~iF~ps|U", 1500.0, "375.0,750.0,1125.0", "1000.0", 5L),
                PasadaEntity(tramoId = "t1", inicio = 7L, totalMs = 90_000L, marcas = "0:0;1490000:90000"),
            )
            assertEquals(90_000L, nueva.tramosDao().mejorPasada("t1")?.totalMs)
            nueva.tramosDao().borrar("t1")
            assertEquals(null, nueva.tramosDao().mejorPasada("t1"))
            assertEquals(8, nueva.openHelper.writableDatabase.version)
        }

    private fun abrirV8DesdeV7(): RallyDatabase {
        val archivo = crearBase(7)
        SQLiteDatabase.openDatabase(archivo.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO recorrido VALUES (1, 100, 200, 1500.0, NULL)")
            db.execSQL("INSERT INTO tramo VALUES ('a', 'Mi tramo', '_p~iF~ps|U', 1500.0, '', '', 5000)")
            db.execSQL("INSERT INTO tramo VALUES ('b', NULL, '_p~iF~ps|U', 900.0, '', '', 7000)")
            db.execSQL("INSERT INTO pasada VALUES (1, 'a', 9000, 90000, '0:0;1490000:90000')")
            db.execSQL("INSERT INTO pasada VALUES (2, 'a', 8000, 95000, '0:0;1490000:95000')")
            db.execSQL("INSERT INTO pasada VALUES (3, 'b', 10, 60000, '0:0;890000:60000')")
        }
        return Room
            .databaseBuilder(contexto, RallyDatabase::class.java, nombreBase)
            .addMigrations(*TODAS)
            .allowMainThreadQueries()
            .build()
            .also { base = it }
    }

    @Test
    fun `de v7 a v8 conserva tramos y pasadas con su ultimo uso y las pasadas quedan sin recorrido`() =
        runBlocking {
            val nueva = abrirV8DesdeV7()

            val a = checkNotNull(nueva.tramosDao().tramo("a"))
            assertEquals("Mi tramo", a.nombre)
            assertEquals(null, a.nombreAutomatico)
            assertEquals(false, a.favorito)
            // La última pasada manda; si su salida es anterior a la creación (relojes distintos), la creación.
            assertEquals(9000L, a.ultimoUso)
            assertEquals(7000L, nueva.tramosDao().tramo("b")?.ultimoUso)
            val pasadas = nueva.tramosDao().pasadas("a")
            assertEquals(listOf(1L, 2L), pasadas.map { it.id })
            assertEquals(listOf(null, null), pasadas.map { it.recorridoId })
            assertEquals(8, nueva.openHelper.writableDatabase.version)
        }

    @Test
    fun `tras v8 una pasada enlaza con su recorrido y al borrarlo el enlace pasa a null sin borrar la pasada`() =
        runBlocking {
            val dao = abrirV8DesdeV7().tramosDao()
            val pasada =
                PasadaEntity(
                    tramoId = "a",
                    inicio = 20_000L,
                    totalMs = 80_000L,
                    marcas = "0:0;1490000:80000",
                    recorridoId = 1L,
                    puntosDesdeMs = 10L,
                    puntosHastaMs = 90L,
                )

            val id = dao.anadirPasada(pasada)

            assertEquals(20_000L, dao.tramo("a")?.ultimoUso)
            assertEquals(1L, dao.pasadas("a").first { it.id == id }.recorridoId)
            base?.recorridosDao()?.borrar(1L)
            val sinEnlace = dao.pasadas("a").first { it.id == id }
            assertEquals(null, sinEnlace.recorridoId)
            assertEquals(90L, sinEnlace.puntosHastaMs)
            dao.borrar("a")
            assertEquals(emptyList<PasadaEntity>(), dao.pasadas("a"))
        }

    private companion object {
        val TODAS =
            arrayOf(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
            )
    }
}
