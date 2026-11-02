package app.trombito.rallycopilot.data.escucha

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.data.db.TramoEntity
import app.trombito.rallycopilot.domain.escucha.EdicionPendiente
import app.trombito.rallycopilot.motor.edicion.AccionEdicion
import app.trombito.rallycopilot.motor.edicion.NotasEditadas
import app.trombito.rallycopilot.motor.edicion.Orden
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.tramos.Tramo
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
class RepositorioEdicionesRoomTest {
    private lateinit var base: RallyDatabase
    private lateinit var repositorio: RepositorioEdicionesRoom

    private val traza = List(21) { PuntoGeo(39.6 + it * 0.00085, 2.9) }
    private val tramo = Tramo("t1", traza, traza.zipWithNext { a, b -> a.distanciaA(b) }.sum())

    @Before
    fun preparar() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        base = Room.inMemoryDatabaseBuilder(contexto, RallyDatabase::class.java).allowMainThreadQueries().build()
        repositorio = RepositorioEdicionesRoom(base.edicionesDao())
        runBlocking { base.tramosDao().insertarTramo(TramoEntity("t1", null, "x", tramo.longitudM, "", "", 1L)) }
    }

    @After
    fun cerrar() = base.close()

    private val dictada = Orden.Nota(Direccion.DERECHA, 3, distanciaSiguienteM = 150)

    @Test
    fun `una edicion dictada con tramo activo queda ligada a el con sus metros`() =
        runBlocking {
            repositorio.publicar(EdicionPendiente(dictada, 640.0, "t1", PuntoGeo(39.6, 2.9), 20L))
            repositorio.publicar(EdicionPendiente(Orden.Quitar, 300.0, "t1", null, 10L))

            val editadas = repositorio.observarDeTramo("t1").first()

            assertEquals(listOf(300.0, 640.0), editadas.map { it.posicionM })
            assertEquals(AccionEdicion.Quitar, editadas[0].accion)
            val sustituir = editadas[1].accion as AccionEdicion.Sustituir
            assertEquals(Direccion.DERECHA, sustituir.nota.direccion)
            assertEquals(3, sustituir.nota.grado)
            assertTrue(repositorio.observarPendientes().first().isEmpty())
        }

    @Test
    fun `sin tramo activo queda pendiente con su coordenada`() =
        runBlocking {
            repositorio.publicar(EdicionPendiente(Orden.Cuidado, 50.0, null, PuntoGeo(39.61, 2.9), 5L))

            val pendientes = repositorio.observarPendientes().first()

            assertEquals(1, pendientes.size)
            assertEquals(AccionEdicion.Cuidado, pendientes[0].accion)
            assertEquals(PuntoGeo(39.61, 2.9), pendientes[0].posicion)
        }

    @Test
    fun `al cerrar la conduccion las pendientes se anclan al tramo creado y el resto se descarta`() =
        runBlocking {
            // A unos 556 m de la salida y a ~10 m de la traza; la otra, a 5 km de ella.
            repositorio.publicar(EdicionPendiente(dictada, 0.0, null, PuntoGeo(39.605, 2.90012), 5L))
            repositorio.publicar(EdicionPendiente(Orden.Quitar, 0.0, null, PuntoGeo(39.7, 2.9), 6L))

            repositorio.anclarPendientes(tramo)

            val editadas = repositorio.observarDeTramo("t1").first()
            assertEquals(1, editadas.size)
            assertEquals(556.0, editadas[0].posicionM, 15.0)
            assertTrue(editadas[0].accion is AccionEdicion.Sustituir)
            assertTrue(repositorio.observarPendientes().first().isEmpty())
        }

    @Test
    fun `sin tramo las pendientes se descartan y borrarDeTramo deja las notas automaticas`() =
        runBlocking {
            repositorio.publicar(EdicionPendiente(Orden.Quitar, 0.0, null, PuntoGeo(39.605, 2.9), 5L))
            repositorio.guardar(NotasEditadas("t1", 100.0, AccionEdicion.Cuidado, 7L))

            repositorio.anclarPendientes(null)
            assertTrue(repositorio.observarPendientes().first().isEmpty())
            assertEquals(1, repositorio.observarDeTramo("t1").first().size)

            repositorio.borrarDeTramo("t1")
            assertTrue(repositorio.observarDeTramo("t1").first().isEmpty())
        }
}
