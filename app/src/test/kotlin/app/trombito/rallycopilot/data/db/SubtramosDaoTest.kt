package app.trombito.rallycopilot.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubtramosDaoTest {
    private lateinit var base: RallyDatabase
    private lateinit var dao: SubtramosDao
    private lateinit var tramos: TramosDao
    private lateinit var recorridos: RecorridosDao

    @Before
    fun preparar() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        base = Room.inMemoryDatabaseBuilder(contexto, RallyDatabase::class.java).allowMainThreadQueries().build()
        dao = base.subtramosDao()
        tramos = base.tramosDao()
        recorridos = base.recorridosDao()
    }

    @After
    fun cerrar() = base.close()

    private fun tramo(id: String) =
        TramoEntity(
            id = id,
            nombre = null,
            traza = "x",
            longitudM = 5_000.0,
            sectores = "",
            microsectores = "",
            creadoEn = 1,
        )

    private suspend fun recorrido(): Long =
        recorridos.insertar(RecorridoEntity(inicioMs = 1, finMs = 2, distanciaM = 1_000.0, destinoNombre = null))

    private fun subtramo(
        tramoRefId: String,
        recorridoId: Long?,
        inicio: Long,
        mejorMs: Long? = null,
    ) = SubtramoEntity(
        recorridoId = recorridoId,
        tramoRefId = tramoRefId,
        desdeM = 100.0,
        hastaM = 900.5,
        longitudM = 800.5,
        inicio = inicio,
        tiempoMs = 41_250,
        mejorMs = mejorMs,
        latDesde = 39.77,
        lonDesde = 2.71,
        latHasta = 39.78,
        lonHasta = 2.72,
    )

    @Test
    fun `insertar devuelve el id y conserva todos los campos, con recorrido y mejor nulos`() =
        runBlocking {
            tramos.insertarTramo(tramo("a"))
            val id = dao.insertar(subtramo("a", recorridoId = null, inicio = 10))

            val leido = dao.observarDeTramo("a").first().single()
            assertEquals(id, leido.id)
            assertEquals(subtramo("a", null, 10).copy(id = id), leido)
            assertEquals(null, leido.recorridoId)
            assertEquals(null, leido.mejorMs)
        }

    @Test
    fun `observar por recorrido solo da los de ese recorrido, por orden de entrada`() =
        runBlocking {
            tramos.insertarTramo(tramo("a"))
            val r1 = recorrido()
            val r2 = recorrido()
            dao.insertar(subtramo("a", r1, inicio = 30))
            dao.insertar(subtramo("a", r1, inicio = 10, mejorMs = 40_000))
            dao.insertar(subtramo("a", r2, inicio = 20))

            assertEquals(listOf(10L, 30L), dao.observarDeRecorrido(r1).first().map { it.inicio })
            assertEquals(listOf(20L), dao.observarDeRecorrido(r2).first().map { it.inicio })
            assertEquals(emptyList<SubtramoEntity>(), dao.observarDeRecorrido(r2 + 1).first())
        }

    @Test
    fun `observar por tramo da los mas recientes primero y solo los de ese tramo`() =
        runBlocking {
            tramos.insertarTramo(tramo("a"))
            tramos.insertarTramo(tramo("b"))
            dao.insertar(subtramo("a", null, inicio = 10))
            dao.insertar(subtramo("a", null, inicio = 30))
            dao.insertar(subtramo("b", null, inicio = 20))
            dao.insertar(subtramo("a", null, inicio = 20))

            assertEquals(listOf(30L, 20L, 10L), dao.observarDeTramo("a").first().map { it.inicio })
            assertEquals(listOf(20L), dao.observarDeTramo("b").first().map { it.inicio })
        }

    @Test
    fun `borrar el tramo de referencia borra sus subtramos y deja los demas`() =
        runBlocking {
            tramos.insertarTramo(tramo("a"))
            tramos.insertarTramo(tramo("b"))
            val r = recorrido()
            dao.insertar(subtramo("a", r, inicio = 10))
            dao.insertar(subtramo("b", r, inicio = 20))

            tramos.borrar("a")

            assertEquals(emptyList<SubtramoEntity>(), dao.observarDeTramo("a").first())
            assertEquals(listOf("b"), dao.observarDeRecorrido(r).first().map { it.tramoRefId })
        }

    @Test
    fun `borrar el recorrido borra sus subtramos pero no los de otros ni el tramo`() =
        runBlocking {
            tramos.insertarTramo(tramo("a"))
            val r1 = recorrido()
            val r2 = recorrido()
            dao.insertar(subtramo("a", r1, inicio = 10))
            dao.insertar(subtramo("a", r2, inicio = 20))
            dao.insertar(subtramo("a", null, inicio = 30))

            recorridos.borrar(r1)

            assertEquals(emptyList<SubtramoEntity>(), dao.observarDeRecorrido(r1).first())
            assertEquals(listOf(30L, 20L), dao.observarDeTramo("a").first().map { it.inicio })
            assertTrue(tramos.tramo("a") != null)
        }

    @Test
    fun `sin el tramo de referencia no se guarda el subtramo`() {
        val resultado = runCatching { runBlocking { dao.insertar(subtramo("no", null, inicio = 1)) } }
        assertTrue(resultado.exceptionOrNull() is android.database.sqlite.SQLiteConstraintException)
    }

    @Test
    fun `todas las marcas de pasadas devuelve tramo, tiempo y curva de cada pasada de golpe`() =
        runBlocking {
            tramos.insertarTramo(tramo("a"))
            tramos.insertarTramo(tramo("b"))
            tramos.insertarPasada(PasadaEntity(tramoId = "a", inicio = 1, totalMs = 90_000, marcas = "m2"))
            tramos.insertarPasada(PasadaEntity(tramoId = "a", inicio = 2, totalMs = 80_000, marcas = "m1"))
            tramos.insertarPasada(PasadaEntity(tramoId = "b", inicio = 3, totalMs = 70_000, marcas = "m3"))

            assertEquals(
                listOf(
                    FilaMarcasPasada("a", 80_000, "m1"),
                    FilaMarcasPasada("a", 90_000, "m2"),
                    FilaMarcasPasada("b", 70_000, "m3"),
                ),
                tramos.todasLasMarcasDePasadas(),
            )
        }
}
