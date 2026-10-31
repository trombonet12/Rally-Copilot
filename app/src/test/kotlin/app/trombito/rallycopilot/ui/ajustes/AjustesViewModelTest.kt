package app.trombito.rallycopilot.ui.ajustes

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.datos.CapaMapa
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AjustesViewModelTest {
    private val repositorio = FalsoRepositorioAjustesConduccion()

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun TestScope.crear(): AjustesViewModel {
        val vm = AjustesViewModel(repositorio)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `el estado refleja lo guardado`() =
        runTest {
            repositorio.mutable.value = AjustesConduccion(nivelZoom = -1, icono = IconoPosicion.CASCO)
            val vm = crear()
            assertEquals(-1, vm.uiState.value.nivelZoom)
            assertEquals(IconoPosicion.CASCO, vm.uiState.value.icono)
        }

    @Test
    fun `el nivel de zoom se acota entre -2 y 2`() =
        runTest {
            val vm = crear()
            vm.onNivelZoom(7)
            assertEquals(AjustesConduccion.NIVEL_MAX, vm.uiState.value.nivelZoom)
            vm.onNivelZoom(-9)
            assertEquals(AjustesConduccion.NIVEL_MIN, vm.uiState.value.nivelZoom)
            vm.onNivelZoom(1)
            assertEquals(1, repositorio.mutable.value.nivelZoom)
        }

    @Test
    fun `el icono y la orientacion se guardan y conservan el resto`() =
        runTest {
            repositorio.mutable.value = AjustesConduccion(capa = CapaMapa.SATELITE, relieve = true, nivelZoom = 2)
            val vm = crear()
            vm.onIcono(IconoPosicion.COCHE_RALLY)
            vm.onOrientacion(Orientacion.NORTE)
            val guardado = repositorio.mutable.value
            assertEquals(IconoPosicion.COCHE_RALLY, guardado.icono)
            assertEquals(Orientacion.NORTE, guardado.orientacion)
            assertEquals(CapaMapa.SATELITE, guardado.capa)
            assertEquals(true, guardado.relieve)
            assertEquals(2, guardado.nivelZoom)
            assertEquals(2, repositorio.guardados.size)
        }
}
