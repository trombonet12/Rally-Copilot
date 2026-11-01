package app.trombito.rallycopilot.ui.navegacion

import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EdicionParadas
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.navegacion.OrdenParadas
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
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

/** Mantener pulsado en el mapa y paradas: la tarjeta del punto y la edición de paradas en vista previa y guiado. */
@OptIn(ExperimentalCoroutinesApi::class)
class NavegacionViewModelParadasTest {
    private val buscador = FalsoBuscador()
    private val enrutador = FalsoEnrutador()
    private val control = FalsoControlNavegacion()
    private val destino = Destino("Sóller", SOLLER)
    private val valldemossa = Destino("Valldemossa", PuntoGeo(39.7106, 2.6213))
    private val inca = Destino("Inca", PuntoGeo(39.7201, 2.9112))

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun crear() = NavegacionViewModel(buscador, enrutador, control, FalsoResolvedor(), FalsoGuiadoPendiente())

    private fun TestScope.recoger(vm: NavegacionViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

    private fun rutasOk() {
        enrutador.respuestas[PerfilRuta.MAS_RAPIDA] = ResultadoRuta.Ok(rutaDePrueba(18_400.0, 1_380.0), 10)
        enrutador.respuestas[PerfilRuta.SINUOSA] = ResultadoRuta.Ok(rutaDePrueba(24_000.0, 2_100.0), 10)
    }

    @Test
    fun `mantener pulsado abre la tarjeta con el nombre del lugar`() =
        runTest {
            val vm = crear()
            recoger(vm)
            vm.onMantenerPulsado(SOLLER, "  Port de Sóller ")
            val punto = vm.uiState.value.puntoElegido!!
            assertEquals("Port de Sóller", punto.nombre)
            assertEquals(Destino("Port de Sóller", SOLLER), punto.destino)
        }

    @Test
    fun `sin nombre el destino del punto usa las coordenadas cortas`() =
        runTest {
            val vm = crear()
            recoger(vm)
            vm.onMantenerPulsado(PuntoGeo(39.5696, 2.6502), "   ")
            val punto = vm.uiState.value.puntoElegido!!
            assertNull(punto.nombre)
            assertEquals("39.5696, 2.6502", punto.destino.nombre)
        }

    @Test
    fun `cerrar la tarjeta la quita`() =
        runTest {
            val vm = crear()
            recoger(vm)
            vm.onMantenerPulsado(SOLLER, null)
            vm.cerrarPunto()
            assertNull(vm.uiState.value.puntoElegido)
        }

    @Test
    fun `ir aqui abre la vista previa hasta el punto y cierra la tarjeta`() =
        runTest {
            rutasOk()
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.onMantenerPulsado(SOLLER, "Sóller")
            vm.irAqui()
            val estado = vm.uiState.value
            assertNull(estado.puntoElegido)
            assertEquals(Destino("Sóller", SOLLER), estado.vistaPrevia?.destino)
            assertTrue(estado.vistaPrevia!!.puedeEmpezar)
        }

    @Test
    fun `anadir parada sin ruta se comporta como ir aqui`() =
        runTest {
            rutasOk()
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.onMantenerPulsado(SOLLER, "Sóller")
            vm.anadirParadaDelPunto()
            val previa = vm.uiState.value.vistaPrevia!!
            assertEquals(destino, previa.destino)
            assertTrue(previa.paradas.isEmpty())
        }

    @Test
    fun `anadir parada con una vista previa la anade y recalcula con rutaConParadas`() =
        runTest {
            rutasOk()
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.elegirDestino(destino)
            vm.onMantenerPulsado(valldemossa.punto, "Valldemossa")
            vm.anadirParadaDelPunto()
            val estado = vm.uiState.value
            assertNull(estado.puntoElegido)
            assertEquals(listOf(valldemossa), estado.vistaPrevia?.paradas)
            assertEquals(destino, estado.vistaPrevia?.destino)
            assertEquals(listOf(listOf(valldemossa.punto)), enrutador.peticionesConParadas.distinct())
            assertTrue(estado.vistaPrevia!!.puedeEmpezar)
            assertTrue(estado.puedeAnadirParada)
        }

    @Test
    fun `las paradas se quedan en el orden en que se anaden y se pueden mover y quitar`() =
        runTest {
            rutasOk()
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.elegirDestino(destino)
            vm.anadirParada(valldemossa)
            vm.anadirParada(inca)
            assertEquals(listOf(valldemossa, inca), vm.uiState.value.paradas)
            vm.moverParada(1, 0)
            assertEquals(listOf(inca, valldemossa), vm.uiState.value.paradas)
            vm.quitarParada(0)
            assertEquals(listOf(valldemossa), vm.uiState.value.paradas)
            assertEquals(
                1,
                vm.uiState.value.marcadoresParada
                    .single()
                    .numero,
            )
        }

    @Test
    fun `al empezar la ruta las paradas pasan al control`() =
        runTest {
            rutasOk()
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.elegirDestino(destino)
            vm.anadirParada(valldemossa)
            vm.empezar()
            assertEquals(listOf(listOf(valldemossa)), control.paradasAlEmpezar)
        }

    @Test
    fun `no caben mas paradas que el maximo`() =
        runTest {
            rutasOk()
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.elegirDestino(destino)
            repeat(EdicionParadas.MAX_PARADAS + 2) { vm.anadirParada(Destino("P$it", PALMA)) }
            assertEquals(EdicionParadas.MAX_PARADAS, vm.uiState.value.paradas.size)
            assertTrue(vm.uiState.value.paradasLlenas)
        }

    @Test
    fun `en el guiado las paradas se editan en el control y la tarjeta sigue abierta`() =
        runTest {
            val vm = crear()
            recoger(vm)
            control.publicar(
                EstadoNavegacion.Guiando(
                    destino,
                    PerfilRuta.MAS_RAPIDA,
                    rutaDePrueba(),
                    null,
                    false,
                    paradas = listOf(valldemossa, inca),
                    paradasHechas = 1,
                ),
            )
            vm.onMantenerPulsado(PALMA, "Palma")
            assertNotNull(vm.uiState.value.puntoElegido)
            assertTrue(vm.uiState.value.puedeAnadirParada)
            assertEquals(
                2,
                vm.uiState.value.marcadoresParada
                    .first()
                    .numero,
            )
            vm.anadirParadaDelPunto()
            vm.quitarParada(0)
            vm.moverParada(0, 1)
            assertEquals(
                listOf(
                    OrdenParadas.Anadir(Destino("Palma", PALMA)),
                    OrdenParadas.Quitar(0),
                    OrdenParadas.Mover(0, 1),
                ),
                control.ordenes,
            )
            assertNull(vm.uiState.value.puntoElegido)
        }

    @Test
    fun `planificando solo en reposo con busqueda, tarjeta o vista previa`() =
        runTest {
            rutasOk()
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            assertFalse(vm.uiState.value.planificando)
            vm.onMantenerPulsado(SOLLER, null)
            assertTrue(vm.uiState.value.planificando)
            vm.cerrarPunto()
            vm.elegirDestino(destino)
            assertTrue(vm.uiState.value.planificando)
            vm.empezar()
            assertFalse(vm.uiState.value.planificando)
        }
}
