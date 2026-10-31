package app.trombito.rallycopilot.ui.navegacion

import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.navegacion.GuiadoPendiente
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** «Seguir a <destino>» del [NavegacionViewModel]: el guiado que quedó a medias al morir el proceso. */
@OptIn(ExperimentalCoroutinesApi::class)
class NavegacionViewModelSeguirTest {
    private val buscador = FalsoBuscador(listOf(ResultadoBusqueda("Sóller", "pueblo", "Sóller", SOLLER)))
    private val enrutador = FalsoEnrutador()
    private val control = FalsoControlNavegacion()
    private val pendiente = FalsoGuiadoPendiente()
    private val destino = Destino("Sóller", SOLLER)

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun crear() = NavegacionViewModel(buscador, enrutador, control, FalsoResolvedor(), pendiente)

    private fun TestScope.recoger(vm: NavegacionViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

    private fun rutasOk() {
        enrutador.respuestas[PerfilRuta.MAS_RAPIDA] = ResultadoRuta.Ok(rutaDePrueba(18_400.0, 1_380.0), 10)
        enrutador.respuestas[PerfilRuta.SINUOSA] = ResultadoRuta.Ok(rutaDePrueba(24_000.0, 2_100.0), 10)
    }

    private val guiadoPendiente = GuiadoPendiente(destino, PerfilRuta.SINUOSA, 1L)

    @Test
    fun `con un guiado pendiente en reposo ofrece seguir a su destino`() =
        runTest {
            pendiente.mutable.value = guiadoPendiente
            val vm = crear()
            recoger(vm)
            assertEquals(OfertaSeguir(destino, FaseSeguir.LISTA), vm.uiState.value.seguir)
            control.publicar(EstadoNavegacion.Libre(0L, 0.0))
            assertEquals(
                destino,
                vm.uiState.value.seguir
                    ?.destino,
            )
        }

    @Test
    fun `sin guiado pendiente no se ofrece nada`() =
        runTest {
            val vm = crear()
            recoger(vm)
            assertNull(vm.uiState.value.seguir)
        }

    @Test
    fun `no se ofrece mientras se guia ni con una busqueda o una vista previa abiertas`() =
        runTest {
            pendiente.mutable.value = guiadoPendiente
            rutasOk()
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.onTexto("Sól")
            advanceTimeBy(300)
            assertNull(vm.uiState.value.seguir)
            vm.cancelar()
            assertNotNull(vm.uiState.value.seguir)
            vm.elegirDestino(destino)
            assertNull(vm.uiState.value.seguir)
            vm.empezar()
            assertNull(vm.uiState.value.seguir)
        }

    @Test
    fun `seguir recalcula desde la posicion actual con el mismo perfil y empieza el guiado`() =
        runTest {
            pendiente.mutable.value = guiadoPendiente
            enrutador.respuestas[PerfilRuta.SINUOSA] = ResultadoRuta.Ok(rutaDePrueba(24_000.0, 2_100.0), 10)
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.seguirPendiente()
            assertEquals(listOf(Triple(PALMA, SOLLER, PerfilRuta.SINUOSA)), enrutador.peticiones)
            val (d, perfil, ruta) = control.empezadas.single()
            assertEquals(destino, d)
            assertEquals(PerfilRuta.SINUOSA, perfil)
            assertEquals(24_000.0, ruta.longitudM, 0.0)
            assertTrue(vm.uiState.value.control is EstadoNavegacion.Guiando)
            assertNull(vm.uiState.value.seguir)
        }

    @Test
    fun `sin fijacion espera a la primera y entonces empieza`() =
        runTest {
            pendiente.mutable.value = guiadoPendiente
            enrutador.respuestas[PerfilRuta.SINUOSA] = ResultadoRuta.Ok(rutaDePrueba(), 10)
            val vm = crear()
            recoger(vm)
            vm.seguirPendiente()
            assertEquals(
                FaseSeguir.SIN_POSICION,
                vm.uiState.value.seguir
                    ?.fase,
            )
            assertTrue(control.empezadas.isEmpty())
            vm.onPosicion(PALMA)
            assertEquals(1, control.empezadas.size)
        }

    @Test
    fun `mientras calcula la fase es calculando y un segundo toque no repite la peticion`() =
        runTest {
            pendiente.mutable.value = guiadoPendiente
            val espera = enrutador.esperar(PerfilRuta.SINUOSA)
            enrutador.respuestas[PerfilRuta.SINUOSA] = ResultadoRuta.Ok(rutaDePrueba(), 10)
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.seguirPendiente()
            assertEquals(
                FaseSeguir.CALCULANDO,
                vm.uiState.value.seguir
                    ?.fase,
            )
            vm.seguirPendiente()
            assertEquals(1, enrutador.peticiones.size)
            espera.complete(Unit)
            assertEquals(1, control.empezadas.size)
        }

    @Test
    fun `si la ruta falla no empieza, avisa y se puede reintentar`() =
        runTest {
            pendiente.mutable.value = guiadoPendiente
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.seguirPendiente()
            assertEquals(
                FaseSeguir.ERROR,
                vm.uiState.value.seguir
                    ?.fase,
            )
            assertTrue(control.empezadas.isEmpty())
            enrutador.respuestas[PerfilRuta.SINUOSA] = ResultadoRuta.Ok(rutaDePrueba(), 10)
            vm.seguirPendiente()
            assertEquals(1, control.empezadas.size)
        }

    @Test
    fun `descartar borra el guiado pendiente y la oferta desaparece`() =
        runTest {
            pendiente.mutable.value = guiadoPendiente
            val vm = crear()
            recoger(vm)
            vm.descartarSeguir()
            assertEquals(1, pendiente.borrados)
            assertNull(vm.uiState.value.seguir)
            assertTrue(control.empezadas.isEmpty())
        }
}
