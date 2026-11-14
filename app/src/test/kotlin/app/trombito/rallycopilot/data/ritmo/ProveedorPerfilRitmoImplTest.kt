package app.trombito.rallycopilot.data.ritmo

import app.trombito.rallycopilot.data.recorridos.PuntoRecorrido
import app.trombito.rallycopilot.data.recorridos.Recorrido
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.domain.carreteras.CalculoCarreteras
import app.trombito.rallycopilot.domain.carreteras.MisCarreteras
import app.trombito.rallycopilot.motor.analisis.CurvasPorGrado
import app.trombito.rallycopilot.motor.analisis.Marcha
import app.trombito.rallycopilot.motor.analisis.ResumenCarreteras
import app.trombito.rallycopilot.motor.analisis.VelocidadVia
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.IdVia
import app.trombito.rallycopilot.motor.ruta.PerfilRitmo
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

private class CarreterasFalso(
    var velocidades: List<VelocidadVia> = emptyList(),
    var falla: Boolean = false,
) : CalculoCarreteras {
    var calculos = 0

    override fun observar(): Flow<MisCarreteras> =
        flow {
            calculos++
            check(!falla) { "sin vías" }
            val resumen = ResumenCarreteras(0, 0.0, CurvasPorGrado.VACIO, emptyList(), emptyList())
            emit(MisCarreteras(resumen, hayVias = true, velocidades = velocidades))
        }
}

private class RecorridosLista(
    inicial: List<Recorrido> = emptyList(),
) : RepositorioRecorridos {
    val lista = MutableStateFlow(inicial)

    override suspend fun empezar(destinoNombre: String?) = 0L

    override suspend fun anadirPuntos(
        id: Long,
        puntos: List<PuntoRecorrido>,
    ) = Unit

    override suspend fun terminar(id: Long): Recorrido? = null

    override suspend fun idsEnCurso() = emptyList<Long>()

    override fun listar(): Flow<List<Recorrido>> = lista

    override suspend fun puntos(id: Long) = emptyList<PuntoRecorrido>()

    override suspend fun borrar(id: Long) = Unit
}

class ProveedorPerfilRitmoImplTest {
    private val ma13 =
        Via(
            1,
            "secondary",
            null,
            null,
            null,
            SentidoUnico.NO,
            "Ma-13",
            null,
            listOf(PuntoGeo(39.6, 2.9), PuntoGeo(39.7, 2.9)),
        )

    /** 4 km por la Ma-13 a 30 km/h. */
    private val lenta = VelocidadVia(ma13, 2, Marcha(4_000.0, 480_000L), Marcha())

    /** Recorrido de [realS] segundos que se previó en [previstoS]. */
    private fun viaje(
        id: Long,
        realS: Double,
        previstoS: Double?,
    ) = Recorrido(id, 0L, (realS * 1_000).toLong(), 10_000.0, null, previstoS)

    @Test
    fun `construye el perfil con las velocidades por via y el factor de los recorridos`() =
        runTest {
            val carreteras = CarreterasFalso(listOf(lenta))
            val viajes = listOf(viaje(1, 1_300.0, 1_000.0), viaje(2, 1_200.0, 1_000.0), viaje(3, 1_250.0, 1_000.0))
            val recorridos = RecorridosLista(viajes)
            val proveedor = ProveedorPerfilRitmoImpl(carreteras, recorridos, backgroundScope)

            val perfil = proveedor.perfil()

            assertEquals(30.0, perfil.vias.getValue(IdVia("Ma-13")).velocidadMediaKmh, 0.1)
            assertEquals(1.25, perfil.factorPersonal, 1e-9)
        }

    @Test
    fun `sin recorridos da el perfil de partida`() =
        runTest {
            val proveedor = ProveedorPerfilRitmoImpl(CarreterasFalso(), RecorridosLista(), backgroundScope)

            assertEquals(PerfilRitmo(), proveedor.perfil())
        }

    @Test
    fun `se cachea mientras no cambien los recorridos y se recalcula al cerrar uno nuevo`() =
        runTest {
            val carreteras = CarreterasFalso(listOf(lenta))
            val recorridos = RecorridosLista(listOf(viaje(1, 1_300.0, 1_000.0)))
            val proveedor = ProveedorPerfilRitmoImpl(carreteras, recorridos, backgroundScope)

            proveedor.perfil()
            proveedor.perfil()
            assertEquals(1, carreteras.calculos)

            recorridos.lista.value = recorridos.lista.value + viaje(2, 1_300.0, 1_000.0)
            proveedor.perfil()
            assertEquals(2, carreteras.calculos)
        }

    @Test
    fun `si el calculo falla da el perfil de partida y lo reintenta la proxima vez`() =
        runTest {
            val carreteras = CarreterasFalso(listOf(lenta), falla = true)
            val recorridos = RecorridosLista(listOf(viaje(1, 1_300.0, 1_000.0)))
            val proveedor = ProveedorPerfilRitmoImpl(carreteras, recorridos, backgroundScope)

            assertEquals(PerfilRitmo(), proveedor.perfil())

            carreteras.falla = false
            assertNotNull(proveedor.perfil().vias[IdVia("Ma-13")])
            assertEquals(2, carreteras.calculos)
        }
}
