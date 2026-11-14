package app.trombito.rallycopilot.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v20 -> v21 (tramos de vía y llegada aprendida): los datos de Joan (recorridos, tramos, pasadas y subtramos) se
 * conservan y las columnas nuevas nacen con su valor por defecto. Nombre de base corto (MAX_PATH en Windows).
 */
@RunWith(RobolectricTestRunner::class)
class MigracionRoomV21Test {
    private val contexto = ApplicationProvider.getApplicationContext<Context>()
    private val antigua = BaseAntigua(contexto)
    private var base: RallyDatabase? = null

    @After
    fun cerrar() {
        base?.close()
        base = null
        contexto.deleteDatabase(BaseAntigua.NOMBRE)
    }

    private fun sembrarV20() {
        val archivo = antigua.crear(20)
        SQLiteDatabase.openDatabase(archivo.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO recorrido VALUES (1, 1000, 9000, 12345.5, 'Sóller')")
            db.execSQL("INSERT INTO recorrido VALUES (2, 20000, NULL, 0.0, NULL)")
            db.execSQL(
                "INSERT INTO tramo (id, nombre, traza, longitudM, sectores, microsectores, creadoEn, " +
                    "nombreAutomatico, favorito, ultimoUso) VALUES ('t1', 'Mi tramo', '_p~iF~ps|U', 1500.5, " +
                    "'375.0,750.0', '1000.0', 5, 'Ma-10 · Sóller', 1, 77)",
            )
            db.execSQL(
                "INSERT INTO pasada (tramoId, inicio, totalMs, marcas, recorridoId, puntosDesdeMs, puntosHastaMs) " +
                    "VALUES ('t1', 100, 90000, '0:0;1500000:90000;', 1, 0, 5000)",
            )
            db.execSQL(
                "INSERT INTO subtramo (recorridoId, tramoRefId, desdeM, hastaM, longitudM, inicio, tiempoMs, " +
                    "mejorMs, latDesde, lonDesde, latHasta, lonHasta) " +
                    "VALUES (1, 't1', 10.0, 510.0, 500.0, 7, 30000, NULL, 39.7, 2.9, 39.71, 2.91)",
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
    fun `los recorridos se conservan y quedan pendientes de trocear, sin tiempo previsto y sin llegada`() =
        runBlocking {
            sembrarV20()
            val recorridos = migrar().recorridosDao()

            assertEquals(
                RecorridoEntity(
                    1,
                    1000,
                    9000,
                    12345.5,
                    "Sóller",
                    tiempoPrevistoS = null,
                    viasTroceadas = false,
                    llegado = false,
                ),
                recorridos.recorrido(1),
            )
            assertEquals(null, recorridos.recorrido(2)?.finMs)
            // La columna nueva se puede escribir y leer en la base migrada.
            assertEquals(1, recorridos.marcarLlegado(1))
            assertEquals(true, recorridos.recorrido(1)?.llegado)
            // Solo los terminados entran en la cola de trocear.
            assertEquals(listOf(1L), recorridos.recorridosSinTrocear().map { it.id })
        }

    @Test
    fun `los tramos pasadas y subtramos se conservan y los tramos son viajes sin datos de via`() =
        runBlocking {
            sembrarV20()
            val nueva = migrar()

            val esperado =
                TramoEntity(
                    id = "t1",
                    nombre = "Mi tramo",
                    traza = "_p~iF~ps|U",
                    longitudM = 1500.5,
                    sectores = "375.0,750.0",
                    microsectores = "1000.0",
                    creadoEn = 5,
                    nombreAutomatico = "Ma-10 · Sóller",
                    favorito = true,
                    ultimoUso = 77,
                    tipo = "VIAJE",
                )
            assertEquals(esperado, nueva.tramosDao().tramo("t1"))
            assertEquals(listOf("t1"), nueva.tramosDao().tramosPorTipo("VIAJE").map { it.id })
            assertEquals(emptyList<TramoEntity>(), nueva.tramosDao().tramosPorTipo("VIA"))
            val pasada = nueva.tramosDao().pasadas("t1").single()
            assertEquals(90_000L, pasada.totalMs)
            assertEquals(1L, pasada.recorridoId)
            val dao = nueva.subtramosDao()
            val subtramos = dao.observarDeTramo("t1").first()
            val subtramo = subtramos.single()
            assertEquals(30_000L, subtramo.tiempoMs)
            assertEquals(null, subtramo.viaRef)
            assertEquals(null, subtramo.sentido)
        }

    @Test
    fun `la base queda en la v21 con los indices nuevos de tramo`() =
        runBlocking {
            sembrarV20()
            val db = migrar().openHelper.writableDatabase

            assertEquals(RallyDatabase.VERSION, db.version)
            assertEquals(
                setOf("index_tramo_tipo", "index_tramo_viaRef_sentido"),
                db.query("PRAGMA index_list(`tramo`)").use { c ->
                    buildSet { while (c.moveToNext()) if (c.getString(3) != "pk") add(c.getString(1)) }
                },
            )
        }
}
