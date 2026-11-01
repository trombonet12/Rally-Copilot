package app.trombito.rallycopilot.data.navegacion

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.data.db.GuiadoPendienteEntity
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.GuiadoPendiente
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositorioGuiadoPendienteRoomTest {
    private lateinit var base: RallyDatabase
    private lateinit var repositorio: RepositorioGuiadoPendienteRoom
    private var ahora = 1_000_000L
    private val destino = Destino("Sóller", PuntoGeo(39.7667, 2.7154))

    @Before
    fun preparar() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        base = Room.inMemoryDatabaseBuilder(contexto, RallyDatabase::class.java).allowMainThreadQueries().build()
        repositorio = RepositorioGuiadoPendienteRoom(base.guiadoPendienteDao(), Dispatchers.Unconfined) { ahora }
    }

    @After
    fun cerrar() = base.close()

    @Test
    fun `sin fila no hay pendiente`() =
        runBlocking {
            assertNull(repositorio.pendiente.first())
        }

    @Test
    fun `guardar y volver a leer sella el momento`() =
        runBlocking {
            repositorio.guardar(destino, PerfilRuta.SINUOSA)

            assertEquals(
                GuiadoPendiente(destino, PerfilRuta.SINUOSA, desdeMs = 1_000_000L),
                repositorio.pendiente.first(),
            )
        }

    @Test
    fun `guardar de nuevo sustituye al anterior`() =
        runBlocking {
            repositorio.guardar(destino, PerfilRuta.SINUOSA)
            val otro = Destino("Lluc", PuntoGeo(39.82, 2.88))
            repositorio.guardar(otro, PerfilRuta.MAS_RAPIDA)

            assertEquals(otro, repositorio.pendiente.first()?.destino)
            assertEquals(PerfilRuta.MAS_RAPIDA, repositorio.pendiente.first()?.perfil)
        }

    @Test
    fun `borrar deja sin pendiente`() =
        runBlocking {
            repositorio.guardar(destino, PerfilRuta.MAS_RAPIDA)
            repositorio.borrar()

            assertNull(repositorio.pendiente.first())
        }

    @Test
    fun `un pendiente de mas de 12 horas se ignora y de justo 12 horas vale`() =
        runBlocking {
            repositorio.guardar(destino, PerfilRuta.MAS_RAPIDA)

            ahora += RepositorioGuiadoPendienteRoom.CADUCIDAD_MS
            assertEquals(destino, repositorio.pendiente.first()?.destino)

            ahora += 1
            assertNull(repositorio.pendiente.first())
        }

    @Test
    fun `un perfil desconocido en disco se ignora`() =
        runBlocking {
            base.guiadoPendienteDao().guardar(GuiadoPendienteEntity(1, "Sóller", 39.7, 2.7, "TELEPORTE", ahora))

            assertNull(repositorio.pendiente.first())
        }

    @Test
    fun `las paradas pendientes se guardan en orden y vuelven iguales`() =
        runBlocking {
            val paradas =
                listOf(
                    Destino("Cala \"Agulla\", Capdepera", PuntoGeo(39.7, 3.45)),
                    Destino("Lluc", PuntoGeo(39.82, 2.88)),
                )

            repositorio.guardar(destino, PerfilRuta.SINUOSA, paradas)

            assertEquals(
                GuiadoPendiente(destino, PerfilRuta.SINUOSA, desdeMs = 1_000_000L, paradas = paradas),
                repositorio.pendiente.first(),
            )
        }

    @Test
    fun `sin paradas la lista sale vacia y un texto ilegible no tumba el guiado`() =
        runBlocking {
            repositorio.guardar(destino, PerfilRuta.MAS_RAPIDA)
            assertEquals(emptyList<Destino>(), repositorio.pendiente.first()?.paradas)

            base.guiadoPendienteDao().guardar(
                GuiadoPendienteEntity(1, "Sóller", 39.77, 2.71, "MAS_RAPIDA", 1_000_000L, paradas = "{no es json"),
            )
            assertEquals(emptyList<Destino>(), repositorio.pendiente.first()?.paradas)
        }
}
