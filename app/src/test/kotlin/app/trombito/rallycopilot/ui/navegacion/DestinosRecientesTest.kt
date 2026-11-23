package app.trombito.rallycopilot.ui.navegacion

import androidx.compose.foundation.layout.Column
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.DestinosRecientes
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioAjustesConduccion
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class RecientesEnMemoria(
    inicial: List<Destino> = emptyList(),
) : DestinosRecientes {
    val lista = MutableStateFlow(inicial)
    override val recientes: Flow<List<Destino>> = lista

    override fun registrar(destino: Destino) {
        lista.value = listOf(destino) + lista.value
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DestinosRecientesViewModelTest {
    private val enrutador = FalsoEnrutador()
    private val destino = Destino("Sóller", SOLLER)

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun crear(
        recientes: DestinosRecientes,
        ajustes: FalsoRepositorioAjustesConduccion = FalsoRepositorioAjustesConduccion(),
    ) = NavegacionViewModel(
        FalsoBuscador(),
        enrutador,
        FalsoControlNavegacion(),
        FalsoResolvedor(),
        FalsoGuiadoPendiente(),
        ajustes,
        destinosRecientes = recientes,
    )

    @Test
    fun `empezar una ruta anota el destino como reciente`() =
        runTest {
            enrutador.respuestas[PerfilRuta.MAS_RAPIDA] = ResultadoRuta.Ok(rutaDePrueba(18_400.0, 1_380.0), 10)
            val almacen = RecientesEnMemoria()
            val vm = crear(almacen)
            val recogida = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            vm.onPosicion(PALMA)
            vm.elegirDestino(destino)
            assertTrue("solo elegir no lo anota", almacen.lista.value.isEmpty())
            vm.empezar()
            assertEquals(listOf(destino), almacen.lista.value)
            recogida.cancel()
        }

    @Test
    fun `el estado expone los recientes y deja fuera la casa`() =
        runTest {
            val valldemossa = Destino("Valldemossa", PuntoGeo(39.7106, 2.6213))
            val almacen = RecientesEnMemoria(listOf(destino, Destino("Casa", PALMA), valldemossa))
            val ajustes = FalsoRepositorioAjustesConduccion(AjustesConduccion(casa = PALMA))
            val vm = crear(almacen, ajustes)
            val recogida = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            assertEquals(listOf(destino, valldemossa), vm.uiState.value.recientes)
            recogida.cancel()
        }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AccesosDestinoUiTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val soller = Destino("Sóller", SOLLER)
    private val inca = Destino("Inca con un nombre bastante largo", PuntoGeo(39.7201, 2.9112))
    private val elegidos = mutableListOf<Destino>()

    private fun poner(estado: NavegacionUiState) =
        banco.poner {
            RallyCopilotTheme {
                Column { AccesosDestino(estado, { elegidos += it }) }
            }
        }

    @Test
    fun `cada reciente es un chip y tocarlo elige ese destino`() {
        poner(NavegacionUiState(recientes = listOf(soller, inca)))
        assertEquals(2, banco.etiquetas(ETIQUETA_RECIENTE_PREFIJO).size)
        assertTrue("Sóller" in banco.textos(banco.conEtiqueta(ETIQUETA_RECIENTE_PREFIJO + 0)))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_RECIENTE_PREFIJO + 1))
        assertEquals(listOf(inca), elegidos)
    }

    @Test
    fun `un nombre largo se recorta con puntos suspensivos`() {
        assertEquals("Inca con un nombr…", nombreCorto(inca.nombre).also { assertEquals(18, it.length) })
        assertEquals("Sóller", nombreCorto("Sóller"))
    }

    @Test
    fun `sin recientes o con algo escrito no hay chips`() {
        poner(NavegacionUiState())
        assertFalse(banco.hay(ETIQUETA_RECIENTE_PREFIJO + 0))
        poner(NavegacionUiState(recientes = listOf(soller), texto = "Sól"))
        assertFalse(banco.hay(ETIQUETA_RECIENTE_PREFIJO + 0))
    }
}
