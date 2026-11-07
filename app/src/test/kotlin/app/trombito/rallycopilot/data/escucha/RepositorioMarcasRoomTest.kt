package app.trombito.rallycopilot.data.escucha

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.data.db.TramoEntity
import app.trombito.rallycopilot.motor.edicion.Marca
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositorioMarcasRoomTest {
    private lateinit var base: RallyDatabase
    private lateinit var repositorio: RepositorioMarcasRoom

    @Before
    fun preparar() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        base = Room.inMemoryDatabaseBuilder(contexto, RallyDatabase::class.java).allowMainThreadQueries().build()
        repositorio = RepositorioMarcasRoom(base.marcasDao())
    }

    @After
    fun cerrar() = base.close()

    @Test
    fun `una marca de un tramo existente se guarda ligada a el con su posicion`() =
        runBlocking {
            base.tramosDao().insertarTramo(TramoEntity("t1", null, "x", 1000.0, "", "", 1L))

            repositorio.guardar(Marca(120.0, 7L, "t1", "radar"), PuntoGeo(39.7, 2.9))

            val guardadas = repositorio.deTramo("t1")
            assertEquals(1, guardadas.size)
            assertEquals(Marca(120.0, 7L, "t1", "radar"), guardadas.single().marca)
            assertEquals(PuntoGeo(39.7, 2.9), guardadas.single().posicion)
        }

    @Test
    fun `una marca de un tramo borrado se guarda sin tramo en vez de perderse`() =
        runBlocking {
            // «t-borrado» no existe: la clave foránea falla y la marca se conserva con tramoId = null.
            repositorio.guardar(Marca(55.0, 9L, "t-borrado", "radar"), PuntoGeo(39.6, 2.8))

            val todas = base.marcasDao().todas()
            assertEquals(1, todas.size)
            assertNull(todas.single().tramoId)
            assertEquals(55.0, todas.single().posicionM, 0.0)
            assertEquals("radar", todas.single().etiqueta)
            assertEquals(39.6, todas.single().lat!!, 0.0)
            assertEquals(emptyList<Any>(), repositorio.deTramo("t-borrado"))
        }

    @Test
    fun `una marca sin posicion guarda lat y lon vacios`() =
        runBlocking {
            repositorio.guardar(Marca(1.0, 2L), null)

            val guardada = base.marcasDao().todas().single()
            assertNull(guardada.lat)
            assertNull(guardada.lon)
            assertNull(guardada.tramoId)
        }
}
