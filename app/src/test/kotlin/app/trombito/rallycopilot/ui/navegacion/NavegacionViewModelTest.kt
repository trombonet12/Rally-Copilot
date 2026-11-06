package app.trombito.rallycopilot.ui.navegacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.domain.enlaces.ResultadoResolucion
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import kotlinx.coroutines.CompletableDeferred
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
    private val resolvedor = FalsoResolvedor()
    private val pendiente = FalsoGuiadoPendiente()
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

    private fun crear() = NavegacionViewModel(buscador, enrutador, control, resolvedor, pendiente)

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
            assertEquals(
                BusquedaDestino.Resultados(listOf(soller), listOf(PALMA.distanciaA(soller.punto))),
                vm.uiState.value.busqueda,
            )
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

    private val enlaceCorto = "https://maps.app.goo.gl/AbCdEf"
    private val enlaceLargo =
        "https://www.google.com/maps/place/Port+de+S%C3%B3ller/@39.7948,2.6942,17z/data=!3d39.7948!4d2.6942"

    @Test
    fun `un enlace corto muestra resolviendo y al llegar la URL con coordenadas ofrece la ubicacion pegada`() =
        runTest {
            val espera = CompletableDeferred<Unit>()
            resolvedor.espera = espera
            resolvedor.respuesta = ResultadoResolucion.Url(enlaceLargo)
            val vm = crear()
            recoger(vm)
            vm.onTexto(enlaceCorto)
            advanceTimeBy(300)
            assertEquals(BusquedaDestino.Resolviendo, vm.uiState.value.busqueda)
            assertEquals(listOf(enlaceCorto), resolvedor.urls)
            espera.complete(Unit)
            val busqueda = vm.uiState.value.busqueda as BusquedaDestino.Ubicacion
            assertEquals("Port de Sóller", busqueda.destino.nombre)
            assertEquals(39.7948, busqueda.destino.punto.lat, 1e-9)
            assertTrue(buscador.consultas.isEmpty())
        }

    @Test
    fun `un enlace corto sin red avisa de que hace falta internet`() =
        runTest {
            resolvedor.respuesta = ResultadoResolucion.SinRed
            val vm = crear()
            recoger(vm)
            vm.onTexto(enlaceCorto)
            advanceTimeBy(300)
            assertEquals(BusquedaDestino.SinRed, vm.uiState.value.busqueda)
        }

    @Test
    fun `un enlace corto con error o no permitido dice que no se pudo abrir`() =
        runTest {
            val vm = crear()
            recoger(vm)
            resolvedor.respuesta = ResultadoResolucion.Error
            vm.onTexto(enlaceCorto)
            advanceTimeBy(300)
            assertEquals(BusquedaDestino.EnlaceNoAbierto, vm.uiState.value.busqueda)
            resolvedor.respuesta = ResultadoResolucion.NoPermitido
            vm.onTexto("$enlaceCorto?x=1")
            advanceTimeBy(300)
            assertEquals(BusquedaDestino.EnlaceNoAbierto, vm.uiState.value.busqueda)
        }

    @Test
    fun `una URL final sin coordenadas dice que no se pudo abrir`() =
        runTest {
            resolvedor.respuesta = ResultadoResolucion.Url("https://www.google.com/maps")
            val vm = crear()
            recoger(vm)
            vm.onTexto(enlaceCorto)
            advanceTimeBy(300)
            assertEquals(BusquedaDestino.EnlaceNoAbierto, vm.uiState.value.busqueda)
        }

    @Test
    fun `cambiar el texto cancela la resolucion y su resultado no se muestra`() =
        runTest {
            val espera = CompletableDeferred<Unit>()
            resolvedor.espera = espera
            resolvedor.respuesta = ResultadoResolucion.Url(enlaceLargo)
            val vm = crear()
            recoger(vm)
            vm.onTexto(enlaceCorto)
            advanceTimeBy(300)
            vm.onTexto("")
            assertEquals(1, resolvedor.canceladas)
            espera.complete(Unit)
            advanceTimeBy(300)
            assertEquals(BusquedaDestino.Inactiva, vm.uiState.value.busqueda)
        }

    @Test
    fun `borrar la busqueda cancela la resolucion`() =
        runTest {
            resolvedor.espera = CompletableDeferred()
            val vm = crear()
            recoger(vm)
            vm.onTexto(enlaceCorto)
            advanceTimeBy(300)
            vm.cancelar()
            assertEquals(1, resolvedor.canceladas)
            assertEquals(BusquedaDestino.Inactiva, vm.uiState.value.busqueda)
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
    fun `editar el texto durante la vista previa busca sin perder la ruta`() =
        runTest {
            rutasOk()
            val vm = crear()
            recoger(vm)
            vm.onPosicion(PALMA)
            vm.elegirDestino(destino)
            vm.onTexto("Valldemossa")
            assertNotNull(vm.uiState.value.vistaPrevia)
            advanceTimeBy(300)
            assertEquals(listOf("Valldemossa"), buscador.consultas.map { it.first })
        }

    @Test
    fun `al abrir el mapa con permiso empieza el modo libre`() =
        runTest {
            val vm = crear()
            recoger(vm)
            vm.onPermisoUbicacion(true)
            assertEquals(1, control.libres)
            assertTrue(vm.uiState.value.control is EstadoNavegacion.Libre)
            // En libre no hay bandera de terminar y sigue la búsqueda de destino.
            assertFalse(vm.uiState.value.conduciendo)
            assertTrue(vm.uiState.value.enReposo)
            assertNull(vm.uiState.value.rutaEnMapa)
        }

    @Test
    fun `sin permiso no hace nada y al concederlo empieza el modo libre`() =
        runTest {
            val vm = crear()
            recoger(vm)
            vm.onPermisoUbicacion(false)
            assertEquals(0, control.libres)
            assertEquals(EstadoNavegacion.Inactiva, vm.uiState.value.control)
            vm.onPermisoUbicacion(true)
            assertEquals(1, control.libres)
            assertTrue(vm.uiState.value.control is EstadoNavegacion.Libre)
        }

    @Test
    fun `tras un error al abrir el mapa se reintenta el modo libre`() =
        runTest {
            control.publicar(EstadoNavegacion.Error("viejo"))
            val vm = crear()
            recoger(vm)
            vm.onPermisoUbicacion(true)
            assertEquals(1, control.libres)
            assertTrue(vm.uiState.value.control is EstadoNavegacion.Libre)
        }

    @Test
    fun `girar la pantalla no reinicia ni para el modo libre`() =
        runTest {
            val vm = crear()
            recoger(vm)
            vm.onPermisoUbicacion(true)
            // La actividad se recrea: la composición nueva repite el aviso de permiso y el ViewModel sigue vivo.
            recoger(vm)
            vm.onPermisoUbicacion(true)
            assertEquals(1, control.libres)
            assertEquals(0, control.terminadas)
            assertTrue(vm.uiState.value.control is EstadoNavegacion.Libre)
        }

    @Test
    fun `girar la pantalla con un error visible no lo borra`() =
        runTest {
            val vm = crear()
            recoger(vm)
            vm.onPermisoUbicacion(true)
            control.publicar(EstadoNavegacion.Error("Se ha perdido la señal GPS."))
            vm.onPermisoUbicacion(true)
            assertEquals(1, control.libres)
            assertEquals(EstadoNavegacion.Error("Se ha perdido la señal GPS."), vm.uiState.value.control)
        }

    @Test
    fun `empezar un destino desde el modo libre lo sustituye por el guiado`() =
        runTest {
            rutasOk()
            val vm = crear()
            recoger(vm)
            vm.onPermisoUbicacion(true)
            vm.onPosicion(PALMA)
            vm.elegirDestino(destino)
            // En libre se ve la vista previa de rutas.
            assertNotNull(vm.uiState.value.vistaPrevia)
            assertTrue(vm.uiState.value.encuadrar)
            vm.empezar()
            assertEquals(1, control.empezadas.size)
            assertTrue(vm.uiState.value.control is EstadoNavegacion.Guiando)
            assertNull(vm.uiState.value.vistaPrevia)
            assertTrue(vm.uiState.value.conduciendo)
            assertEquals(0, control.terminadas)
        }

    @Test
    fun `terminar el guiado vuelve al modo libre sin parar el servicio`() =
        runTest {
            rutasOk()
            val vm = crear()
            recoger(vm)
            vm.onPermisoUbicacion(true)
            vm.onPosicion(PALMA)
            vm.elegirDestino(destino)
            vm.empezar()
            vm.terminar()
            assertEquals(2, control.libres)
            assertEquals(0, control.terminadas)
            assertTrue(vm.uiState.value.control is EstadoNavegacion.Libre)
            assertFalse(vm.uiState.value.conduciendo)
        }

    @Test
    fun `cerrar la llegada vuelve al modo libre`() =
        runTest {
            val vm = crear()
            recoger(vm)
            vm.onPermisoUbicacion(true)
            control.publicar(EstadoNavegacion.Llegada(destino))
            vm.terminar()
            assertTrue(vm.uiState.value.control is EstadoNavegacion.Libre)
        }

    @Test
    fun `terminar sin permiso para todo`() =
        runTest {
            val vm = crear()
            recoger(vm)
            control.publicar(EstadoNavegacion.Error("x"))
            vm.terminar()
            assertEquals(1, control.terminadas)
            assertEquals(0, control.libres)
            assertEquals(EstadoNavegacion.Inactiva, vm.uiState.value.control)
        }

    @Test
    fun `al salir del mapa se para el modo libre`() =
        runTest {
            val almacen = ViewModelStore()
            val vm = ViewModelProvider(almacen, fabrica()).get(NavegacionViewModel::class.java)
            recoger(vm)
            vm.onPermisoUbicacion(true)
            almacen.clear()
            assertEquals(1, control.terminadas)
            assertEquals(EstadoNavegacion.Inactiva, control.estado.value)
        }

    @Test
    fun `al salir del mapa tambien se para el guiado`() =
        runTest {
            rutasOk()
            val almacen = ViewModelStore()
            val vm = ViewModelProvider(almacen, fabrica()).get(NavegacionViewModel::class.java)
            recoger(vm)
            vm.onPermisoUbicacion(true)
            vm.onPosicion(PALMA)
            vm.elegirDestino(destino)
            vm.empezar()
            almacen.clear()
            assertEquals(1, control.terminadas)
            assertEquals(EstadoNavegacion.Inactiva, control.estado.value)
        }

    @Test
    fun `al salir del mapa sin nada en marcha no se toca el control`() =
        runTest {
            val almacen = ViewModelStore()
            ViewModelProvider(almacen, fabrica()).get(NavegacionViewModel::class.java)
            almacen.clear()
            assertEquals(0, control.terminadas)
        }

    private fun fabrica() =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = crear() as T
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
