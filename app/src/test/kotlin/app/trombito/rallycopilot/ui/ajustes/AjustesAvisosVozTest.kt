package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.VelocidadHabla
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.FalsoControlOrdenes
import app.trombito.rallycopilot.ui.comun.FalsoLocutor
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioAjustesConduccion
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioDatosMapa
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.copiloto.CopilotoViewModel
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_AVISAR_INCIDENCIAS
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_AVISAR_RADARES
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_AVISAR_VELOCIDAD
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_HABLA_PREFIJO
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ajustes > Voz: avisos por voz y velocidad de habla con su vista previa en vivo. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AjustesAvisosVozTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun descripcionDe(etiqueta: String) =
        banco
            .conEtiqueta(etiqueta)
            .config
            .getOrNull(SemanticsProperties.ContentDescription)
            ?.firstOrNull()

    @Test
    fun `la seccion de voz ofrece los tres avisos y las dos velocidades`() {
        banco.ponerAjustes(AjustesConduccion(voz = VozCopiloto.DAVEFX), SeccionAjustes.VOZ)

        listOf(ETIQUETA_AVISAR_RADARES, ETIQUETA_AVISAR_INCIDENCIAS, ETIQUETA_AVISAR_VELOCIDAD).forEach {
            assertEquals("Activado", banco.descripcionEstado(it))
        }
        assertEquals("Seleccionado", banco.descripcionEstado(ETIQUETA_HABLA_PREFIJO + "normal"))
        assertTrue("Velocidad de habla" in banco.todosLosTextos())
        assertTrue("Avisos por voz" in banco.todosLosTextos())
    }

    @Test
    fun `apagar un aviso se ve tachado en la vista previa y lo pide`() {
        val pedidos = mutableListOf<Boolean>()
        banco.ponerAjustes(
            AjustesConduccion(voz = VozCopiloto.DAVEFX),
            SeccionAjustes.VOZ,
            AccionesAjustes(onAvisarRadares = { pedidos += it }),
        )
        assertEquals("Radares: avisa", descripcionDe(ETIQUETA_PREVISTA_INDICACION_PREFIJO + "radares"))

        banco.pulsar(banco.conEtiqueta(ETIQUETA_AVISAR_RADARES))

        assertEquals(listOf(false), pedidos)
        assertEquals("Desactivado", banco.descripcionEstado(ETIQUETA_AVISAR_RADARES))
        assertEquals("Radares: no avisa", descripcionDe(ETIQUETA_PREVISTA_INDICACION_PREFIJO + "radares"))
        assertEquals("Incidencias: avisa", descripcionDe(ETIQUETA_PREVISTA_INDICACION_PREFIJO + "incidencias"))
        assertEquals("Velocidad: avisa", descripcionDe(ETIQUETA_PREVISTA_INDICACION_PREFIJO + "velocidad"))
    }

    @Test
    fun `elegir la velocidad rapida cambia la vista previa y lo pide`() {
        val pedidas = mutableListOf<VelocidadHabla>()
        banco.ponerAjustes(
            AjustesConduccion(voz = VozCopiloto.DAVEFX),
            SeccionAjustes.VOZ,
            AccionesAjustes(onVelocidadHabla = { pedidas += it }),
        )
        assertEquals("Habla normal", descripcionDe(ETIQUETA_PREVISTA_HABLA))

        banco.pulsar(banco.conEtiqueta(ETIQUETA_HABLA_PREFIJO + "rapida"))

        assertEquals(listOf(VelocidadHabla.RAPIDA), pedidas)
        assertEquals("Habla rápida", descripcionDe(ETIQUETA_PREVISTA_HABLA))
        assertEquals("Seleccionado", banco.descripcionEstado(ETIQUETA_HABLA_PREFIJO + "rapida"))
    }

    @Test
    fun `la vista previa de voz sigue midiendo entre 160 y 200 dp con los avisos`() {
        banco.ponerAjustes(AjustesConduccion(voz = VozCopiloto.DAVEFX), SeccionAjustes.VOZ)

        val alto = banco.conEtiqueta(ETIQUETA_SECCION_PREVISTA_PREFIJO + "voz").size.height
        val densidad = 3 // xxhdpi

        assertTrue("Alto $alto px", alto in (160 * densidad)..(200 * densidad))
    }
}

/** Los ViewModels de Ajustes y de la hoja del copiloto guardan los avisos y aplican la velocidad al locutor. */
@OptIn(ExperimentalCoroutinesApi::class)
class AvisosVozViewModelsTest {
    private val repositorio = FalsoRepositorioAjustesConduccion(AjustesConduccion(voz = VozCopiloto.SHARVARD))
    private val base = FalsoLocutor()
    private val velocidades = mutableListOf<VelocidadHabla>()

    private val locutor: Locutor =
        object : Locutor by base {
            override fun usarVelocidad(v: VelocidadHabla) {
                velocidades += v
            }
        }

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun TestScope.ajustes(): AjustesViewModel {
        val vm = AjustesViewModel(repositorio, locutor, FalsoRepositorioDatosMapa())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private fun TestScope.copiloto(): CopilotoViewModel {
        val vm = CopilotoViewModel(repositorio, locutor, FalsoControlOrdenes(), FalsoRepositorioDatosMapa())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `Ajustes guarda cada familia de avisos sin tocar las demas`() =
        runTest {
            val vm = ajustes()

            vm.onAvisarRadares(false)
            vm.onAvisarIncidencias(false)
            vm.onAvisarVelocidad(false)
            assertEquals(
                Triple(false, false, false),
                repositorio.mutable.value.let {
                    Triple(it.avisarRadares, it.avisarIncidencias, it.avisarVelocidad)
                },
            )
            vm.onAvisarIncidencias(true)

            val guardado = repositorio.mutable.value
            assertEquals(
                Triple(false, true, false),
                Triple(guardado.avisarRadares, guardado.avisarIncidencias, guardado.avisarVelocidad),
            )
            assertEquals(VozCopiloto.SHARVARD, guardado.voz)
            assertEquals(false, vm.uiState.value.avisarRadares)
        }

    @Test
    fun `Ajustes aplica la velocidad al locutor, la guarda y dice el ejemplo con ella`() =
        runTest {
            val vm = ajustes()

            vm.onVelocidadHabla(VelocidadHabla.RAPIDA)

            assertEquals(VelocidadHabla.RAPIDA, repositorio.mutable.value.velocidadHabla)
            assertEquals(VelocidadHabla.RAPIDA, velocidades.first())
            assertEquals(listOf(VozCopiloto.SHARVARD), base.ejemplos.map { it.first })
            assertEquals(VelocidadHabla.RAPIDA, velocidades.last())
        }

    @Test
    fun `la hoja del copiloto guarda los avisos y refleja el estado`() =
        runTest {
            val vm = copiloto()
            assertEquals(
                Triple(true, true, true),
                vm.uiState.value.let {
                    Triple(it.avisarRadares, it.avisarIncidencias, it.avisarVelocidad)
                },
            )

            vm.onAvisarRadares(false)
            vm.onAvisarVelocidad(false)

            assertEquals(false, repositorio.mutable.value.avisarRadares)
            assertEquals(true, repositorio.mutable.value.avisarIncidencias)
            assertEquals(false, vm.uiState.value.avisarVelocidad)
            assertEquals(false, vm.uiState.value.avisarRadares)
            assertEquals(true, vm.uiState.value.avisarIncidencias)
        }

    @Test
    fun `la hoja del copiloto cambia la velocidad al instante, la guarda y suena el ejemplo`() =
        runTest {
            val vm = copiloto()

            vm.onVelocidadHabla(VelocidadHabla.RAPIDA)

            assertEquals(VelocidadHabla.RAPIDA, repositorio.mutable.value.velocidadHabla)
            assertEquals(VelocidadHabla.RAPIDA, vm.uiState.value.velocidadHabla)
            assertEquals(VelocidadHabla.RAPIDA, velocidades.first())
            assertEquals(1, base.ejemplos.size)

            vm.onVelocidadHabla(VelocidadHabla.NORMAL)
            assertEquals(VelocidadHabla.NORMAL, repositorio.mutable.value.velocidadHabla)
            assertEquals(VelocidadHabla.NORMAL, velocidades.last())
        }
}
