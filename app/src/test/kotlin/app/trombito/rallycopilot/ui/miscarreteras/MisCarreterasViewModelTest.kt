package app.trombito.rallycopilot.ui.miscarreteras

import android.database.sqlite.SQLiteException
import app.trombito.rallycopilot.data.carreteras.CalculadorCarreteras
import app.trombito.rallycopilot.data.carreteras.RecorridosFalsos
import app.trombito.rallycopilot.data.carreteras.lineaEste
import app.trombito.rallycopilot.data.carreteras.viaFalsa
import app.trombito.rallycopilot.data.carreteras.viasFalsas
import app.trombito.rallycopilot.data.recorridos.Recorrido
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MisCarreterasViewModelTest {
    private val ma10 = viaFalsa(1, "Ma-10", 0, 1_000)

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun TestScope.calculador(
        repo: RecorridosFalsos,
        conVias: Boolean = true,
    ) = CalculadorCarreteras(
        repo,
        viasFalsas(if (conVias) listOf(ma10) else null),
        UnconfinedTestDispatcher(testScheduler),
    )

    private fun TestScope.crear(
        repo: RecorridosFalsos,
        conVias: Boolean = true,
    ): MisCarreterasViewModel {
        val vm = MisCarreterasViewModel(calculador(repo, conVias))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `empieza cargando`() =
        runTest {
            val vm = MisCarreterasViewModel(calculador(RecorridosFalsos(emptyMap()), conVias = false))
            assertTrue(vm.uiState.value.cargando)
        }

    @Test
    fun `con recorridos da kilometros, trazos coloreables y carreteras`() =
        runTest {
            val vm = crear(RecorridosFalsos(mapOf(1L to lineaEste(0, 900), 2L to lineaEste(0, 900))))
            val estado = vm.uiState.value
            assertFalse(estado.cargando)
            assertFalse(estado.vacio)
            assertEquals(2, estado.recorridos)
            assertEquals(1_800.0, estado.metrosTotales, 20.0)
            assertEquals(2, estado.trazos.single().pasadas)
            assertTrue(estado.hayVias)
        }

    @Test
    fun `sin recorridos es el estado vacio`() =
        runTest {
            val vm = crear(RecorridosFalsos(emptyMap()))
            assertTrue(vm.uiState.value.vacio)
        }

    @Test
    fun `sin vias no hay trazos pero se avisa`() =
        runTest {
            val vm = crear(RecorridosFalsos(mapOf(1L to lineaEste(0, 900))), conVias = false)
            val estado = vm.uiState.value
            assertTrue(estado.trazos.isEmpty())
            assertFalse(estado.hayVias)
            assertEquals(1, estado.recorridos)
        }

    @Test
    fun `un fallo de la base pasa a error sin cerrar la pantalla`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            repo.fallo = SQLiteException("disco")
            val vm = crear(repo)
            assertTrue(vm.uiState.value.error)
            assertFalse(vm.uiState.value.cargando)
        }

    @Test
    fun `un recorrido nuevo actualiza el resumen`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            val vm = crear(repo)
            assertEquals(1, vm.uiState.value.recorridos)
            repo.trazas[2L] = lineaEste(0, 900)
            repo.lista.value = repo.lista.value + Recorrido(2L, 2L, 3L, 900.0, null)
            assertEquals(2, vm.uiState.value.recorridos)
        }
}
