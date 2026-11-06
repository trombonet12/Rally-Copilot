package app.trombito.rallycopilot.ui.inicio

import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import app.trombito.rallycopilot.ui.comun.detalladaFalsa
import app.trombito.rallycopilot.ui.comun.resumenFalso
import app.trombito.rallycopilot.ui.comun.tramoFalso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InicioViewModelTest {
    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sin tramos con pasadas no hay ultima salida`() =
        runTest {
            val repositorio =
                FalsoRepositorioTramos(listOf(resumenFalso("a", pasadas = 0, mejorMs = null)))
            val vm = InicioViewModel(repositorio)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            assertNull(vm.uiState.value.ultimaSalida)
        }

    @Test
    fun `la ultima salida es el tramo usado mas recientemente con su pasada mas reciente y su mejor tiempo`() =
        runTest {
            val reciente = resumenFalso("a", "Ma-2141 · Escorca", pasadas = 2, mejorMs = 240_000L, ultimoUsoMs = 3_000L)
            val antiguo =
                resumenFalso("b", "Ma-10 · Valldemossa", pasadas = 1, mejorMs = 500_000L, ultimoUsoMs = 1_000L)
            val repositorio =
                FalsoRepositorioTramos(
                    listOf(reciente, antiguo),
                    pasadas =
                        mapOf(
                            "a" to
                                listOf(
                                    detalladaFalsa(2, "a", 245_500L, 2_000L),
                                    detalladaFalsa(1, "a", 240_000L, 1_000L, esMejor = true),
                                ),
                        ),
                    trazas = mapOf("a" to tramoFalso("a")),
                )
            val vm = InicioViewModel(repositorio)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            val salida = vm.uiState.value.ultimaSalida
            assertNotNull(salida)
            assertEquals("a", salida!!.tramoId)
            assertEquals("Ma-2141 · Escorca", salida.nombre)
            assertEquals(245_500L, salida.tiempoMs)
            assertEquals(240_000L, salida.mejorMs)
            assertEquals(3, salida.traza.size)
        }
}
