package app.trombito.rallycopilot.ui.mapasoffline

import app.trombito.rallycopilot.data.detalle.FalsaPrecarga
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstadoDetalle
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.precarga.EstimacionTamano
import app.trombito.rallycopilot.motor.precarga.TeselasPrecarga
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioDatosMapa
import app.trombito.rallycopilot.ui.mapa.CamaraMapa
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

@OptIn(ExperimentalCoroutinesApi::class)
class ZonaDetalleViewModelTest {
    private val repositorio = FalsoRepositorioDatosMapa()
    private val precarga = FalsaPrecarga()

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun TestScope.vm(): ZonaDetalleViewModel =
        ZonaDetalleViewModel(repositorio, precarga).also {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
        }

    @Test
    fun `sin esquinas la zona es el encuadre visible y la estimacion sale de sus teselas`() =
        runTest {
            val vm = vm()
            assertTrue(vm.uiState.value.estimacion == null)

            vm.onTamano(360.0, 760.0)

            val e = checkNotNull(vm.uiState.value.estimacion)
            assertTrue(e.teselas > 0)
            assertEquals(e.teselas * EstimacionTamano.BYTES_MEDIOS_POR_TESELA, e.bytes)
            assertFalse(vm.uiState.value.personalizada)
        }

    @Test
    fun `dos toques forman un rectangulo, el tercero empieza otro y limpiar vuelve al encuadre`() =
        runTest {
            val vm = vm()
            vm.onTamano(360.0, 760.0)

            vm.onToque(PuntoGeo(39.5600, 2.6500))
            vm.onToque(PuntoGeo(39.5700, 2.6600))
            val zona = checkNotNull(vm.uiState.value.zona)
            assertEquals(2.65, zona.oeste, 1e-9)
            assertEquals(2.66, zona.este, 1e-9)
            assertEquals(5, vm.uiState.value.contorno.size)

            vm.onToque(PuntoGeo(39.58, 2.70))
            assertEquals(1, vm.uiState.value.esquinas.size)

            vm.onLimpiar()
            assertFalse(vm.uiState.value.personalizada)
        }

    @Test
    fun `z19 da cuatro veces las teselas de z18 y el zoom se limita a 18 o 19`() =
        runTest {
            val vm = vm()
            vm.onToque(PuntoGeo(39.5600, 2.6500))
            vm.onToque(PuntoGeo(39.5700, 2.6600))
            val z18 = checkNotNull(vm.uiState.value.estimacion).teselas

            vm.onZoom(19)
            val z19 = checkNotNull(vm.uiState.value.estimacion).teselas
            vm.onZoom(25)

            assertTrue(z19 in 3 * z18..5 * z18)
            assertEquals(TeselasPrecarga.ZOOM_MAX_ZONA, vm.uiState.value.zoom)
        }

    @Test
    fun `avisa si no cabe en lo que queda del limite`() =
        runTest {
            repositorio.estadoMutable.value =
                EstadoDatosMapa(instalados = DatosInstalados("v", 4L * 1024 * 1024 * 1024 - 1_000, true))
            val vm = vm()
            vm.onToque(PuntoGeo(39.5600, 2.6500))
            vm.onToque(PuntoGeo(39.5700, 2.6600))

            assertFalse(vm.uiState.value.cabe)
        }

    @Test
    fun `confirmar encola la zona y fuera de Mallorca no hace nada`() =
        runTest {
            val vm = vm()
            vm.onToque(PuntoGeo(39.5600, 2.6500))
            vm.onToque(PuntoGeo(39.5700, 2.6600))
            vm.onZoom(19)

            assertTrue(vm.onConfirmar())
            assertEquals(19, precarga.zonas.single().second)

            vm.onToque(PuntoGeo(41.0, 1.0))
            vm.onToque(PuntoGeo(41.1, 1.1))
            assertFalse(vm.onConfirmar())
            assertEquals(1, precarga.zonas.size)
        }

    @Test
    fun `el detalle ya bajado resta del espacio`() =
        runTest {
            precarga.estadoMutable.value = EstadoDetalle(bytes = 4L * 1024 * 1024 * 1024, teselas = 10)
            val vm = vm()
            vm.onToque(PuntoGeo(39.5600, 2.6500))
            vm.onToque(PuntoGeo(39.5700, 2.6600))

            assertFalse(vm.uiState.value.cabe)
        }
}
