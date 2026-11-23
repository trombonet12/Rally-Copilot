package app.trombito.rallycopilot.data.navegacion

import android.content.Context
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.conReciente
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Los tres últimos destinos: el más reciente primero, sin duplicados y sobreviviendo a un nuevo arranque. */
@RunWith(RobolectricTestRunner::class)
class AlmacenDestinosRecientesTest {
    private fun almacen() = AlmacenDestinosRecientes(RuntimeEnvironment.getApplication())

    private fun destino(
        nombre: String,
        lat: Double = 39.6,
    ) = Destino(nombre, PuntoGeo(lat, 2.9))

    @Test
    fun `al principio no hay ninguno`() =
        runTest {
            assertTrue(almacen().recientes.first().isEmpty())
        }

    @Test
    fun `el mas reciente va primero`() =
        runTest {
            val almacen = almacen()
            almacen.registrar(destino("Palma", 39.57))
            almacen.registrar(destino("Sóller", 39.77))
            assertEquals(listOf("Sóller", "Palma"), almacen.recientes.first().map { it.nombre })
        }

    @Test
    fun `guarda como mucho tres y se olvida del mas antiguo`() =
        runTest {
            val almacen = almacen()
            listOf("A", "B", "C", "D").forEachIndexed { i, n -> almacen.registrar(destino(n, 39.0 + i)) }
            assertEquals(listOf("D", "C", "B"), almacen.recientes.first().map { it.nombre })
        }

    @Test
    fun `repetir un destino lo sube arriba sin duplicarlo`() =
        runTest {
            val almacen = almacen()
            almacen.registrar(destino("A", 39.1))
            almacen.registrar(destino("B", 39.2))
            almacen.registrar(destino("A", 39.1))
            assertEquals(listOf("A", "B"), almacen.recientes.first().map { it.nombre })
        }

    @Test
    fun `el mismo punto con otro nombre tampoco se duplica`() {
        val lista = listOf(destino("Casa de Pep", 39.1)).conReciente(destino("Pep", 39.1))
        assertEquals(listOf("Pep"), lista.map { it.nombre })
    }

    @Test
    fun `sobrevive a un nuevo arranque con nombre y coordenadas`() =
        runTest {
            almacen().registrar(Destino("Port d'Andratx\tcentro", PuntoGeo(39.5448, 2.3877)))
            val leido = almacen().recientes.first().single()
            assertEquals("Port d'Andratx centro", leido.nombre)
            assertEquals(PuntoGeo(39.5448, 2.3877), leido.punto)
        }

    @Test
    fun `un texto dañado se ignora linea a linea`() =
        runTest {
            RuntimeEnvironment
                .getApplication()
                .getSharedPreferences(AlmacenDestinosRecientes.ARCHIVO, Context.MODE_PRIVATE)
                .edit()
                .putString(AlmacenDestinosRecientes.CLAVE, "basura\nx\ty\tz\n999\t2\tFuera\n39.5\t2.5\tBueno")
                .commit()
            assertEquals(listOf("Bueno"), almacen().recientes.first().map { it.nombre })
        }
}
