package app.trombito.rallycopilot.ui.ajustes

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.ajustes.Tema
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.FalsoLocutor
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioAjustesConduccion
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
class AjustesViewModelTest {
    private val repositorio = FalsoRepositorioAjustesConduccion()
    private val locutor = FalsoLocutor()

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun TestScope.crear(): AjustesViewModel {
        val vm = AjustesViewModel(repositorio, locutor)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `el estado refleja lo guardado`() =
        runTest {
            repositorio.mutable.value = AjustesConduccion(nivelZoom = -1, icono = IconoPosicion.CASCO)
            val vm = crear()
            assertEquals(-1, vm.uiState.value.nivelZoom)
            assertEquals(IconoPosicion.CASCO, vm.uiState.value.icono)
        }

    @Test
    fun `el nivel de zoom se acota entre -2 y 2`() =
        runTest {
            val vm = crear()
            vm.onNivelZoom(7)
            assertEquals(AjustesConduccion.NIVEL_MAX, vm.uiState.value.nivelZoom)
            vm.onNivelZoom(-9)
            assertEquals(AjustesConduccion.NIVEL_MIN, vm.uiState.value.nivelZoom)
            vm.onNivelZoom(1)
            assertEquals(1, repositorio.mutable.value.nivelZoom)
        }

    @Test
    fun `el icono y la orientacion se guardan y conservan el resto`() =
        runTest {
            repositorio.mutable.value = AjustesConduccion(capa = CapaMapa.SATELITE, relieve = true, nivelZoom = 2)
            val vm = crear()
            vm.onIcono(IconoPosicion.COCHE_RALLY)
            vm.onOrientacion(Orientacion.NORTE)
            val guardado = repositorio.mutable.value
            assertEquals(IconoPosicion.COCHE_RALLY, guardado.icono)
            assertEquals(Orientacion.NORTE, guardado.orientacion)
            assertEquals(CapaMapa.SATELITE, guardado.capa)
            assertEquals(true, guardado.relieve)
            assertEquals(2, guardado.nivelZoom)
            assertEquals(2, repositorio.guardados.size)
        }

    @Test
    fun `la anticipacion y las notas simplificadas se guardan`() =
        runTest {
            val vm = crear()
            assertEquals(NivelAnticipacion.NORMAL, vm.uiState.value.anticipacion)
            assertFalse(vm.uiState.value.notasSimplificadas)
            vm.onAnticipacion(NivelAnticipacion.MUY_PRONTO)
            vm.onNotasSimplificadas(true)
            assertEquals(NivelAnticipacion.MUY_PRONTO, repositorio.mutable.value.anticipacion)
            assertTrue(repositorio.mutable.value.notasSimplificadas)
            // Lo demás no se toca.
            assertEquals(AjustesConduccion().nivelZoom, repositorio.mutable.value.nivelZoom)
        }

    @Test
    fun `el modo y la voz del copiloto se guardan sin tocar el resto`() =
        runTest {
            repositorio.mutable.value = AjustesConduccion(nivelZoom = 1)
            val vm = crear()
            assertEquals(ModoCopiloto.COMPLETO, vm.uiState.value.modoCopiloto)
            assertNull(vm.uiState.value.voz)
            vm.onModoCopiloto(ModoCopiloto.SOLO_ALERTAS)
            vm.onVozCopiloto(VozCopiloto.SHARVARD)
            val guardado = repositorio.mutable.value
            assertEquals(ModoCopiloto.SOLO_ALERTAS, guardado.modoCopiloto)
            assertEquals(VozCopiloto.SHARVARD, guardado.voz)
            assertEquals(1, guardado.nivelZoom)
        }

    @Test
    fun `probar una voz llama al locutor y no cambia la elegida`() =
        runTest {
            repositorio.mutable.value = AjustesConduccion(voz = VozCopiloto.DAVEFX)
            val vm = crear()
            vm.onProbarVoz(VozCopiloto.SHARVARD)
            assertEquals(listOf(VozCopiloto.SHARVARD), locutor.probadas)
            assertEquals(VozCopiloto.DAVEFX, repositorio.mutable.value.voz)
            assertTrue(repositorio.guardados.isEmpty())
        }

    @Test
    fun `el volumen se acota a 50-150, se guarda y se aplica al locutor al instante`() =
        runTest {
            repositorio.mutable.value = AjustesConduccion(nivelZoom = 1)
            val vm = crear()
            assertEquals(100, vm.uiState.value.volumenVoz)
            vm.onVolumenVoz(130)
            vm.onVolumenVoz(400)
            vm.onVolumenVoz(0)
            assertEquals(listOf(130, 150, 50), locutor.volumenes)
            assertEquals(50, repositorio.mutable.value.volumenVoz)
            assertEquals(1, repositorio.mutable.value.nivelZoom)
        }

    @Test
    fun `escuchar ejemplo dice la nota con la voz, el volumen y las simplificadas elegidos`() =
        runTest {
            repositorio.mutable.value = AjustesConduccion(voz = VozCopiloto.SHARVARD, volumenVoz = 70)
            val vm = crear()
            vm.onEscucharEjemplo()
            vm.onNotasSimplificadas(true)
            vm.onEscucharEjemplo()
            assertEquals(listOf(70, 70), locutor.volumenes)
            assertEquals(
                listOf(
                    VozCopiloto.SHARVARD to "derecha cuatro larga, cincuenta",
                    VozCopiloto.SHARVARD to "derecha media larga, cincuenta",
                ),
                locutor.ejemplos,
            )
        }

    @Test
    fun `probar una voz usa el volumen elegido`() =
        runTest {
            repositorio.mutable.value = AjustesConduccion(volumenVoz = 120)
            val vm = crear()
            vm.onProbarVoz(VozCopiloto.DAVEFX)
            assertEquals(listOf(120), locutor.volumenes)
        }

    @Test
    fun `sin voz elegida el ejemplo suena con la primera`() =
        runTest {
            val vm = crear()
            vm.onEscucharEjemplo()
            assertEquals(VozCopiloto.DAVEFX, locutor.ejemplos.single().first)
        }

    @Test
    fun `el tema la capa y el relieve se guardan sin tocar el resto`() =
        runTest {
            repositorio.mutable.value = AjustesConduccion(nivelZoom = 2)
            val vm = crear()
            assertEquals(Tema.OSCURO, vm.uiState.value.tema)
            vm.onTema(Tema.CLARO)
            vm.onCapa(CapaMapa.SATELITE)
            vm.onRelieve(true)
            val guardado = repositorio.mutable.value
            assertEquals(Tema.CLARO, guardado.tema)
            assertEquals(CapaMapa.SATELITE, guardado.capa)
            assertTrue(guardado.relieve)
            assertEquals(2, guardado.nivelZoom)
        }

    @Test
    fun `cronometrar y notas grandes se guardan sin tocar el resto`() =
        runTest {
            repositorio.mutable.value = AjustesConduccion(nivelZoom = 1)
            val vm = crear()
            assertTrue(vm.uiState.value.cronometrar)
            assertFalse(vm.uiState.value.notasGrandes)
            vm.onCronometrar(false)
            vm.onNotasGrandes(true)
            val guardado = repositorio.mutable.value
            assertFalse(guardado.cronometrar)
            assertTrue(guardado.notasGrandes)
            assertEquals(1, guardado.nivelZoom)
        }

    @Test
    fun `las indicaciones se guardan una a una y conservan el resto`() =
        runTest {
            repositorio.mutable.value = AjustesConduccion(nivelZoom = 1)
            val vm = crear()
            vm.onAvisarCruces(false)
            assertEquals(false, repositorio.mutable.value.avisarCruces)
            assertTrue(repositorio.mutable.value.avisarSalidasAutopista)
            vm.onAvisarSalidasAutopista(false)
            vm.onAvisarRotondas(false)
            val guardado = repositorio.mutable.value
            assertFalse(guardado.avisarCruces || guardado.avisarSalidasAutopista || guardado.avisarRotondas)
            assertEquals(1, guardado.nivelZoom)
            vm.onAvisarRotondas(true)
            assertTrue(repositorio.mutable.value.avisarRotondas)
        }
}
