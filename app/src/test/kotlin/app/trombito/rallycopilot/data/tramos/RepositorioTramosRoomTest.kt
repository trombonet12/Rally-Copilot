package app.trombito.rallycopilot.data.tramos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.MarcaTiempo
import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.tramos.Tramo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositorioTramosRoomTest {
    private lateinit var base: RallyDatabase
    private lateinit var repositorio: RepositorioTramosRoom

    @Before
    fun preparar() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        base = Room.inMemoryDatabaseBuilder(contexto, RallyDatabase::class.java).allowMainThreadQueries().build()
        repositorio = RepositorioTramosRoom(base.tramosDao(), Dispatchers.Unconfined)
    }

    @After
    fun cerrar() = base.close()

    /** Tramo de unos 1,9 km hacia el norte con cortes por defecto (3 sectores y 1 micro). */
    private fun tramo(id: String = "t1"): Tramo {
        val traza = List(21) { PuntoGeo(39.6 + it * 0.00085, 2.9) }
        return Tramo(id, traza, traza.zipWithNext { a, b -> a.distanciaA(b) }.sum())
    }

    /** Pasada a ritmo constante de [msPorM] ms por metro: una marca cada 5 m más las de los cortes y la meta. */
    private fun pasada(
        tramo: Tramo,
        msPorM: Double,
    ): Pasada {
        val cadaCinco = (0 until (tramo.metaM / 5).toInt()).map { it * 5.0 }
        val distancias = (cadaCinco + tramo.sectores + tramo.microsectores + tramo.metaM).distinct().sorted()
        val marcas = distancias.map { MarcaTiempo(it, Math.round(it * msPorM)) }
        return Pasada(tramo.id, marcas, marcas.last().tMs)
    }

    @Test
    fun `un tramo creado vuelve igual con su primera pasada como mejor`() =
        runBlocking {
            val t = tramo()
            val primera = pasada(t, 100.0)

            repositorio.crear(t, "Sa Calobra", 5_000L, primera, 4_000L)

            val guardado = repositorio.observar().first().single()
            assertEquals(t.id, guardado.tramo.id)
            assertEquals("Sa Calobra", guardado.nombre)
            assertEquals(5_000L, guardado.creadoEnMs)
            assertEquals(1, guardado.pasadas)
            assertEquals(primera.totalMs, guardado.mejorTotalMs)
            assertEquals(t.longitudM, guardado.tramo.longitudM, 0.0)
            assertEquals(t.sectores, guardado.tramo.sectores)
            assertEquals(t.microsectores, guardado.tramo.microsectores)
            val traza = guardado.tramo.traza
            assertEquals(t.traza.size, traza.size)
            assertEquals(t.traza.first().lat, traza.first().lat, 1e-6)
            assertEquals(t.traza.last().lon, traza.last().lon, 1e-6)
        }

    @Test
    fun `la mejor pasada es la de menor tiempo y se guardan marcas compactas que interpolan igual`() =
        runBlocking {
            val t = tramo()
            repositorio.crear(t, null, 1L, pasada(t, 100.0), 10L)
            val lenta = pasada(t, 120.0)
            val rapida = pasada(t, 80.0)
            val idLenta = repositorio.guardarPasada(lenta, 20L)
            val idRapida = repositorio.guardarPasada(rapida, 30L)
            assertTrue(idRapida > idLenta)

            val mejor = checkNotNull(repositorio.mejorPasada(t.id))

            assertEquals(rapida.totalMs, mejor.totalMs)
            assertTrue("marcas ${mejor.tiempos.size}", mejor.tiempos.size < rapida.tiempos.size)
            for (d in listOf(0.0, 333.0, t.sectores[0], 1234.5, t.metaM)) {
                assertEquals(checkNotNull(rapida.tiempoEn(d)), checkNotNull(mejor.tiempoEn(d)), 1.0)
            }
            val resumen = repositorio.observar().first().single()
            assertEquals(3, resumen.pasadas)
            val observada = repositorio.observarMejorPasada(t.id).first()
            assertEquals(rapida.totalMs, observada?.totalMs)
        }

    @Test
    fun `a igualdad de tiempo gana la primera y cronometrables trae cada tramo con su mejor`() =
        runBlocking {
            val a = tramo("a")
            val b = tramo("b")
            repositorio.crear(a, null, 1L, pasada(a, 100.0), 10L)
            repositorio.crear(b, null, 2L, pasada(b, 90.0), 10L)
            repositorio.guardarPasada(pasada(a, 100.0), 99L)
            repositorio.guardarPasada(pasada(a, 95.0), 100L)

            val lista = repositorio.cronometrables().associateBy { it.tramo.id }

            assertEquals(setOf("a", "b"), lista.keys)
            assertEquals(pasada(a, 95.0).totalMs, lista.getValue("a").mejor?.totalMs)
            assertEquals(pasada(b, 90.0).totalMs, lista.getValue("b").mejor?.totalMs)
            // El más reciente primero en el flujo de la lista.
            val ids = repositorio.observar().first().map { it.tramo.id }
            assertEquals(listOf("b", "a"), ids)
        }

    @Test
    fun `borrar el tramo borra sus pasadas y guardar en un tramo inexistente falla`() =
        runBlocking {
            val t = tramo()
            repositorio.crear(t, null, 1L, pasada(t, 100.0), 10L)
            repositorio.borrar(t.id)

            assertTrue(repositorio.observar().first().isEmpty())
            assertNull(repositorio.mejorPasada(t.id))
            val bd = base.openHelper.writableDatabase
            assertTrue(bd.query("SELECT * FROM pasada").use { it.count == 0 })
            val fallo = runCatching { repositorio.guardarPasada(pasada(t, 100.0), 5L) }.exceptionOrNull()
            assertNotNull(fallo)
            assertTrue(fallo is IllegalStateException)
        }

    @Test
    fun `una fila de tramo danada se ignora sin romper el resto`() =
        runBlocking {
            val t = tramo()
            repositorio.crear(t, null, 1L, pasada(t, 100.0), 10L)
            val bd = base.openHelper.writableDatabase
            bd.execSQL("INSERT INTO tramo VALUES ('malo', NULL, '!!', 1000.0, 'x', '', 2)")

            val cronometrables = repositorio.cronometrables()
            assertEquals(listOf(t.id), cronometrables.map { it.tramo.id })
            val guardados = repositorio.observar().first()
            assertEquals(listOf(t.id), guardados.map { it.tramo.id })
        }
}
