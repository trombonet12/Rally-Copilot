package app.trombito.rallycopilot.ui.mapasoffline

import app.trombito.rallycopilot.domain.datos.AjustesMapas
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.MotivoError
import app.trombito.rallycopilot.domain.datos.ProgresoDescarga
import app.trombito.rallycopilot.motor.datos.LIMITE_POR_DEFECTO
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioDatosMapa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapasOfflineViewModelTest {
    private val repositorio = FalsoRepositorioDatosMapa()

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    @Test
    fun `el estado pasa de cargando a sin datos`() =
        runTest {
            val vm = MapasOfflineViewModel(repositorio)
            assertEquals(FaseMapas.CARGANDO, vm.uiState.value.fase)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            assertEquals(FaseMapas.SIN_DATOS, vm.uiState.value.fase)
            assertEquals(LIMITE_POR_DEFECTO, vm.uiState.value.ajustes.limiteBytes)
            assertTrue(vm.uiState.value.ajustes.soloWifi)
        }

    @Test
    fun `las fases siguen al repositorio`() =
        runTest {
            val vm = MapasOfflineViewModel(repositorio)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            repositorio.estadoMutable.value = EstadoDatosMapa(descarga = ProgresoDescarga(25, 100, false))
            assertEquals(FaseMapas.DESCARGANDO, vm.uiState.value.fase)
            assertEquals(25, vm.uiState.value.porcentaje)
            repositorio.estadoMutable.value = EstadoDatosMapa(error = MotivoError.SIN_ESPACIO)
            assertEquals(FaseMapas.SIN_DATOS, vm.uiState.value.fase)
            assertEquals(MotivoError.SIN_ESPACIO, vm.uiState.value.datos.error)
        }

    @Test
    fun `esperando red conserva el ultimo progreso conocido y lo olvida al terminar`() =
        runTest {
            val vm = MapasOfflineViewModel(repositorio)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            repositorio.estadoMutable.value = EstadoDatosMapa(descarga = ProgresoDescarga(250, 1_000, false))
            assertEquals(25, vm.uiState.value.porcentaje)
            repositorio.estadoMutable.value = EstadoDatosMapa(descarga = ProgresoDescarga(0, 0, true))
            assertEquals(ProgresoDescarga(250, 1_000, false), vm.uiState.value.progreso)
            assertEquals(25, vm.uiState.value.porcentaje)
            repositorio.estadoMutable.value = EstadoDatosMapa()
            assertEquals(null, vm.uiState.value.ultimoProgreso)
            repositorio.estadoMutable.value = EstadoDatosMapa(descarga = ProgresoDescarga(0, 0, true))
            assertEquals(null, vm.uiState.value.porcentaje)
        }

    @Test
    fun `las acciones llegan al repositorio`() =
        runTest {
            val vm = MapasOfflineViewModel(repositorio)
            vm.onDescargar()
            vm.onCancelar()
            vm.onBorrar()
            assertEquals(1, repositorio.descargas)
            assertEquals(1, repositorio.cancelaciones)
            assertEquals(1, repositorio.borrados)
        }

    @Test
    fun `solo WiFi y limite se guardan sin perder el otro ajuste`() =
        runTest {
            val vm = MapasOfflineViewModel(repositorio)
            vm.onSoloWifi(false)
            val porDefecto = AjustesMapas(soloWifi = false, limiteBytes = LIMITE_POR_DEFECTO)
            assertEquals(porDefecto, repositorio.ajustesMutable.value)
            vm.onLimiteGb(8)
            val ochoGb = AjustesMapas(soloWifi = false, limiteBytes = 8 * BYTES_POR_GB)
            assertEquals(ochoGb, repositorio.ajustesMutable.value)
            vm.onSoloWifi(true)
            assertTrue(repositorio.ajustesMutable.value.soloWifi)
            assertEquals(8 * BYTES_POR_GB, repositorio.ajustesMutable.value.limiteBytes)
        }

    @Test
    fun `el limite por defecto es una de las opciones`() {
        assertTrue((LIMITE_POR_DEFECTO / BYTES_POR_GB).toInt() in LIMITES_GB)
    }
}
