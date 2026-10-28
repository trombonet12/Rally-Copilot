package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.ui.comun.FalsaFuenteUbicacion
import app.trombito.rallycopilot.ui.comun.FalsoProveedorVia
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioDatosMapa
import app.trombito.rallycopilot.ui.comun.fijacion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class MapaViewModelTest {
    private val instalados = DatosInstalados("2026.10.1", 1_000_000, conSatelite = true)
    private val estilos = EstilosMapa(vectorial = "{\"v\":1}", satelite = "{\"s\":1}")
    private val repositorio = FalsoRepositorioDatosMapa(EstadoDatosMapa(instalados = instalados), estilos)
    private val ubicacion = FalsaFuenteUbicacion()
    private val vias = FalsoProveedorVia()

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun crear() = MapaViewModel(repositorio, ubicacion, vias)

    private fun TestScope.recoger(vm: MapaViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

    @Test
    fun `el estado inicial es de carga y se resuelve al llegar los datos`() =
        runTest {
            val vm = crear()
            assertTrue(vm.uiState.value.cargando)
            recoger(vm)
            val estado = vm.uiState.value
            assertFalse(estado.cargando)
            assertTrue(estado.hayDatos)
            assertEquals(estilos.vectorial, estado.estiloJson)
            assertNull(estado.velocidadKmh)
            assertNull(estado.posicion)
        }

    @Test
    fun `sin datos instalados no hay estilo`() =
        runTest {
            repositorio.estadoMutable.value = EstadoDatosMapa()
            repositorio.estilosDisponibles = null
            val vm = crear()
            recoger(vm)
            assertFalse(vm.uiState.value.cargando)
            assertFalse(vm.uiState.value.hayDatos)
            assertNull(vm.uiState.value.estiloJson)
        }

    @Test
    fun `las fijaciones dan velocidad en kmh y limite de la via`() =
        runTest {
            vias.via = ViaActual("Carretera de Valldemossa", "Ma-1110", 50)
            val vm = crear()
            recoger(vm)
            val f = fijacion(tiempoMs = 1_000, velocidadMs = 20.0)
            ubicacion.flujo.emit(f)
            val estado = vm.uiState.value
            assertEquals(72, estado.velocidadKmh)
            assertEquals(50, estado.limiteKmh)
            assertEquals(f, estado.posicion)
            assertEquals("Ma-1110", estado.via?.ref)
        }

    @Test
    fun `una via sin maxspeed no tiene limite`() =
        runTest {
            vias.via = ViaActual("Camí vell", null, null)
            val vm = crear()
            recoger(vm)
            ubicacion.flujo.emit(fijacion(tiempoMs = 1_000, velocidadMs = 10.0))
            assertEquals(36, vm.uiState.value.velocidadKmh)
            assertNull(vm.uiState.value.limiteKmh)
            val via = vm.uiState.value.via
            assertEquals("Camí vell", via?.nombre)
        }

    @Test
    fun `sin via cercana tampoco hay limite`() =
        runTest {
            vias.via = null
            val vm = crear()
            recoger(vm)
            ubicacion.flujo.emit(fijacion(tiempoMs = 1_000, velocidadMs = 10.0))
            assertNull(vm.uiState.value.limiteKmh)
            assertNull(vm.uiState.value.via)
        }

    @Test
    fun `sin permiso no se recogen fijaciones y al concederlo empiezan`() =
        runTest {
            ubicacion.permiso = false
            val vm = crear()
            recoger(vm)
            assertEquals(0, ubicacion.flujo.subscriptionCount.value)
            vm.onPermisoUbicacion(true)
            assertEquals(1, ubicacion.flujo.subscriptionCount.value)
            ubicacion.flujo.emit(fijacion(tiempoMs = 1_000, velocidadMs = 5.0))
            assertEquals(18, vm.uiState.value.velocidadKmh)
            vm.onPermisoUbicacion(false)
            assertEquals(0, ubicacion.flujo.subscriptionCount.value)
            assertNull(vm.uiState.value.velocidadKmh)
        }

    @Test
    fun `el GPS se deja de usar cuando nadie mira la pantalla`() =
        runTest {
            val vm = crear()
            val recogida = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            assertEquals(1, ubicacion.flujo.subscriptionCount.value)
            recogida.cancel()
            advanceTimeBy(6_000)
            runCurrent()
            assertEquals(0, ubicacion.flujo.subscriptionCount.value)
        }

    @Test
    fun `el satelite solo se usa si esta instalado`() =
        runTest {
            val vm = crear()
            recoger(vm)
            vm.onCapa(CapaMapa.SATELITE)
            assertEquals(CapaMapa.SATELITE, vm.uiState.value.capa)
            assertEquals(estilos.satelite, vm.uiState.value.estiloJson)

            repositorio.estilosDisponibles = EstilosMapa(vectorial = estilos.vectorial, satelite = null)
            repositorio.estadoMutable.value = EstadoDatosMapa(instalados = instalados.copy(conSatelite = false))
            assertFalse(vm.uiState.value.puedeSatelite)
            assertEquals(CapaMapa.VECTORIAL, vm.uiState.value.capa)
            assertEquals(estilos.vectorial, vm.uiState.value.estiloJson)
        }

    @Test
    fun `descartar el aviso lo pide al repositorio`() =
        runTest {
            repositorio.estadoMutable.value = EstadoDatosMapa(instalados = instalados, avisoActualizado = "2026.10.1")
            val vm = crear()
            recoger(vm)
            assertEquals("2026.10.1", vm.uiState.value.datos.avisoActualizado)
            vm.onDescartarAviso()
            assertEquals(1, repositorio.avisosDescartados)
        }
}
