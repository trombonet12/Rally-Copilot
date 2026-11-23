package app.trombito.rallycopilot.ui.resumen

import app.trombito.rallycopilot.data.resumen.ViajesTerminadosEnMemoria
import app.trombito.rallycopilot.domain.resumen.ViajeTerminado
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResumenPendienteViewModelTest {
    private val viajes = ViajesTerminadosEnMemoria()
    private var ahoraMs = 1_000_000L

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun TestScope.crear(): ResumenPendienteViewModel {
        val vm = ResumenPendienteViewModel(viajes) { ahoraMs }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.pendiente.collect {} }
        return vm
    }

    @Test
    fun `sin viaje terminado no hay nada pendiente`() =
        runTest {
            assertNull(crear().pendiente.value)
        }

    @Test
    fun `un viaje recien terminado queda pendiente hasta que se consume`() =
        runTest {
            val vm = crear()
            viajes.publicar(ViajeTerminado(7L, ahoraMs))
            assertEquals(7L, vm.pendiente.value)
            vm.consumir(7L)
            assertNull(vm.pendiente.value)
        }

    @Test
    fun `consumir otro recorrido no quita el pendiente`() =
        runTest {
            val vm = crear()
            viajes.publicar(ViajeTerminado(7L, ahoraMs))
            vm.consumir(3L)
            assertEquals(7L, vm.pendiente.value)
        }

    @Test
    fun `el ultimo viaje sustituye al anterior`() =
        runTest {
            val vm = crear()
            viajes.publicar(ViajeTerminado(7L, ahoraMs))
            viajes.publicar(ViajeTerminado(8L, ahoraMs))
            assertEquals(8L, vm.pendiente.value)
        }

    @Test
    fun `un viaje de hace mas de media hora ya no se enseña solo`() =
        runTest {
            val vm = crear()
            viajes.publicar(ViajeTerminado(7L, ahoraMs - CADUCIDAD_RESUMEN_MS - 1))
            assertNull(vm.pendiente.value)
        }

    @Test
    fun `parado o sin velocidad abre el resumen y en marcha no`() {
        assertEquals(7L, resumenAlParar(7L, 0))
        assertEquals(7L, resumenAlParar(7L, VELOCIDAD_PARADO_KMH - 1))
        assertEquals(7L, resumenAlParar(7L, null))
        assertNull(resumenAlParar(7L, VELOCIDAD_PARADO_KMH))
        assertNull(resumenAlParar(7L, 80))
        assertNull(resumenAlParar(null, 0))
    }
}
