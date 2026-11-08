package app.trombito.rallycopilot.ui.copiloto

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.datos.EstadoComplemento
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.ProgresoDescarga
import app.trombito.rallycopilot.domain.escucha.DialogoVoz
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.datos.Complemento
import app.trombito.rallycopilot.motor.edicion.Orden
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.FalsoControlOrdenes
import app.trombito.rallycopilot.ui.comun.FalsoLocutor
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioAjustesConduccion
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioDatosMapa
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CopilotoViewModelTest {
    private val repositorio = FalsoRepositorioAjustesConduccion()
    private val locutor = FalsoLocutor()
    private val ordenes = FalsoControlOrdenes()
    private val datos = FalsoRepositorioDatosMapa()

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun TestScope.crear(): CopilotoViewModel {
        val vm = CopilotoViewModel(repositorio, locutor, ordenes, datos)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `antes de leer los ajustes no se pregunta la voz`() {
        assertFalse(CopilotoUiState().cargado)
        assertFalse(CopilotoUiState().pedirVoz)
    }

    @Test
    fun `el modo inicial es completo y sin voz elegida se pide la voz`() =
        runTest {
            val vm = crear()
            val estado = vm.uiState.value
            assertTrue(estado.cargado)
            assertEquals(ModoCopiloto.COMPLETO, estado.modo)
            assertNull(estado.voz)
            assertTrue(estado.pedirVoz)
        }

    @Test
    fun `con la voz ya elegida no se pide`() =
        runTest {
            repositorio.mutable.value = AjustesConduccion(voz = VozCopiloto.SHARVARD)
            val vm = crear()
            assertEquals(VozCopiloto.SHARVARD, vm.uiState.value.voz)
            assertFalse(vm.uiState.value.pedirVoz)
        }

    @Test
    fun `elegir la voz la guarda y deja de pedirla`() =
        runTest {
            val vm = crear()
            vm.onElegirVoz(VozCopiloto.SHARVARD)
            assertEquals(VozCopiloto.SHARVARD, repositorio.mutable.value.voz)
            assertFalse(vm.uiState.value.pedirVoz)
        }

    @Test
    fun `elegir sharvard sin tenerla lanza su descarga y elegir dave no`() =
        runTest {
            val vm = crear()
            vm.onElegirVoz(VozCopiloto.DAVEFX)
            assertTrue(datos.complementosDescargados.isEmpty())
            vm.onElegirVoz(VozCopiloto.SHARVARD)
            assertEquals(listOf(Complemento.VOZ_SHARVARD), datos.complementosDescargados)
        }

    @Test
    fun `el boton descargar del dialogo baja sharvard una sola vez y no hace nada con dave`() =
        runTest {
            val vm = crear()
            vm.onDescargarVoz(VozCopiloto.DAVEFX)
            assertTrue(datos.complementosDescargados.isEmpty())
            vm.onDescargarVoz(VozCopiloto.SHARVARD)
            datos.estadoMutable.value =
                EstadoDatosMapa(
                    complementos =
                        mapOf(Complemento.VOZ_SHARVARD to EstadoComplemento(descarga = ProgresoDescarga(0, 0, true))),
                )
            vm.onDescargarVoz(VozCopiloto.SHARVARD)
            assertEquals(listOf(Complemento.VOZ_SHARVARD), datos.complementosDescargados)
        }

    @Test
    fun `posponer oculta el dialogo sin guardar ninguna voz`() =
        runTest {
            val vm = crear()
            vm.onPosponerVoz()
            assertFalse(vm.uiState.value.pedirVoz)
            assertNull(repositorio.mutable.value.voz)
            assertTrue(repositorio.guardados.isEmpty())
        }

    @Test
    fun `una instancia nueva vuelve a preguntar tras posponer`() =
        runTest {
            crear().onPosponerVoz()
            assertTrue(crear().uiState.value.pedirVoz)
        }

    @Test
    fun `cambiar el modo lo guarda conservando lo demas`() =
        runTest {
            repositorio.mutable.value = AjustesConduccion(nivelZoom = 2, voz = VozCopiloto.DAVEFX)
            val vm = crear()
            vm.onModo(ModoCopiloto.NOTAS)
            assertEquals(ModoCopiloto.NOTAS, vm.uiState.value.modo)
            val guardado = repositorio.mutable.value
            assertEquals(ModoCopiloto.NOTAS, guardado.modoCopiloto)
            assertEquals(2, guardado.nivelZoom)
            assertEquals(VozCopiloto.DAVEFX, guardado.voz)
        }

    @Test
    fun `probar llama al locutor con esa voz y no guarda nada`() =
        runTest {
            val vm = crear()
            vm.onProbarVoz(VozCopiloto.DAVEFX)
            vm.onProbarVoz(VozCopiloto.SHARVARD)
            assertEquals(listOf(VozCopiloto.DAVEFX, VozCopiloto.SHARVARD), locutor.probadas)
            assertTrue(repositorio.guardados.isEmpty())
        }

    @Test
    fun `el microfono pide escuchar una orden y el estado refleja que escucha`() =
        runTest {
            val vm = crear()
            assertFalse(vm.uiState.value.escuchando)

            vm.onEscuchar()
            ordenes.estaEscuchando.value = true

            assertEquals(1, ordenes.peticiones)
            assertTrue(vm.uiState.value.escuchando)
        }

    @Test
    fun `las notas grandes se guardan, se recuerdan en el estado y conservan lo demas`() =
        runTest {
            repositorio.mutable.value = AjustesConduccion(modoCopiloto = ModoCopiloto.NOTAS)
            val vm = crear()
            assertFalse(vm.uiState.value.notasGrandes)

            vm.onNotasGrandes(true)

            assertTrue(vm.uiState.value.notasGrandes)
            assertTrue(repositorio.mutable.value.notasGrandes)
            assertEquals(ModoCopiloto.NOTAS, repositorio.mutable.value.modoCopiloto)
        }

    @Test
    fun `la salida manual llega al estado, se guarda y conserva lo demas`() =
        runTest {
            repositorio.mutable.value = AjustesConduccion(modoCopiloto = ModoCopiloto.NOTAS)
            val vm = crear()
            assertFalse(vm.uiState.value.salidaManual)

            vm.onSalidaManual(true)
            assertTrue(vm.uiState.value.salidaManual)
            assertTrue(repositorio.mutable.value.salidaManual)
            assertEquals(ModoCopiloto.NOTAS, repositorio.mutable.value.modoCopiloto)

            vm.onSalidaManual(false)
            assertFalse(repositorio.mutable.value.salidaManual)
        }

    @Test
    fun `los interruptores de indicaciones llegan al estado, se guardan uno a uno y conservan lo demas`() =
        runTest {
            repositorio.mutable.value = AjustesConduccion(modoCopiloto = ModoCopiloto.NOTAS)
            val vm = crear()
            assertFalse(vm.uiState.value.avisarCruces)
            assertFalse(vm.uiState.value.avisarPuentes)
            assertTrue(vm.uiState.value.avisarSalidasAutopista)
            assertTrue(vm.uiState.value.avisarRotondas)

            vm.onAvisarCruces(true)
            vm.onAvisarPuentes(true)
            assertTrue(vm.uiState.value.avisarCruces)
            assertTrue(vm.uiState.value.avisarPuentes)
            assertTrue(repositorio.mutable.value.avisarCruces)
            assertTrue(repositorio.mutable.value.avisarPuentes)
            assertTrue(repositorio.mutable.value.avisarRotondas)

            vm.onAvisarPuentes(false)
            assertFalse(vm.uiState.value.avisarPuentes)
            assertTrue(vm.uiState.value.avisarCruces)

            vm.onAvisarSalidasAutopista(false)
            vm.onAvisarRotondas(false)
            assertFalse(vm.uiState.value.avisarSalidasAutopista)
            assertFalse(vm.uiState.value.avisarRotondas)

            vm.onAvisarCruces(false)
            assertFalse(vm.uiState.value.avisarCruces)
            assertFalse(repositorio.mutable.value.avisarRotondas)
            assertEquals(ModoCopiloto.NOTAS, repositorio.mutable.value.modoCopiloto)
        }

    @Test
    fun `el dialogo de voz nuevo llega al estado y el de antes de abrir el mapa no`() =
        runTest {
            ordenes.ultimoDialogo.value = DialogoVoz(1, "satélite", "hecho")
            val vm = crear()
            assertNull(vm.uiState.value.dialogo)

            val nuevo = DialogoVoz(2, "modo notas", "modo notas", Orden.Silencio)
            ordenes.ultimoDialogo.value = nuevo

            assertEquals(nuevo, vm.uiState.value.dialogo)
        }
}
