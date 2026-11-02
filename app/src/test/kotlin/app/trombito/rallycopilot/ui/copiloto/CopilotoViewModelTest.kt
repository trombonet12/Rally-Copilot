package app.trombito.rallycopilot.ui.copiloto

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.FalsoControlOrdenes
import app.trombito.rallycopilot.ui.comun.FalsoLocutor
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioAjustesConduccion
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CopilotoViewModelTest {
    private val repositorio = FalsoRepositorioAjustesConduccion()
    private val locutor = FalsoLocutor()
    private val ordenes = FalsoControlOrdenes()

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun TestScope.crear(): CopilotoViewModel {
        val vm = CopilotoViewModel(repositorio, locutor, ordenes)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `antes de leer los ajustes no se pregunta la voz`() {
        assertFalse(CopilotoUiState().cargado)
        assertFalse(CopilotoUiState().pedirVoz)
    }

    @Test
    fun `el modo inicial es completo y sin voz elegida se pide la voz`() =
        runTest {
            val vm = crear()
            val estado = vm.uiState.value
            assertTrue(estado.cargado)
            assertEquals(ModoCopiloto.COMPLETO, estado.modo)
            assertNull(estado.voz)
            assertTrue(estado.pedirVoz)
        }

    @Test
    fun `con la voz ya elegida no se pide`() =
        runTest {
            repositorio.mutable.value = AjustesConduccion(voz = VozCopiloto.SHARVARD)
            val vm = crear()
            assertEquals(VozCopiloto.SHARVARD, vm.uiState.value.voz)
            assertFalse(vm.uiState.value.pedirVoz)
        }

    @Test
    fun `elegir la voz la guarda y deja de pedirla`() =
        runTest {
            val vm = crear()
            vm.onElegirVoz(VozCopiloto.SHARVARD)
            assertEquals(VozCopiloto.SHARVARD, repositorio.mutable.value.voz)
            assertFalse(vm.uiState.value.pedirVoz)
        }

    @Test
    fun `posponer oculta el dialogo sin guardar ninguna voz`() =
        runTest {
            val vm = crear()
            vm.onPosponerVoz()
            assertFalse(vm.uiState.value.pedirVoz)
            assertNull(repositorio.mutable.value.voz)
            assertTrue(repositorio.guardados.isEmpty())
        }

    @Test
    fun `una instancia nueva vuelve a preguntar tras posponer`() =
        runTest {
            crear().onPosponerVoz()
            assertTrue(crear().uiState.value.pedirVoz)
        }

    @Test
    fun `cambiar el modo lo guarda conservando lo demas`() =
        runTest {
            repositorio.mutable.value = AjustesConduccion(nivelZoom = 2, voz = VozCopiloto.DAVEFX)
            val vm = crear()
            vm.onModo(ModoCopiloto.NOTAS)
            assertEquals(ModoCopiloto.NOTAS, vm.uiState.value.modo)
            val guardado = repositorio.mutable.value
            assertEquals(ModoCopiloto.NOTAS, guardado.modoCopiloto)
            assertEquals(2, guardado.nivelZoom)
            assertEquals(VozCopiloto.DAVEFX, guardado.voz)
        }

    @Test
    fun `probar llama al locutor con esa voz y no guarda nada`() =
        runTest {
            val vm = crear()
            vm.onProbarVoz(VozCopiloto.DAVEFX)
            vm.onProbarVoz(VozCopiloto.SHARVARD)
            assertEquals(listOf(VozCopiloto.DAVEFX, VozCopiloto.SHARVARD), locutor.probadas)
            assertTrue(repositorio.guardados.isEmpty())
        }

    @Test
    fun `el microfono pide escuchar una orden y el estado refleja que escucha`() =
        runTest {
            val vm = crear()
            assertFalse(vm.uiState.value.escuchando)

            vm.onEscuchar()
            ordenes.estaEscuchando.value = true

            assertEquals(1, ordenes.peticiones)
            assertTrue(vm.uiState.value.escuchando)
        }

    @Test
    fun `las notas grandes se guardan, se recuerdan en el estado y conservan lo demas`() =
        runTest {
            repositorio.mutable.value = AjustesConduccion(modoCopiloto = ModoCopiloto.NOTAS)
            val vm = crear()
            assertFalse(vm.uiState.value.notasGrandes)

            vm.onNotasGrandes(true)

            assertTrue(vm.uiState.value.notasGrandes)
            assertTrue(repositorio.mutable.value.notasGrandes)
            assertEquals(ModoCopiloto.NOTAS, repositorio.mutable.value.modoCopiloto)
        }
}
