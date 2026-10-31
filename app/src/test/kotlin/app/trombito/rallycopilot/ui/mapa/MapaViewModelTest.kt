package app.trombito.rallycopilot.ui.mapa

import androidx.lifecycle.SavedStateHandle
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.ui.comun.FalsaFuenteUbicacion
import app.trombito.rallycopilot.ui.comun.FalsoProveedorVia
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioAjustesConduccion
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
    private val ajustes = FalsoRepositorioAjustesConduccion()

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun crear(guardado: SavedStateHandle = SavedStateHandle()) =
        MapaViewModel(repositorio, ubicacion, vias, ajustes, guardado)

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
    fun `la camara y el seguimiento sobreviven a recrear la actividad`() =
        runTest {
            val guardado = SavedStateHandle()
            val vm = crear(guardado)
            recoger(vm)
            assertNull(vm.camaraGuardada)
            assertTrue(vm.uiState.value.siguiendo)
            val camara = CamaraMapa(39.7, 2.9, 15.0)
            vm.onCamara(camara)
            vm.onSeguimientoPerdido()
            assertFalse(vm.uiState.value.siguiendo)

            // La actividad se recrea: otro ViewModel con el mismo estado guardado.
            val nuevo = crear(guardado)
            recoger(nuevo)
            assertEquals(camara, nuevo.camaraGuardada)
            assertFalse(nuevo.uiState.value.siguiendo)
            nuevo.onRecentrar()
            assertTrue(nuevo.uiState.value.siguiendo)
            assertEquals(camara, crear(guardado).camaraGuardada)
        }

    @Test
    fun `recentrar recupera el seguimiento y se conserva el zoom`() =
        runTest {
            val vm = crear()
            recoger(vm)
            vm.onCamara(CamaraMapa(39.7, 2.9, 13.5))
            vm.onSeguimientoPerdido()
            vm.onRecentrar()
            assertTrue(vm.uiState.value.siguiendo)
            assertEquals(13.5, vm.camaraGuardada?.zoom ?: 0.0, 0.0)
        }

    @Test
    fun `datos instalados con estilo ilegible se marcan como danados`() =
        runTest {
            repositorio.estilosDisponibles = null
            val vm = crear()
            recoger(vm)
            assertFalse(vm.uiState.value.hayDatos)
            assertTrue(vm.uiState.value.datosDanados)
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

    @Test
    fun `los ajustes de conduccion llegan al estado`() =
        runTest {
            ajustes.mutable.value =
                AjustesConduccion(
                    nivelZoom = 1,
                    icono = IconoPosicion.COCHE_RALLY,
                    orientacion = Orientacion.NORTE,
                    capa = CapaMapa.SATELITE,
                    relieve = true,
                )
            val vm = crear()
            recoger(vm)
            val estado = vm.uiState.value
            assertEquals(CapaMapa.SATELITE, estado.capaElegida)
            assertTrue(estado.relieve)
            assertEquals(Orientacion.NORTE, estado.orientacion)
            assertEquals(IconoPosicion.COCHE_RALLY, estado.icono)
        }

    @Test
    fun `capa relieve y orientacion se guardan en los ajustes sin tocar el resto`() =
        runTest {
            val vm = crear()
            recoger(vm)
            vm.onCapa(CapaMapa.SATELITE)
            vm.onRelieve(true)
            vm.onOrientacion(Orientacion.NORTE)
            val guardado = ajustes.mutable.value
            assertEquals(CapaMapa.SATELITE, guardado.capa)
            assertTrue(guardado.relieve)
            assertEquals(Orientacion.NORTE, guardado.orientacion)
            assertEquals(AjustesConduccion().icono, guardado.icono)
            assertEquals(3, ajustes.guardados.size)
            vm.onRelieve(false)
            assertFalse(vm.uiState.value.relieve)
        }

    @Test
    fun `sin velocidad conocida no hay zoom automatico`() =
        runTest {
            val vm = crear()
            recoger(vm)
            assertNull(vm.uiState.value.zoomObjetivo)
        }

    @Test
    fun `al pasar de 30 a 90 kmh el zoom objetivo se aleja poco a poco`() =
        runTest {
            val vm = crear()
            recoger(vm)
            ubicacion.flujo.emit(fijacion(tiempoMs = 1_000, velocidadMs = 30 / 3.6))
            assertEquals(16.5, vm.uiState.value.zoomObjetivo ?: 0.0, 0.01)
            ubicacion.flujo.emit(fijacion(tiempoMs = 2_000, velocidadMs = 90 / 3.6))
            // Limitado a 0,5 niveles por segundo: no salta de golpe a 15.
            assertEquals(16.0, vm.uiState.value.zoomObjetivo ?: 0.0, 0.01)
            (3..6).forEach { ubicacion.flujo.emit(fijacion(tiempoMs = it * 1_000L, velocidadMs = 90 / 3.6)) }
            assertEquals(15.0, vm.uiState.value.zoomObjetivo ?: 0.0, 0.01)
        }

    @Test
    fun `el ajuste de zoom desplaza el zoom automatico`() =
        runTest {
            ajustes.mutable.value = AjustesConduccion(nivelZoom = AjustesConduccion.NIVEL_MAX)
            val vm = crear()
            recoger(vm)
            ubicacion.flujo.emit(fijacion(tiempoMs = 1_000, velocidadMs = 50 / 3.6))
            // 16,0 a 50 km/h más 2 niveles de ajuste (0,5 de zoom cada uno).
            assertEquals(17.0, vm.uiState.value.zoomObjetivo ?: 0.0, 0.01)
        }

    @Test
    fun `un pellizco pausa el zoom automatico ocho segundos y despues se retoma`() =
        runTest {
            val vm = crear()
            recoger(vm)
            ubicacion.flujo.emit(fijacion(tiempoMs = 1_000, velocidadMs = 90 / 3.6))
            assertEquals(15.0, vm.uiState.value.zoomObjetivo ?: 0.0, 0.01)
            vm.onZoomManual(zoom = 17.2, ahoraMs = 2_000)
            // En pausa se mantiene el zoom del usuario aunque la velocidad pida otro.
            ubicacion.flujo.emit(fijacion(tiempoMs = 3_000, velocidadMs = 90 / 3.6))
            assertEquals(17.2, vm.uiState.value.zoomObjetivo ?: 0.0, 0.01)
            ubicacion.flujo.emit(fijacion(tiempoMs = 9_000, velocidadMs = 90 / 3.6))
            assertEquals(17.2, vm.uiState.value.zoomObjetivo ?: 0.0, 0.01)
            // Pasados los 8 s (hasta 10 000 ms) vuelve al automático a ritmo limitado.
            ubicacion.flujo.emit(fijacion(tiempoMs = 10_000, velocidadMs = 90 / 3.6))
            ubicacion.flujo.emit(fijacion(tiempoMs = 11_000, velocidadMs = 90 / 3.6))
            val retomado = vm.uiState.value.zoomObjetivo ?: 0.0
            assertTrue("Debe bajar hacia 15 sin saltar: $retomado", retomado < 17.2 && retomado > 15.0)
        }
}
