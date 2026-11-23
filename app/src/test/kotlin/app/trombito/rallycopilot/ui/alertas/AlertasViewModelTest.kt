package app.trombito.rallycopilot.ui.alertas

import app.trombito.rallycopilot.domain.alertas.AlertasEnCurso
import app.trombito.rallycopilot.domain.alertas.ExcesoEnCurso
import app.trombito.rallycopilot.domain.alertas.IncidenciasVigentes
import app.trombito.rallycopilot.domain.alertas.ProveedorAlertas
import app.trombito.rallycopilot.domain.alertas.ProveedorRadaresMapa
import app.trombito.rallycopilot.domain.alertas.RadarProximo
import app.trombito.rallycopilot.domain.alertas.RadaresEnMapa
import app.trombito.rallycopilot.domain.alertas.RepositorioIncidencias
import app.trombito.rallycopilot.domain.alertas.TramoEnMapa
import app.trombito.rallycopilot.domain.diagnostico.NivelRegistro
import app.trombito.rallycopilot.domain.diagnostico.RegistroDiagnostico
import app.trombito.rallycopilot.motor.alertas.Radar
import app.trombito.rallycopilot.motor.alertas.TipoRadar
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.mapa.NotasEnMapa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import java.time.Instant

private class FalsoProveedorAlertas : ProveedorAlertas {
    val estado = MutableStateFlow<AlertasEnCurso?>(null)
    override val alertas: StateFlow<AlertasEnCurso?> = estado
}

private class FalsoRepositorioIncidencias : RepositorioIncidencias {
    val estado = MutableStateFlow(IncidenciasVigentes())
    override val incidencias: StateFlow<IncidenciasVigentes> = estado

    override suspend fun refrescar() = Unit
}

private class RegistroEnMemoria : RegistroDiagnostico {
    val lineas = mutableListOf<String>()

    override fun registrar(
        nivel: NivelRegistro,
        etiqueta: String,
        mensaje: String,
    ) {
        lineas += "$etiqueta $mensaje"
    }

    override fun registrarSincrono(
        nivel: NivelRegistro,
        etiqueta: String,
        mensaje: String,
    ) = registrar(nivel, etiqueta, mensaje)
}

private class FalsosRadaresMapa(
    var datos: RadaresEnMapa = RadaresEnMapa(),
) : ProveedorRadaresMapa {
    override suspend fun radaresEnMapa(): RadaresEnMapa = datos
}

@OptIn(ExperimentalCoroutinesApi::class)
class AlertasViewModelTest {
    private val proveedor = FalsoProveedorAlertas()
    private val repositorio = FalsoRepositorioIncidencias()
    private val radares = FalsosRadaresMapa()
    private val registro = RegistroEnMemoria()
    private var ahoraMs = AHORA_MS

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun TestScope.crear(): AlertasViewModel {
        val vm = AlertasViewModel(proveedor, repositorio, radares, registro)
        vm.reloj = { ahoraMs }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `sin conduccion y sin incidencias el estado esta vacio`() =
        runTest {
            val vm = crear()
            assertEquals(AlertasUiState(), vm.uiState.value)
        }

    @Test
    fun `los radares de los datos salen siempre en el estado aunque no haya conduccion`() =
        runTest {
            val radar = Radar("r1", TipoRadar.FIJO, PuntoGeo(39.6, 2.9), 10.0, 80, null, "test")
            radares.datos = RadaresEnMapa(listOf(radar))
            val vm = crear()
            assertEquals(
                listOf(MarcadorRadar("r1", ClaseRadar.FIJO, 39.6, 2.9, 80, rumboGrados = 10.0)),
                vm.uiState.value.radares.marcadores,
            )
            assertNull(vm.uiState.value.bocadillo)
        }

    @Test
    fun `lo que publica la sesion llega a la UI`() =
        runTest {
            val vm = crear()
            proveedor.estado.value =
                AlertasEnCurso(
                    radarProximo = RadarProximo(TipoRadar.FIJO, 500.0, 50),
                    exceso = ExcesoEnCurso(50, rojo = true, exceso = true),
                )
            val ui = vm.uiState.value
            assertEquals(AvisoAlerta.Radar(ClaseRadar.FIJO, 500, 50), ui.aviso)
            assertTrue(ui.panel.rojo && ui.panel.exceso)
            proveedor.estado.value = null
            assertNull(vm.uiState.value.aviso)
            assertFalse(vm.uiState.value.panel.rojo)
        }

    @Test
    fun `fuera de conduccion las incidencias del repositorio salen en el mapa`() =
        runTest {
            val vm = crear()
            repositorio.estado.value = IncidenciasVigentes(listOf(incidencia("a"), incidencia("b")), null)
            assertEquals(
                listOf("a", "b"),
                vm.uiState.value.marcadores
                    .map { it.id },
            )
        }

    @Test
    fun `tocar un marcador abre su tarjeta, tocarlo otra vez o cerrar la quita`() =
        runTest {
            val vm = crear()
            repositorio.estado.value = IncidenciasVigentes(listOf(incidencia("a"), incidencia("b")), null)
            vm.onSeleccionar(SeleccionMapa.Incidencia("a"))
            assertEquals(SeleccionMapa.Incidencia("a"), vm.uiState.value.seleccionada)
            vm.onSeleccionar(SeleccionMapa.Incidencia("b"))
            assertEquals(SeleccionMapa.Incidencia("b"), vm.uiState.value.seleccionada)
            vm.onSeleccionar(SeleccionMapa.Incidencia("b"))
            assertNull(vm.uiState.value.seleccionada)
            vm.onSeleccionar(SeleccionMapa.Incidencia("a"))
            vm.onSeleccionar(null)
            assertNull(vm.uiState.value.seleccionada)
        }

    @Test
    fun `el bocadillo de la incidencia tocada se cierra solo a los 8 segundos`() =
        runTest {
            val vm = crear()
            repositorio.estado.value = IncidenciasVigentes(listOf(incidencia("a")), null)
            vm.onSeleccionar(SeleccionMapa.Incidencia("a"))
            assertEquals(
                "seleccion-a",
                vm.uiState.value.bocadillo
                    ?.id,
            )
            advanceTimeBy(CIERRE_SELECCION_MS - 1)
            assertNotNull(vm.uiState.value.seleccionada)
            advanceTimeBy(2)
            assertNull(vm.uiState.value.seleccionada)
            assertNull(vm.uiState.value.bocadillo)
        }

    @Test
    fun `tocar otra incidencia reinicia los 8 segundos`() =
        runTest {
            val vm = crear()
            repositorio.estado.value = IncidenciasVigentes(listOf(incidencia("a"), incidencia("b")), null)
            vm.onSeleccionar(SeleccionMapa.Incidencia("a"))
            advanceTimeBy(CIERRE_SELECCION_MS - 1_000)
            vm.onSeleccionar(SeleccionMapa.Incidencia("b"))
            advanceTimeBy(CIERRE_SELECCION_MS - 1_000)
            assertEquals(SeleccionMapa.Incidencia("b"), vm.uiState.value.seleccionada)
            advanceTimeBy(1_500)
            assertNull(vm.uiState.value.seleccionada)
        }

    @Test
    fun `la tarjeta desaparece si la incidencia ya no esta`() =
        runTest {
            val vm = crear()
            repositorio.estado.value = IncidenciasVigentes(listOf(incidencia("a")), null)
            vm.onSeleccionar(SeleccionMapa.Incidencia("a"))
            assertNotNull(vm.uiState.value.seleccionada)
            repositorio.estado.value = IncidenciasVigentes(emptyList(), null)
            assertNull(vm.uiState.value.seleccionada)
        }

    @Test
    fun `la antiguedad se actualiza con el reloj aunque no cambien los datos`() =
        runTest {
            val vm = crear()
            repositorio.estado.value = IncidenciasVigentes(emptyList(), Instant.ofEpochMilli(AHORA_MS))
            assertNull(vm.uiState.value.frescura)
            ahoraMs += 31 * 60_000L
            advanceTimeBy(61_000L)
            assertEquals(FrescuraIncidencias(31 * 60_000L, conError = false), vm.uiState.value.frescura)
        }

    private fun idBocadillo(vm: AlertasViewModel): String? {
        val bocadillo = vm.uiState.value.bocadillo
        return bocadillo?.id
    }

    private val radarFijo = Radar("r1", TipoRadar.FIJO, PuntoGeo(39.6, 2.9), 10.0, 80, null, "test")
    private val inicioTramo = Radar("t-ini", TipoRadar.TRAMO_INICIO, PuntoGeo(39.7, 2.9), 0.0, 80, "t", "test")
    private val finTramo = Radar("t-fin", TipoRadar.TRAMO_FIN, PuntoGeo(39.71, 2.9), 0.0, 80, "t", "test")

    @Test
    fun `tocar un radar abre su bocadillo, tocarlo otra vez lo cierra y se cierra solo a los 8 segundos`() =
        runTest {
            radares.datos = RadaresEnMapa(listOf(radarFijo))
            val vm = crear()
            vm.onSeleccionar(SeleccionMapa.Radar("r1"))
            assertEquals("seleccion-radar-r1", idBocadillo(vm))
            vm.onSeleccionar(SeleccionMapa.Radar("r1"))
            assertNull(vm.uiState.value.bocadillo)
            vm.onSeleccionar(SeleccionMapa.Radar("r1"))
            advanceTimeBy(CIERRE_SELECCION_MS + 1)
            assertNull(vm.uiState.value.seleccionada)
        }

    @Test
    fun `tocar la linea de un tramo en otro punto es el mismo tramo y cierra el bocadillo`() =
        runTest {
            val trazado = TramoEnMapa("t", 80, listOf(inicioTramo.punto, finTramo.punto))
            radares.datos = RadaresEnMapa(listOf(inicioTramo, finTramo), listOf(trazado))
            val vm = crear()
            vm.onSeleccionar(SeleccionMapa.TramoRadar("t", PuntoGeo(39.705, 2.9)))
            assertNotNull(vm.uiState.value.bocadillo)
            vm.onSeleccionar(SeleccionMapa.TramoRadar("t", PuntoGeo(39.708, 2.9)))
            assertNull(vm.uiState.value.seleccionada)
        }

    @Test
    fun `la nota y la bandera tocadas salen cuando el mapa publica sus elementos`() =
        runTest {
            val vm = crear()
            val bandera = BanderaMapa("tr", esMeta = true, lat = 39.7, lon = 2.8, nombre = "Sóller", mejorMs = 1_000L)
            vm.onSeleccionar(SeleccionMapa.Bandera("tr", esMeta = true))
            assertNull(vm.uiState.value.bocadillo)
            vm.actualizarTocables(NotasEnMapa(), listOf(bandera))
            assertEquals("seleccion-bandera-tr-true", idBocadillo(vm))
            vm.actualizarTocables(NotasEnMapa(), emptyList())
            assertNull(vm.uiState.value.bocadillo)
        }

    @Test
    fun `los toques se anotan en el registro de diagnostico`() =
        runTest {
            val vm = crear()
            vm.registrarToque("toque capa=radar resultado=bocadillo")
            assertEquals(listOf("Mapa toque capa=radar resultado=bocadillo"), registro.lineas)
        }
}
