package app.trombito.rallycopilot.data.recorridos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.data.db.RallyDatabase
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
class RepositorioRecorridosRoomTest {
    private lateinit var base: RallyDatabase
    private lateinit var repositorio: RepositorioRecorridosRoom
    private var ahora = 1_000L

    @Before
    fun preparar() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        base = Room.inMemoryDatabaseBuilder(contexto, RallyDatabase::class.java).allowMainThreadQueries().build()
        repositorio = RepositorioRecorridosRoom(base.recorridosDao(), Dispatchers.Unconfined) { ahora }
    }

    @After
    fun cerrar() = base.close()

    /** Puntos hacia el norte cada 0,001 grados (unos 111 m) desde [desde], uno por segundo. */
    private fun ruta(
        cuantos: Int,
        desde: Int = 0,
    ) = List(cuantos) { i ->
        PuntoRecorrido(
            lat = 39.5 + (desde + i) * 0.001,
            lon = 2.9,
            tiempoMs = 10_000L + (desde + i) * 1_000L,
            velocidadMs = 30.0,
        )
    }

    @Test
    fun `un recorrido de mas de 1 km se conserva con sus puntos`() =
        runBlocking {
            val id = repositorio.empezar("Sóller")
            repositorio.anadirPuntos(id, ruta(11)) // 10 tramos, 1,1 km

            val recorrido = repositorio.terminar(id)

            assertNotNull(recorrido)
            assertEquals(1_000L, recorrido?.inicioMs)
            assertEquals(20_000L, recorrido?.finMs)
            assertEquals("Sóller", recorrido?.destinoNombre)
            assertEquals(1_112.0, recorrido?.distanciaM ?: 0.0, 5.0)
            assertEquals(listOf(recorrido), repositorio.listar().first())
            assertEquals(11, repositorio.puntos(id).size)
        }

    @Test
    fun `la distancia guardada ignora los puntos de deriva a menos de 5 m`() =
        runBlocking {
            val id = repositorio.empezar()
            val tramo = ruta(11)
            // Entre cada par de puntos, una deriva a ~2 m del anterior.
            val conDeriva = tramo.flatMap { listOf(it, it.copy(lat = it.lat + 0.00002, tiempoMs = it.tiempoMs + 500)) }
            repositorio.anadirPuntos(id, conDeriva)

            val recorrido = repositorio.terminar(id)

            assertEquals(1_112.0, recorrido?.distanciaM ?: 0.0, 5.0)
        }

    @Test
    fun `un recorrido de menos de 1 km se descarta con sus puntos`() =
        runBlocking {
            val id = repositorio.empezar()
            repositorio.anadirPuntos(id, ruta(5)) // 444 m

            assertNull(repositorio.terminar(id))

            assertTrue(repositorio.listar().first().isEmpty())
            assertTrue(repositorio.puntos(id).isEmpty())
            assertNull(base.recorridosDao().recorrido(id))
        }

    @Test
    fun `un recorrido sin puntos se descarta`() =
        runBlocking {
            assertNull(repositorio.terminar(repositorio.empezar()))
        }

    @Test
    fun `los puntos se añaden a continuacion y conservan el orden`() =
        runBlocking {
            val id = repositorio.empezar()
            repositorio.anadirPuntos(id, ruta(3))
            repositorio.anadirPuntos(id, ruta(3, desde = 3))
            repositorio.anadirPuntos(id, emptyList())

            assertEquals((0 until 6).toList(), base.recorridosDao().puntos(id).map { it.orden })
            assertEquals(ruta(6), repositorio.puntos(id))
        }

    @Test
    fun `listar solo devuelve los terminados, el mas reciente primero`() =
        runBlocking {
            ahora = 1_000
            val primero = repositorio.empezar()
            repositorio.anadirPuntos(primero, ruta(12))
            ahora = 5_000
            val segundo = repositorio.empezar()
            repositorio.anadirPuntos(segundo, ruta(12))
            ahora = 9_000
            repositorio.empezar() // en curso

            repositorio.terminar(primero)
            repositorio.terminar(segundo)

            assertEquals(listOf(segundo, primero), repositorio.listar().first().map { it.id })
        }

    @Test
    fun `borrar un recorrido borra sus puntos en cascada y respeta los demas`() =
        runBlocking {
            val a = repositorio.empezar()
            val b = repositorio.empezar()
            repositorio.anadirPuntos(a, ruta(12))
            repositorio.anadirPuntos(b, ruta(12))

            repositorio.borrar(a)

            assertTrue(base.recorridosDao().puntos(a).isEmpty())
            assertEquals(12, base.recorridosDao().puntos(b).size)
        }
}
