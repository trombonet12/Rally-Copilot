package app.trombito.rallycopilot.ui.alertas

import app.trombito.rallycopilot.domain.alertas.AlertasEnCurso
import app.trombito.rallycopilot.domain.alertas.ExcesoEnCurso
import app.trombito.rallycopilot.domain.alertas.IncidenciasVigentes
import app.trombito.rallycopilot.domain.alertas.ProveedorAlertas
import app.trombito.rallycopilot.domain.alertas.ProveedorRadaresMapa
import app.trombito.rallycopilot.domain.alertas.RadarProximo
import app.trombito.rallycopilot.domain.alertas.RadaresEnMapa
import app.trombito.rallycopilot.domain.alertas.RepositorioIncidencias
import app.trombito.rallycopilot.motor.alertas.Radar
import app.trombito.rallycopilot.motor.alertas.TipoRadar
import app.trombito.rallycopilot.motor.geo.PuntoGeo
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
        val vm = AlertasViewModel(proveedor, repositorio, radares)
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
                listOf(MarcadorRadar("r1", ClaseRadar.FIJO, 39.6, 2.9, 80)),
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
            vm.onSeleccionar("a")
            assertEquals(
                "a",
                vm.uiState.value.seleccionada
                    ?.id,
            )
            vm.onSeleccionar("b")
            assertEquals(
                "b",
                vm.uiState.value.seleccionada
                    ?.id,
            )
            vm.onSeleccionar("b")
            assertNull(vm.uiState.value.seleccionada)
            vm.onSeleccionar("a")
            vm.onSeleccionar(null)
            assertNull(vm.uiState.value.seleccionada)
        }

    @Test
    fun `la tarjeta desaparece si la incidencia ya no esta`() =
        runTest {
            val vm = crear()
            repositorio.estado.value = IncidenciasVigentes(listOf(incidencia("a")), null)
            vm.onSeleccionar("a")
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
}
