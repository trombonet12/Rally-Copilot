package app.trombito.rallycopilot.ui.perfil

import app.trombito.rallycopilot.domain.tramos.EstadisticasPerfil
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import app.trombito.rallycopilot.ui.comun.detalladaFalsa
import app.trombito.rallycopilot.ui.comun.resumenFalso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PerfilViewModelTest {
    private val repositorio =
        FalsoRepositorioTramos(
            resumenes =
                listOf(
                    resumenFalso("a", "Ma-2141 · Escorca", pasadas = 2, longitudM = 5_000.0),
                    resumenFalso("b", "Ma-10 · Valldemossa", pasadas = 1, longitudM = 10_000.0),
                    resumenFalso("c", "Sin pasadas", pasadas = 0, mejorMs = null),
                ),
            pasadas =
                mapOf(
                    "a" to
                        listOf(
                            detalladaFalsa(2, "a", 240_000L, 2_000L, esMejor = true),
                            detalladaFalsa(1, "a", 250_000L, 1_000L),
                        ),
                    "b" to listOf(detalladaFalsa(3, "b", 500_000L, 3_000L, esMejor = true)),
                ),
            perfil = EstadisticasPerfil(4, 42_500.0, 500L, 3_500L, 3, 3),
        )

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun TestScope.crear(repo: FalsoRepositorioTramos = repositorio): PerfilViewModel {
        val vm = PerfilViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `empieza cargando y luego agrega kilometros, tramos y pasadas`() =
        runTest {
            val vm = PerfilViewModel(repositorio)
            assertTrue(vm.uiState.value.cargando)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            val perfil = vm.uiState.value.perfil
            assertNotNull(perfil)
            assertFalse(vm.uiState.value.cargando)
            assertEquals(42.5, perfil!!.kmTotales, 1e-9)
            assertEquals(3, perfil.numeroTramos)
            assertEquals(3, perfil.numeroPasadas)
            // 19 970 m cronometrados (hasta la meta) en 990 s.
            assertEquals(19_970.0 / 990_000.0 * 3600.0, perfil.velMediaKmh!!, 1e-6)
        }

    @Test
    fun `los mejores tiempos son los de cada tramo con pasadas, el de mas pasadas primero`() =
        runTest {
            val vm = crear()
            val mejores = vm.uiState.value.mejores
            assertEquals(listOf("a", "b"), mejores.map { it.tramoId })
            assertEquals(listOf(240_000L, 500_000L), mejores.map { it.mejorMs })
            assertEquals(
                "a",
                vm.uiState.value.seleccionado
                    ?.tramoId,
            )
        }

    @Test
    fun `elegir un tramo cambia la evolucion que se ve`() =
        runTest {
            val vm = crear()
            vm.onSeleccionar("b")
            assertEquals(
                "b",
                vm.uiState.value.seleccionado
                    ?.tramoId,
            )
            assertEquals(
                1,
                vm.uiState.value.seleccionado
                    ?.evolucion
                    ?.size,
            )
            vm.onSeleccionar("a")
            assertEquals(
                listOf(true, true),
                vm.uiState.value.seleccionado
                    ?.evolucion
                    ?.map { it.esRecord },
            )
        }

    @Test
    fun `una seleccion que ya no existe vuelve al tramo por defecto`() =
        runTest {
            val vm = crear()
            vm.onSeleccionar("fantasma")
            assertEquals(
                "a",
                vm.uiState.value.seleccionado
                    ?.tramoId,
            )
        }

    @Test
    fun `el historial va de mas nuevo a mas antiguo con la diferencia contra la mejor`() =
        runTest {
            val vm = crear()
            val historial =
                vm.uiState.value.perfil!!
                    .historial
            assertEquals(listOf("3", "2", "1"), historial.map { it.pasadaId })
            assertEquals(listOf(0L, 0L, 10_000L), historial.map { it.deltaMejorMs })
        }

    @Test
    fun `sin recorridos ni pasadas el perfil esta vacio`() =
        runTest {
            val vm = crear(FalsoRepositorioTramos())
            assertFalse(vm.uiState.value.cargando)
            assertTrue(vm.uiState.value.vacio)
        }

    @Test
    fun `se actualiza cuando llega una pasada nueva`() =
        runTest {
            val vm = crear()
            repositorio.pasadasPorTramo["b"] =
                listOf(
                    detalladaFalsa(4, "b", 480_000L, 4_000L, esMejor = true),
                    detalladaFalsa(3, "b", 500_000L, 3_000L),
                )
            repositorio.resumenesMutable.value =
                repositorio.resumenesMutable.value.map { if (it.id == "b") it.copy(pasadas = 2) else it }
            assertEquals(
                4,
                vm.uiState.value.perfil
                    ?.numeroPasadas,
            )
            assertEquals(
                480_000L,
                vm.uiState.value.mejores
                    .first { it.tramoId == "b" }
                    .mejorMs,
            )
        }
}
