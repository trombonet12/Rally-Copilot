package app.trombito.rallycopilot.ui.mistramos

import android.database.sqlite.SQLiteException
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import app.trombito.rallycopilot.ui.comun.resumenFalso
import app.trombito.rallycopilot.ui.comun.tramoFalso
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MisTramosViewModelTest {
    private val a = resumenFalso("a", "Ma-2141 · Escorca", pasadas = 3, favorito = true, ultimoUsoMs = 3_000L)
    private val b = resumenFalso("b", "Ma-10 · Valldemossa", pasadas = 1, ultimoUsoMs = 2_000L)
    private val c = resumenFalso("c", "Ma-13 · Pollença", pasadas = 2, ultimoUsoMs = 1_000L)
    private val repositorio = FalsoRepositorioTramos(listOf(a, b, c))

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun TestScope.crear(): MisTramosViewModel {
        val vm = MisTramosViewModel(repositorio)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `empieza cargando y luego muestra todos los tramos`() =
        runTest {
            val vm = MisTramosViewModel(repositorio)
            assertTrue(vm.uiState.value.cargando)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            assertFalse(vm.uiState.value.cargando)
            assertEquals(FiltroTramos.TODOS, vm.uiState.value.filtro)
            assertEquals(
                listOf("a", "b", "c"),
                vm.uiState.value.tramos
                    .map { it.id },
            )
        }

    @Test
    fun `los filtros dejan repetidos y favoritos`() =
        runTest {
            val vm = crear()
            vm.onFiltro(FiltroTramos.REPETIDOS)
            assertEquals(
                listOf("a", "c"),
                vm.uiState.value.tramos
                    .map { it.id },
            )
            assertEquals(FiltroTramos.REPETIDOS, vm.uiState.value.filtro)
            vm.onFiltro(FiltroTramos.FAVORITOS)
            assertEquals(
                listOf("a"),
                vm.uiState.value.tramos
                    .map { it.id },
            )
            vm.onFiltro(FiltroTramos.TODOS)
            assertEquals(3, vm.uiState.value.tramos.size)
        }

    @Test
    fun `marcar favorito alterna el estado y se refleja en la lista`() =
        runTest {
            val vm = crear()
            vm.onFavorito(b)
            assertEquals(listOf("b" to true), repositorio.favoritos)
            assertTrue(
                vm.uiState.value.tramos
                    .first { it.id == "b" }
                    .favorito,
            )
            vm.onFavorito(a)
            assertEquals("a" to false, repositorio.favoritos.last())
            assertFalse(
                vm.uiState.value.tramos
                    .first { it.id == "a" }
                    .favorito,
            )
        }

    @Test
    fun `borrar oculta la fila sin borrar todavia y deshacer la devuelve`() =
        runTest {
            val vm = crear()
            vm.onBorrar(b)
            assertEquals(
                listOf("a", "c"),
                vm.uiState.value.tramos
                    .map { it.id },
            )
            assertEquals(BorradoPendiente("b", "Ma-10 · Valldemossa"), vm.uiState.value.borradoPendiente)
            assertTrue(repositorio.borrados.isEmpty())
            vm.onDeshacer()
            assertEquals(
                listOf("a", "b", "c"),
                vm.uiState.value.tramos
                    .map { it.id },
            )
            assertNull(vm.uiState.value.borradoPendiente)
            vm.onBorradoConfirmado()
            assertTrue("Tras deshacer no queda nada que confirmar", repositorio.borrados.isEmpty())
        }

    @Test
    fun `confirmar el borrado lo hace de verdad`() =
        runTest {
            val vm = crear()
            vm.onBorrar(b)
            vm.onBorradoConfirmado()
            assertEquals(listOf("b"), repositorio.borrados)
            assertEquals(
                listOf("a", "c"),
                vm.uiState.value.tramos
                    .map { it.id },
            )
            assertNull(vm.uiState.value.borradoPendiente)
        }

    @Test
    fun `si la base falla al borrar el tramo vuelve a la lista y se avisa sin cerrar la app`() =
        runTest {
            val vm = crear()
            repositorio.falloAlBorrar = SQLiteException("disco lleno")
            vm.onBorrar(b)
            assertEquals(
                listOf("a", "c"),
                vm.uiState.value.tramos
                    .map { it.id },
            )
            vm.onBorradoConfirmado()
            assertEquals(
                listOf("a", "b", "c"),
                vm.uiState.value.tramos
                    .map { it.id },
            )
            assertEquals("Ma-10 · Valldemossa", vm.uiState.value.falloBorrado)
            assertNull(vm.uiState.value.borradoPendiente)
            assertTrue(repositorio.borrados.isEmpty())
            vm.onFalloVisto()
            assertNull(vm.uiState.value.falloBorrado)
        }

    @Test
    fun `un fallo de estado de la base al borrar tampoco cierra la app`() =
        runTest {
            val vm = crear()
            repositorio.falloAlBorrar = IllegalStateException("base cerrada")
            vm.onBorrar(c)
            vm.onSalir()
            assertEquals(
                listOf("a", "b", "c"),
                vm.uiState.value.tramos
                    .map { it.id },
            )
            assertEquals("Ma-13 · Pollença", vm.uiState.value.falloBorrado)
            // Y se puede volver a intentar cuando la base responde
            repositorio.falloAlBorrar = null
            vm.onFalloVisto()
            vm.onBorrar(c)
            vm.onBorradoConfirmado()
            assertEquals(listOf("c"), repositorio.borrados)
            assertNull(vm.uiState.value.falloBorrado)
        }

    @Test
    fun `borrar otro tramo confirma el anterior`() =
        runTest {
            val vm = crear()
            vm.onBorrar(b)
            vm.onBorrar(c)
            assertEquals(listOf("b"), repositorio.borrados)
            assertEquals(
                "c",
                vm.uiState.value.borradoPendiente
                    ?.id,
            )
            vm.onDeshacer()
            assertEquals(
                listOf("a", "c"),
                vm.uiState.value.tramos
                    .map { it.id },
            )
        }

    @Test
    fun `salir de la pantalla con un borrado pendiente lo confirma`() =
        runTest {
            val vm = crear()
            vm.onBorrar(a)
            vm.onSalir()
            assertEquals(listOf("a"), repositorio.borrados)
        }

    @Test
    fun `renombrar abre el dialogo y guarda el nombre sin espacios sobrantes`() =
        runTest {
            val vm = crear()
            vm.onPedirRenombrar(c)
            assertEquals(
                "c",
                vm.uiState.value.renombrando
                    ?.id,
            )
            vm.onRenombrar("  Mi curva favorita ")
            assertEquals(listOf("c" to "Mi curva favorita"), repositorio.renombrados)
            assertNull(vm.uiState.value.renombrando)
            assertEquals(
                "Mi curva favorita",
                vm.uiState.value.tramos
                    .first { it.id == "c" }
                    .nombre,
            )
        }

    @Test
    fun `renombrar con el campo vacio vuelve al nombre automatico`() =
        runTest {
            val vm = crear()
            vm.onPedirRenombrar(c)
            vm.onRenombrar("   ")
            assertEquals(listOf("c" to null), repositorio.renombrados)
            assertNotNull(
                vm.uiState.value.tramos
                    .firstOrNull { it.id == "c" },
            )
        }

    @Test
    fun `cancelar el renombrado cierra el dialogo sin guardar`() =
        runTest {
            val vm = crear()
            vm.onPedirRenombrar(a)
            vm.onCancelarRenombrar()
            assertNull(vm.uiState.value.renombrando)
            assertTrue(repositorio.renombrados.isEmpty())
        }

    @Test
    fun `sin tramos la lista queda vacia y ya no esta cargando`() =
        runTest {
            val vacio = FalsoRepositorioTramos()
            val vm = MisTramosViewModel(vacio)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            assertFalse(vm.uiState.value.cargando)
            assertTrue(
                vm.uiState.value.tramos
                    .isEmpty(),
            )
        }

    @Test
    fun `lee la silueta de cada tramo una sola vez para las miniaturas`() =
        runTest {
            val con = FalsoRepositorioTramos(listOf(a, b), trazas = mapOf("a" to tramoFalso("a")))
            val vm = MisTramosViewModel(con)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            // Solo hay silueta del tramo que existe en el almacén; el otro sigue sin ella (hueco con la bandera).
            assertEquals(setOf("a"), vm.uiState.value.trazas.keys)
            assertEquals(
                3,
                vm.uiState.value.trazas
                    .getValue("a")
                    .size,
            )
        }
}
