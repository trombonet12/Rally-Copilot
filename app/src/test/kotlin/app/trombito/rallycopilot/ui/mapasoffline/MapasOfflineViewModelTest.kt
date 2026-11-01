package app.trombito.rallycopilot.ui.mapasoffline

import app.trombito.rallycopilot.domain.datos.AjustesMapas
import app.trombito.rallycopilot.domain.datos.DatosInstalados
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
import org.junit.Assert.assertFalse
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
    fun `mientras se prepara la siembra la fase es preparando y despues sigue al repositorio`() =
        runTest {
            repositorio.estadoMutable.value = EstadoDatosMapa(preparando = true)
            val vm = MapasOfflineViewModel(repositorio)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            assertEquals(FaseMapas.PREPARANDO, vm.uiState.value.fase)
            repositorio.estadoMutable.value = EstadoDatosMapa(error = MotivoError.SIN_ESPACIO)
            assertEquals(FaseMapas.SIN_DATOS, vm.uiState.value.fase)
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
    fun `el limite por defecto son 4 GB y las opciones son 2 4 y 8`() {
        assertEquals(4 * BYTES_POR_GB, LIMITE_POR_DEFECTO)
        assertEquals(listOf(2, 4, 8), LIMITES_GB)
        assertTrue((LIMITE_POR_DEFECTO / BYTES_POR_GB).toInt() in LIMITES_GB)
    }

    private val sembradoSinSatelite = EstadoDatosMapa(instalados = DatosInstalados("datos-2026.11.01-1", 900, false))

    @Test
    fun `con lo sembrado y sin satelite ofrece el satelite con su tamano real`() =
        runTest {
            val repo = FalsoRepositorioDatosMapa(sembradoSinSatelite, bytesSatelite = 1_300_000_000)
            val vm = MapasOfflineViewModel(repo)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

            val estado = vm.uiState.value
            assertEquals(FaseMapas.INSTALADO, estado.fase)
            assertTrue(estado.ofreceSatelite)
            assertEquals(1_300_000_000L, estado.bytesSatelite)
            assertEquals("el aviso de datos moviles habla del satelite", 1_300_000_000L, estado.bytesPendientes)
        }

    @Test
    fun `sin red no hay tamano pero se sigue ofreciendo el satelite`() =
        runTest {
            val repo = FalsoRepositorioDatosMapa(sembradoSinSatelite, bytesSatelite = null)
            val vm = MapasOfflineViewModel(repo)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

            assertTrue(vm.uiState.value.ofreceSatelite)
            assertEquals(null, vm.uiState.value.bytesSatelite)
            assertEquals(null, vm.uiState.value.bytesPendientes)
        }

    @Test
    fun `con el satelite instalado o sin lo sembrado no se ofrece`() =
        runTest {
            val repo = FalsoRepositorioDatosMapa(sembradoSinSatelite, bytesSatelite = 0)
            val vm = MapasOfflineViewModel(repo)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

            repo.estadoMutable.value = EstadoDatosMapa(instalados = DatosInstalados("v", 900, true))
            assertEquals(FaseMapas.INSTALADO, vm.uiState.value.fase)
            assertFalse(vm.uiState.value.ofreceSatelite)

            repo.estadoMutable.value = EstadoDatosMapa()
            assertEquals(FaseMapas.SIN_DATOS, vm.uiState.value.fase)
            assertFalse(vm.uiState.value.ofreceSatelite)
        }

    @Test
    fun `con el z16 instalado y el z17 por bajar se ofrece el satelite con el tamano de lo que falta`() =
        runTest {
            val z16Puesto = EstadoDatosMapa(instalados = DatosInstalados("v1", 1_000, conSatelite = true))
            val repo = FalsoRepositorioDatosMapa(z16Puesto, bytesSatelite = 1_138_219_608)
            val vm = MapasOfflineViewModel(repo)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

            assertTrue(vm.uiState.value.ofreceSatelite)
            assertEquals(1_138_219_608L, vm.uiState.value.bytesSatelite)
            assertEquals(1_138_219_608L, vm.uiState.value.bytesPendientes)

            // Se instala el z17: cambia lo instalado, se vuelve a preguntar y ya no falta nada.
            repo.bytesSatelite = 0
            repo.estadoMutable.value = EstadoDatosMapa(instalados = DatosInstalados("v1", 1_138_220_608, true))

            assertFalse(vm.uiState.value.ofreceSatelite)
        }

    @Test
    fun `con el z16 instalado y sin red no se ofrece nada`() =
        runTest {
            val z16Puesto = EstadoDatosMapa(instalados = DatosInstalados("v1", 1_000, conSatelite = true))
            val vm = MapasOfflineViewModel(FalsoRepositorioDatosMapa(z16Puesto, bytesSatelite = null))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

            assertFalse(vm.uiState.value.ofreceSatelite)
        }

    @Test
    fun `borrar desde la pantalla llega al repositorio y repite la consulta del tamano`() =
        runTest {
            val repo = FalsoRepositorioDatosMapa(sembradoSinSatelite, bytesSatelite = null)
            val vm = MapasOfflineViewModel(repo)
            assertEquals(1, repo.consultasTamanoSatelite)
            repo.bytesSatelite = 700
            vm.onBorrar()
            assertEquals(1, repo.borrados)
            assertEquals("tras borrar vuelve a pedir el tamano si no lo tenia", 2, repo.consultasTamanoSatelite)
        }
}
