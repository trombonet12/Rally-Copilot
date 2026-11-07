package app.trombito.rallycopilot.data.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridosRoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.math.sin

/**
 * La cadena de migraciones entera (QA-6, ARQ-6, ARQ-14, QA-7): la lista única de producción sin huecos, el salto
 * v16 -> v17, la cadena v1 -> v17 comparada con el esquema exportado y una base con datos variados.
 */
@RunWith(RobolectricTestRunner::class)
class MigracionRoomCadenaTest {
    private val contexto = ApplicationProvider.getApplicationContext<Context>()
    private val antigua = BaseAntigua(contexto)
    private var base: RallyDatabase? = null

    @After
    fun cerrar() {
        base?.close()
        base = null
        contexto.deleteDatabase(NOMBRE)
        contexto.deleteDatabase(NOMBRE_ESPERADA)
    }

    private fun abrirConTodas(): RallyDatabase =
        Room
            .databaseBuilder(contexto, RallyDatabase::class.java, NOMBRE)
            .addMigrations(*TODAS_LAS_MIGRACIONES)
            .allowMainThreadQueries()
            .build()
            .also { base = it }

    @Test
    fun `la lista de migraciones enlaza la v1 con la version actual sin huecos y cada version tiene su esquema`() {
        val ordenadas = TODAS_LAS_MIGRACIONES.sortedBy { it.startVersion }

        assertEquals(1, ordenadas.first().startVersion)
        assertEquals(RallyDatabase.VERSION, ordenadas.last().endVersion)
        ordenadas.forEach { assertEquals(it.startVersion + 1, it.endVersion) }
        ordenadas.zipWithNext { a, b -> assertEquals(a.endVersion, b.startVersion) }
        assertEquals(RallyDatabase.VERSION - 1, ordenadas.size)
        for (version in 1..RallyDatabase.VERSION) {
            assertEquals(
                version,
                antigua
                    .esquema(version)
                    .getValue("version")
                    .jsonPrimitive.content
                    .toInt(),
            )
        }
    }

    @Test
    fun `de v16 a v17 conserva las marcas con tramo o sin el y borra las huerfanas`() =
        runBlocking {
            val archivo = antigua.crear(16)
            SQLiteDatabase.openDatabase(archivo.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL(
                    "INSERT INTO tramo (id, nombre, traza, longitudM, sectores, microsectores, creadoEn) " +
                        "VALUES ('a', NULL, 'x', 1000.0, '', '', 1)",
                )
                db.execSQL("INSERT INTO marca VALUES (1, 10.0, 1, 'a', 39.7, 2.9, 'radar')")
                db.execSQL("INSERT INTO marca VALUES (2, 20.0, 2, NULL, NULL, NULL, NULL)")
                db.execSQL("INSERT INTO marca VALUES (3, 30.0, 3, 'borrado', 39.8, 2.8, NULL)")
                db.execSQL("INSERT INTO marca VALUES (4, 40.0, 4, 'otro', NULL, NULL, 'radar')")
            }

            val nueva = abrirConTodas()
            val marcas = nueva.marcasDao()

            assertEquals(
                listOf(
                    MarcaEntity(1, 10.0, 1L, "a", 39.7, 2.9, "radar"),
                    MarcaEntity(2, 20.0, 2L, null, null, null, null),
                ),
                marcas.todas(),
            )
            assertEquals(emptyList<MarcaEntity>(), marcas.deTramo("borrado"))
            val indice = "SELECT name FROM sqlite_master WHERE name = 'index_marca_tramoId'"
            nueva.openHelper.writableDatabase
                .query(indice)
                .use { assertEquals(1, it.count) }
            // La clave foránea: sin tramo no se guarda la marca, y al borrar el tramo se van las suyas.
            val huerfana = MarcaEntity(posicionM = 1.0, tiempoMs = 9L, tramoId = "no", lat = null, lon = null)
            val sinTramo = runCatching { marcas.insertar(huerfana) }
            assertTrue(sinTramo.exceptionOrNull() is SQLiteConstraintException)
            marcas.insertar(huerfana.copy(tramoId = "a"))
            assertEquals(2, marcas.deTramo("a").size)
            nueva.tramosDao().borrar("a")
            assertEquals(emptyList<MarcaEntity>(), marcas.deTramo("a"))
            assertEquals(listOf(2L), marcas.todas().map { it.id })
            assertEquals(null, nueva.recorridosDao().calibracion())
            assertEquals(RallyDatabase.VERSION, nueva.openHelper.writableDatabase.version)
        }

    @Test
    fun `la cadena completa de la v1 a la actual deja el esquema igual al exportado`() =
        runBlocking {
            antigua.crear(1)
            // Abre, migra y deja que Room valide contra el esquema final.
            abrirConTodas().openHelper.writableDatabase
            base?.close()
            base = null
            val esperada = antigua.crear(RallyDatabase.VERSION, NOMBRE_ESPERADA)

            assertEquals(describir(esperada), describir(contexto.getDatabasePath(NOMBRE)))
        }

    /** Tablas con sus columnas, claves foráneas e índices, sin depender del orden de las columnas. */
    private fun describir(archivo: File): Map<String, Set<String>> =
        SQLiteDatabase.openDatabase(archivo.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val internas = setOf("android_metadata", "sqlite_sequence", "room_master_table")
            val tablas =
                db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                    generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList()
                }
            tablas.filterNot { it in internas }.associateWith { tabla ->
                val detalles = mutableSetOf<String>()
                db.rawQuery("PRAGMA table_info(`$tabla`)", null).use { c ->
                    while (c.moveToNext()) {
                        detalles += "col ${c.getString(1)} ${c.getString(2)} notnull=${c.getInt(3)} " +
                            "def=${c.getString(4)} pk=${c.getInt(5)}"
                    }
                }
                db.rawQuery("PRAGMA foreign_key_list(`$tabla`)", null).use { c ->
                    while (c.moveToNext()) {
                        detalles += "fk ${c.getString(3)} -> ${c.getString(2)}.${c.getString(4)} " +
                            "upd=${c.getString(5)} del=${c.getString(6)}"
                    }
                }
                db.rawQuery("PRAGMA index_list(`$tabla`)", null).use { c ->
                    while (c.moveToNext()) {
                        if (c.getString(3) != "pk") detalles += "idx ${c.getString(1)} unique=${c.getInt(2)}"
                    }
                }
                detalles
            }
        }

    /**
     * Una base de la v16 con datos como los de una release real (sin ser de nadie): miles de puntos, nulos, textos
     * largos con acentos y emojis, tramos con trazas de decenas de miles de caracteres y marcas huérfanas.
     */
    private fun sembrarV16(archivo: File) {
        SQLiteDatabase.openDatabase(archivo.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.beginTransaction()
            try {
                db.execSQL("UPDATE ajustes_conduccion SET voz = NULL, casaLat = NULL, casaLon = NULL")
                db.execSQL(
                    "INSERT INTO guiado_pendiente VALUES (1, '$TEXTO_LARGO', 39.77, 2.71, 'SINUOSA', 5, " +
                        "'[$TEXTO_LARGO]')",
                )
                sembrarRecorridos(db)
                for (t in 0 until TRAMOS) sembrarTramo(db, t)
                for (n in 0 until HUERFANAS) {
                    db.execSQL(
                        "INSERT INTO marca (posicionM, tiempoMs, tramoId, lat, lon, etiqueta) " +
                            "VALUES (1.0, $n, 'borrado$n', 39.7, 2.9, NULL)",
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun sembrarRecorridos(db: SQLiteDatabase) {
        val recorrido = db.compileStatement("INSERT INTO recorrido VALUES (?, ?, ?, ?, ?)")
        val punto = db.compileStatement("INSERT INTO recorrido_punto VALUES (?, ?, ?, ?, ?, ?)")
        for (id in 1L..RECORRIDOS) {
            val enCurso = id == RECORRIDOS
            recorrido.bindLong(1, id)
            recorrido.bindLong(2, id * 10_000_000)
            if (enCurso) recorrido.bindNull(3) else recorrido.bindLong(3, id * 10_000_000 + PUNTOS * 1_000)
            recorrido.bindDouble(4, if (enCurso) 0.0 else PUNTOS * 10.0)
            if (id % 2 == 0L) recorrido.bindNull(5) else recorrido.bindString(5, TEXTO_LARGO)
            recorrido.executeInsert()
            for (orden in 0 until if (enCurso) PUNTOS / 3 else PUNTOS) {
                punto.bindLong(1, id)
                punto.bindLong(2, orden.toLong())
                punto.bindDouble(3, 39.6 + orden * 0.00009)
                punto.bindDouble(4, 2.9 + 0.0004 * sin(orden / 20.0))
                punto.bindLong(5, orden * 1_000L)
                if (orden % 7 == 0) punto.bindNull(6) else punto.bindDouble(6, 8.0 + orden % 13)
                punto.executeInsert()
            }
        }
    }

    private fun sembrarTramo(
        db: SQLiteDatabase,
        t: Int,
    ) {
        val nombre = if (t % 2 == 0) "Tramo $t $TEXTO_LARGO" else null
        db.execSQL(
            "INSERT INTO tramo (id, nombre, traza, longitudM, sectores, microsectores, creadoEn, " +
                "nombreAutomatico, favorito, ultimoUso) VALUES ('t$t', ?, '${"_p~iF~ps|U".repeat(4_000)}', 1500.5, " +
                "'375.0,750.0,1125.0', '', ${t * 1000L}, ?, ${t % 2}, ${t * 2000L})",
            arrayOf(nombre, if (t == 3) null else "Ma-10 · Sóller"),
        )
        val marcas = "0:0;1490000:90000;".repeat(1_000)
        for (n in 0 until PASADAS) {
            val conRecorrido = n % 3 != 0
            db.execSQL(
                "INSERT INTO pasada (tramoId, inicio, totalMs, marcas, recorridoId, puntosDesdeMs, puntosHastaMs) " +
                    "VALUES ('t$t', ${n * 100L}, ${90_000L + n}, '$marcas', " +
                    "${if (conRecorrido) 1 + n % 5 else "NULL"}, ${if (conRecorrido) 0 else "NULL"}, " +
                    "${if (conRecorrido) 5000 else "NULL"})",
            )
        }
        for (n in 0 until EDICIONES) {
            db.execSQL(
                "INSERT INTO edicion_nota (tramoId, posicionM, lat, lon, accion, nota, marcaTiempo) " +
                    "VALUES (${if (n % 5 == 4) "NULL" else "'t$t'"}, ${n * 10.0}, NULL, NULL, 'QUITAR', NULL, $n)",
            )
        }
        for (n in 0 until MARCAS) {
            db.execSQL(
                "INSERT INTO marca (posicionM, tiempoMs, tramoId, lat, lon, etiqueta) VALUES " +
                    "(${n * 5.0}, $n, ${if (n % 4 == 3) "NULL" else "'t$t'"}, NULL, NULL, " +
                    "${if (n % 5 == 0) "'radar'" else "NULL"})",
            )
        }
    }

    @Test
    fun `una base de la v16 con datos variados se abre tras migrar y conserva todo salvo las marcas huerfanas`() =
        runBlocking {
            sembrarV16(antigua.crear(16))

            val nueva = abrirConTodas()

            val recorridos = nueva.recorridosDao()
            assertEquals(listOf(RECORRIDOS), recorridos.idsEnCurso())
            assertEquals(PUNTOS, recorridos.puntos(1).size)
            assertEquals(PUNTOS / 3, recorridos.puntos(RECORRIDOS).size)
            assertEquals((PUNTOS + 6) / 7, recorridos.puntos(2).count { it.velocidadMs == null })
            assertEquals(TEXTO_LARGO, recorridos.recorrido(1)?.destinoNombre)
            assertEquals(null, recorridos.recorrido(2)?.destinoNombre)
            val tramos = nueva.tramosDao()
            assertEquals(TRAMOS, tramos.tramos().size)
            assertEquals(40_000, tramos.tramo("t0")?.traza?.length)
            assertEquals("Tramo 0 $TEXTO_LARGO", tramos.tramo("t0")?.nombre)
            assertEquals(null, tramos.tramo("t1")?.nombre)
            assertEquals(null, tramos.tramo("t3")?.nombreAutomatico)
            assertEquals(PASADAS, tramos.pasadas("t2").size)
            assertEquals(18_000, tramos.mejorPasada("t2")?.marcas?.length)
            assertEquals(null, tramos.pasadas("t0").first { it.recorridoId == null }.puntosDesdeMs)
            val filas = tramos.observarFilasResumen().first()
            assertEquals(List(TRAMOS) { PASADAS }, filas.map { it.pasadas })
            val ediciones = nueva.edicionesDao()
            for (t in 0 until TRAMOS) assertEquals(EDICIONES - EDICIONES / 5, ediciones.deTramo("t$t").size)
            assertEquals(TRAMOS * (EDICIONES / 5), ediciones.pendientes().size)
            // Las huérfanas no pasan a la v17; todas las demás sí.
            assertEquals(TRAMOS * MARCAS, nueva.marcasDao().todas().size)
            val ajustes = checkNotNull(nueva.ajustesConduccionDao().observar().first())
            assertEquals(null, ajustes.voz)
            assertEquals(null, ajustes.casaLat)
            assertEquals(
                TEXTO_LARGO,
                nueva
                    .guiadoPendienteDao()
                    .observar()
                    .first()
                    ?.destinoNombre,
            )

            // El factor de calibración se calcula (y se guarda) sobre los recorridos migrados.
            val repositorio = RepositorioRecorridosRoom(recorridos, Dispatchers.Unconfined)
            val factor = repositorio.factorCalibracion()
            assertEquals("5,4,3,2,1", recorridos.calibracion()?.clave)
            assertEquals(factor, repositorio.factorCalibracion())
        }

    private companion object {
        const val NOMBRE = BaseAntigua.NOMBRE
        const val NOMBRE_ESPERADA = "n.db"
        const val RECORRIDOS = 6L
        const val PUNTOS = 3_000
        const val TRAMOS = 4
        const val PASADAS = 12
        const val EDICIONES = 10
        const val MARCAS = 12
        const val HUERFANAS = 7

        /** Texto con acentos, ñ, comillas y emojis (pares sustitutos), de unos 500 caracteres. */
        val TEXTO_LARGO = "Sóller – Pollença, curva «dels Cavallers» ñandú 🏁 ".repeat(10)
    }
}
