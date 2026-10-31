package app.trombito.rallycopilot.ui.navegacion

import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavegacionViewModelTest {
    private val buscador = FalsoBuscador()
    private val enrutador = FalsoEnrutador()
    private val control = FalsoControlNavegacion()
    private val soller = ResultadoBusqueda("Sóller", "pueblo", "Sóller", SOLLER)
    private val destino = Destino("Sóller", SOLLER)

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun crear() = NavegacionViewModel(buscador, enrutador, control)

    private fun TestScope.recoger(vm: NavegacionViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

    private fun rutasOk() {
        enrutador.respuestas[PerfilRuta.MAS_RAPIDA] = ResultadoRuta.Ok(rutaDePrueba(18_400.0, 1_380.0), 10)
        enrutador.respuestas[PerfilRuta.SINUOSA] = ResultadoRuta.Ok(rutaDePrueba(24_000.0, 2_100.0), 10)
    }

    @Test
    fun `al teclear espera unos 250 ms antes de buscar y busca con la ultima posicion`() =
        runTest {
            buscador.resultados = listOf(soller)
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.onTexto("Sól")
            advanceTimeBy(200)
            assertTrue(buscador.consultas.isEmpty())
            advanceTimeBy(100)
            assertEquals(listOf("Sól" to PALMA), buscador.consultas)
            assertEquals(BusquedaDestino.Resultados(listOf(soller)), vm.uiState.value.busqueda)
        }

    @Test
    fun `varias teclas seguidas hacen una sola busqueda con el ultimo texto`() =
        runTest {
            val vm = crear()
            recoger(vm)
            vm.onTexto("So")
            advanceTimeBy(100)
            vm.onTexto("Sol")
            advanceTimeBy(100)
            vm.onTexto("Soll")
            advanceTimeBy(300)
            assertEquals(listOf("Soll"), buscador.consultas.map { it.first })
        }

    @Test
    fun `con menos de dos letras no hay busqueda ni lista`() =
        runTest {
            val vm = crear()
            recoger(vm)
            vm.onTexto("S")
            advanceTimeBy(400)
            assertTrue(buscador.consultas.isEmpty())
            assertEquals(BusquedaDestino.Inactiva, vm.uiState.value.busqueda)
        }

    @Test
    fun `sin coincidencias la lista llega vacia`() =
        runTest {
            val vm = crear()
            recoger(vm)
            vm.onTexto("zzzz")
            advanceTimeBy(300)
            assertEquals(BusquedaDestino.Resultados(emptyList()), vm.uiState.value.busqueda)
        }

    @Test
    fun `unas coordenadas pegadas son el destino y no se consulta el indice`() =
        runTest {
            val vm = crear()
            recoger(vm)
            vm.onTexto("39.7667, 2.7154")
            advanceTimeBy(300)
            val busqueda = vm.uiState.value.busqueda as BusquedaDestino.Ubicacion
            assertEquals(SOLLER.lat, busqueda.destino.punto.lat, 1e-9)
            assertEquals(SOLLER.lon, busqueda.destino.punto.lon, 1e-9)
            assertTrue(busqueda.sinNombre)
            assertTrue(buscador.consultas.isEmpty())
        }

    @Test
    fun `un enlace con nombre usa ese nombre`() =
        runTest {
            val vm = crear()
            recoger(vm)
            vm.onTexto("geo:39.7667,2.7154?q=39.7667,2.7154(Port de Sóller)")
            advanceTimeBy(300)
            val busqueda = vm.uiState.value.busqueda as BusquedaDestino.Ubicacion
            assertEquals("Port de Sóller", busqueda.destino.nombre)
            assertFalse(busqueda.sinNombre)
        }

    @Test
    fun `un enlace corto avisa de que necesita red`() =
        runTest {
            val vm = crear()
            recoger(vm)
            vm.onTexto("https://maps.app.goo.gl/AbCdEf")
            advanceTimeBy(300)
            assertEquals(BusquedaDestino.NecesitaRed, vm.uiState.value.busqueda)
            assertTrue(buscador.consultas.isEmpty())
        }

    @Test
    fun `elegir destino abre la vista previa y calcula las dos rutas desde la ultima fijacion`() =
        runTest {
            rutasOk()
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.elegirDestino(destino)
            val previa = vm.uiState.value.vistaPrevia!!
            assertEquals(PerfilRuta.MAS_RAPIDA, previa.perfil)
            val pedidos = enrutador.peticiones.map { it.third }.toSet()
            assertEquals(setOf(PerfilRuta.MAS_RAPIDA, PerfilRuta.SINUOSA), pedidos)
            assertTrue(enrutador.peticiones.all { it.first == PALMA && it.second == SOLLER })
            assertTrue(previa.opciones.values.all { it is OpcionRuta.Lista })
            assertTrue(previa.puedeEmpezar)
            assertEquals("Sóller", vm.uiState.value.texto)
            assertEquals(BusquedaDestino.Inactiva, vm.uiState.value.busqueda)
        }

    @Test
    fun `mientras calcula cada perfil esta cargando y se resuelve por separado`() =
        runTest {
            rutasOk()
            val sinuosa = enrutador.esperar(PerfilRuta.SINUOSA)
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.elegirDestino(destino)
            var previa = vm.uiState.value.vistaPrevia!!
            assertTrue(previa.opciones[PerfilRuta.MAS_RAPIDA] is OpcionRuta.Lista)
            assertEquals(OpcionRuta.Cargando, previa.opciones[PerfilRuta.SINUOSA])
            sinuosa.complete(Unit)
            previa = vm.uiState.value.vistaPrevia!!
            assertTrue(previa.opciones[PerfilRuta.SINUOSA] is OpcionRuta.Lista)
        }

    @Test
    fun `sin datos de rutas ninguna opcion permite empezar`() =
        runTest {
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.elegirDestino(destino)
            val previa = vm.uiState.value.vistaPrevia!!
            assertTrue(previa.opciones.values.all { it == OpcionRuta.SinDatos })
            assertFalse(previa.puedeEmpezar)
            vm.empezar()
            assertTrue(control.empezadas.isEmpty())
        }

    @Test
    fun `un error de ruta se refleja y la otra ruta sigue siendo valida`() =
        runTest {
            enrutador.respuestas[PerfilRuta.MAS_RAPIDA] = ResultadoRuta.Error("sin camino")
            enrutador.respuestas[PerfilRuta.SINUOSA] = ResultadoRuta.Ok(rutaDePrueba(), 10)
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.elegirDestino(destino)
            val previa = vm.uiState.value.vistaPrevia!!
            assertEquals(OpcionRuta.Error("sin camino"), previa.opciones[PerfilRuta.MAS_RAPIDA])
            // La elegida falló y la otra no: pasa a la buena para no dejar «Empezar» desactivado.
            assertEquals(PerfilRuta.SINUOSA, previa.perfil)
            assertTrue(previa.puedeEmpezar)
        }

    @Test
    fun `sin fijacion se avisa y las rutas se calculan al llegar la primera`() =
        runTest {
            rutasOk()
            val vm = crear()
            recoger(vm)
            vm.elegirDestino(destino)
            assertTrue(
                vm.uiState.value.vistaPrevia!!
                    .sinPosicion,
            )
            assertTrue(enrutador.peticiones.isEmpty())
            vm.onPosicion(PALMA)
            val previa = vm.uiState.value.vistaPrevia!!
            assertFalse(previa.sinPosicion)
            assertTrue(previa.puedeEmpezar)
            assertEquals(2, enrutador.peticiones.size)
        }

    @Test
    fun `elegir la sinuosa y empezar lanza el guiado con esa ruta`() =
        runTest {
            rutasOk()
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.elegirDestino(destino)
            vm.elegirPerfil(PerfilRuta.SINUOSA)
            vm.empezar()
            val (d, perfil, ruta) = control.empezadas.single()
            assertEquals(destino, d)
            assertEquals(PerfilRuta.SINUOSA, perfil)
            assertEquals(24_000.0, ruta.longitudM, 0.0)
            val estado = vm.uiState.value
            assertTrue(estado.control is EstadoNavegacion.Guiando)
            assertNull(estado.vistaPrevia)
            assertEquals(ruta, estado.rutaEnMapa)
            assertEquals(SOLLER, estado.destinoEnMapa)
            assertFalse(estado.encuadrar)
            assertTrue(estado.conduciendo)
        }

    @Test
    fun `por defecto se empieza con la ruta mas rapida`() =
        runTest {
            rutasOk()
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.elegirDestino(destino)
            vm.empezar()
            assertEquals(PerfilRuta.MAS_RAPIDA, control.empezadas.single().second)
        }

    @Test
    fun `la ruta de la vista previa se dibuja y se encuadra`() =
        runTest {
            rutasOk()
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.elegirDestino(destino)
            val estado = vm.uiState.value
            assertNotNull(estado.rutaEnMapa)
            assertEquals(SOLLER, estado.destinoEnMapa)
            assertTrue(estado.encuadrar)
        }

    @Test
    fun `cancelar cierra la vista previa y borra el texto`() =
        runTest {
            rutasOk()
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.elegirDestino(destino)
            vm.cancelar()
            val estado = vm.uiState.value
            assertNull(estado.vistaPrevia)
            assertEquals("", estado.texto)
            assertNull(estado.rutaEnMapa)
            assertTrue(control.empezadas.isEmpty())
        }

    @Test
    fun `editar el texto durante la vista previa vuelve a la busqueda`() =
        runTest {
            rutasOk()
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.elegirDestino(destino)
            vm.onTexto("Valldemossa")
            assertNull(vm.uiState.value.vistaPrevia)
            advanceTimeBy(300)
            assertEquals(listOf("Valldemossa"), buscador.consultas.map { it.first })
        }

    @Test
    fun `conducir sin destino empieza el modo libre`() =
        runTest {
            val vm = crear()
            recoger(vm)
            vm.conducirLibre()
            assertEquals(1, control.libres)
            assertTrue(vm.uiState.value.control is EstadoNavegacion.Libre)
            assertTrue(vm.uiState.value.conduciendo)
            assertNull(vm.uiState.value.rutaEnMapa)
        }

    @Test
    fun `terminar para la conduccion y vuelve a reposo`() =
        runTest {
            val vm = crear()
            recoger(vm)
            vm.conducirLibre()
            vm.terminar()
            assertEquals(1, control.terminadas)
            assertTrue(vm.uiState.value.enReposo)
        }

    @Test
    fun `la llegada y el error del control se reflejan sin busqueda ni vista previa`() =
        runTest {
            val vm = crear()
            recoger(vm)
            control.publicar(EstadoNavegacion.Llegada(destino))
            assertEquals(EstadoNavegacion.Llegada(destino), vm.uiState.value.control)
            assertFalse(vm.uiState.value.enReposo)
            control.publicar(EstadoNavegacion.Error("Se ha perdido la señal GPS."))
            assertEquals(EstadoNavegacion.Error("Se ha perdido la señal GPS."), vm.uiState.value.control)
        }
}
