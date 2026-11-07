package app.trombito.rallycopilot.ui.mapa

import androidx.lifecycle.SavedStateHandle
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.voz.PrioridadVoz
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.FalsaFuenteUbicacion
import app.trombito.rallycopilot.ui.comun.FalsoLocutor
import app.trombito.rallycopilot.ui.comun.FalsoProveedorVia
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioAjustesConduccion
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioDatosMapa
import app.trombito.rallycopilot.ui.comun.fijacion
import app.trombito.rallycopilot.ui.navegacion.FalsoControlNavegacion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * La señal GPS del mapa (banda «Sin señal GPS» y aviso del copiloto) de punta a punta con el reloj virtual: banda a los
 * 10 s, voz solo si la pérdida pasa de 30 s, histéresis, enfriamiento y fijaciones antiguas (QA-1, QA-2, UX-1, UX-5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapaViewModelSenalTest {
    private val instalados = DatosInstalados("2026.10.1", 1_000_000, conSatelite = true)
    private val estilos = EstilosMapa(vectorial = "{\"v\":1}", satelite = "{\"s\":1}")
    private val repositorio = FalsoRepositorioDatosMapa(EstadoDatosMapa(instalados = instalados), estilos)
    private val ubicacion = FalsaFuenteUbicacion()
    private val vias = FalsoProveedorVia()
    private val ajustes = FalsoRepositorioAjustesConduccion()
    private val navegacion = FalsoControlNavegacion()
    private val locutor = FalsoLocutor()

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    /** Reloj monótono del test: el tiempo virtual del planificador más un origen común con las fijaciones. */
    private var origenRelojMs = 0L
    private lateinit var planificador: TestCoroutineScheduler

    private fun crear() =
        MapaViewModel(repositorio, ubicacion, vias, ajustes, navegacion, SavedStateHandle(), locutor).also {
            it.reloj = { origenRelojMs + (if (::planificador.isInitialized) planificador.currentTime else 0L) }
        }

    private fun TestScope.recoger(vm: MapaViewModel): Job {
        planificador = testScheduler
        return backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    private suspend fun emitir(f: Fijacion) {
        ubicacion.flujo.emit(f)
    }

    private fun TestScope.avanzarReloj(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `tras 10 s sin fijacion util sale la banda y el copiloto lo dice una sola vez a los 30 s`() =
        runTest {
            ajustes.mutable.value = AjustesConduccion(voz = VozCopiloto.DAVEFX)
            navegacion.publicar(EstadoNavegacion.Libre(0, 0.0))
            val vm = crear()
            recoger(vm)
            origenRelojMs = 1_000
            emitir(fijacion(tiempoMs = 1_000, velocidadMs = 16.7))
            assertFalse(vm.uiState.value.sinSenalGps)
            avanzarReloj(9_000)
            assertFalse("a los 9 s todavia hay senal", vm.uiState.value.sinSenalGps)
            avanzarReloj(2_000)
            assertTrue(vm.uiState.value.sinSenalGps)
            assertTrue("la voz espera a los 30 s", locutor.dichas.isEmpty())
            avanzarReloj(25_000)
            assertEquals(listOf("Sin señal GPS"), locutor.dichas)
            avanzarReloj(60_000)
            assertEquals("una sola vez, no cada segundo", listOf("Sin señal GPS"), locutor.dichas)
            val frase = locutor.frases.single()
            assertEquals("urgente", PrioridadVoz.URGENTE, frase.prioridad)
            assertTrue("caduca a los 5 s", frase.caducaMs != null)
        }

    @Test
    fun `al volver la senal tras una perdida larga el copiloto dice GPS recuperado`() =
        runTest {
            ajustes.mutable.value = AjustesConduccion(voz = VozCopiloto.DAVEFX)
            navegacion.publicar(EstadoNavegacion.Libre(0, 0.0))
            val vm = crear()
            recoger(vm)
            origenRelojMs = 1_000
            emitir(fijacion(tiempoMs = 1_000, velocidadMs = 16.7))
            avanzarReloj(40_000)
            assertTrue(vm.uiState.value.sinSenalGps)
            emitir(fijacion(tiempoMs = 41_000, velocidadMs = 16.7))
            assertTrue("una sola fijacion no recupera", vm.uiState.value.sinSenalGps)
            avanzarReloj(1_000)
            emitir(fijacion(tiempoMs = 42_000, velocidadMs = 16.7))
            avanzarReloj(1_000)
            emitir(fijacion(tiempoMs = 43_000, velocidadMs = 16.7))
            assertFalse(vm.uiState.value.sinSenalGps)
            assertEquals(listOf("Sin señal GPS", "GPS recuperado"), locutor.dichas)
        }

    @Test
    fun `una fijacion de mas de 30 m de error no devuelve la senal`() =
        runTest {
            navegacion.publicar(EstadoNavegacion.Libre(0, 0.0))
            val vm = crear()
            recoger(vm)
            origenRelojMs = 1_000
            emitir(fijacion(tiempoMs = 1_000, velocidadMs = 16.7))
            avanzarReloj(12_000)
            assertTrue(vm.uiState.value.sinSenalGps)
            for (i in 0 until 4) {
                emitir(fijacion(tiempoMs = 13_000 + i * 1_000L, velocidadMs = 16.7).copy(precisionM = 40.0))
                avanzarReloj(1_000)
            }
            assertTrue(vm.uiState.value.sinSenalGps)
        }

    @Test
    fun `en silencio o sin conducir el copiloto no avisa pero el estado si cambia`() =
        runTest {
            ajustes.mutable.value = AjustesConduccion(modoCopiloto = ModoCopiloto.SILENCIO, voz = VozCopiloto.DAVEFX)
            navegacion.publicar(EstadoNavegacion.Libre(0, 0.0))
            val vm = crear()
            recoger(vm)
            origenRelojMs = 1_000
            emitir(fijacion(tiempoMs = 1_000, velocidadMs = 16.7))
            avanzarReloj(40_000)
            assertTrue(vm.uiState.value.sinSenalGps)
            assertTrue(locutor.dichas.isEmpty())

            ajustes.mutable.value = AjustesConduccion(modoCopiloto = ModoCopiloto.COMPLETO, voz = VozCopiloto.DAVEFX)
            navegacion.publicar(EstadoNavegacion.Inactiva)
            for (i in 0 until 3) {
                emitir(fijacion(tiempoMs = 41_000 + i * 1_000L, velocidadMs = 16.7))
                avanzarReloj(1_000)
            }
            avanzarReloj(45_000)
            assertTrue("sin conduccion no se dice nada", locutor.dichas.isEmpty())
        }

    @Test
    fun `pasar de Libre a Inactiva a mitad de perdida y volver no deja avisos atrasados`() =
        runTest {
            ajustes.mutable.value = AjustesConduccion(voz = VozCopiloto.DAVEFX)
            navegacion.publicar(EstadoNavegacion.Libre(0, 0.0))
            val vm = crear()
            recoger(vm)
            origenRelojMs = 1_000
            emitir(fijacion(tiempoMs = 1_000, velocidadMs = 16.7))
            avanzarReloj(20_000)
            navegacion.publicar(EstadoNavegacion.Inactiva)
            avanzarReloj(20_000)
            assertTrue("sin conduccion no se dice nada", locutor.dichas.isEmpty())
            for (i in 0 until 3) {
                emitir(fijacion(tiempoMs = 41_000 + i * 1_000L, velocidadMs = 16.7))
                avanzarReloj(1_000)
            }
            navegacion.publicar(EstadoNavegacion.Libre(0, 0.0))
            avanzarReloj(5_000)
            assertFalse(vm.uiState.value.sinSenalGps)
            assertTrue("la perdida ya acabo: nada atrasado ${locutor.dichas}", locutor.dichas.isEmpty())
        }

    @Test
    fun `senal debil intermitente no canta Sin senal en 60 s`() =
        runTest {
            ajustes.mutable.value = AjustesConduccion(voz = VozCopiloto.DAVEFX)
            navegacion.publicar(EstadoNavegacion.Libre(0, 0.0))
            val vm = crear()
            recoger(vm)
            origenRelojMs = 1_000
            // Una buena cada 8 s: nunca 10 s sin fijacion util.
            repeat(8) { i ->
                emitir(fijacion(tiempoMs = 1_000 + i * 8_000L, velocidadMs = 16.7))
                avanzarReloj(8_000)
            }
            assertFalse(vm.uiState.value.sinSenalGps)
            assertTrue("sin voz: ${locutor.dichas}", locutor.dichas.isEmpty())
        }

    @Test
    fun `una fijacion antigua de arranque (ultima conocida) no provoca aviso`() =
        runTest {
            ajustes.mutable.value = AjustesConduccion(voz = VozCopiloto.DAVEFX)
            navegacion.publicar(EstadoNavegacion.Libre(0, 0.0))
            val vm = crear()
            recoger(vm)
            origenRelojMs = 100_000
            emitir(fijacion(tiempoMs = origenRelojMs - 60_000, velocidadMs = 16.7))
            avanzarReloj(3_000)
            assertTrue(locutor.dichas.isEmpty())
            assertFalse(vm.uiState.value.sinSenalGps)
            avanzarReloj(30_000)
            assertTrue("la antigua no cuenta como senal perdida", locutor.dichas.isEmpty())
        }

    @Test
    fun `la banda sale a los 10 s pero la voz solo si la perdida pasa de 30 s`() =
        runTest {
            ajustes.mutable.value = AjustesConduccion(voz = VozCopiloto.DAVEFX)
            navegacion.publicar(EstadoNavegacion.Libre(0, 0.0))
            val vm = crear()
            recoger(vm)
            origenRelojMs = 1_000
            emitir(fijacion(tiempoMs = 1_000, velocidadMs = 16.7))
            avanzarReloj(9_000)
            assertFalse("a los 9 s todavia no", vm.uiState.value.sinSenalGps)
            avanzarReloj(2_000)
            assertTrue("a los 11 s hay banda", vm.uiState.value.sinSenalGps)
            assertTrue("pero no voz", locutor.dichas.isEmpty())
            avanzarReloj(25_000)
            assertEquals(listOf("Sin señal GPS"), locutor.dichas)
        }

    @Test
    fun `una perdida corta con banda no dice nada ni al recuperarse`() =
        runTest {
            ajustes.mutable.value = AjustesConduccion(voz = VozCopiloto.DAVEFX)
            navegacion.publicar(EstadoNavegacion.Libre(0, 0.0))
            val vm = crear()
            recoger(vm)
            origenRelojMs = 1_000
            emitir(fijacion(tiempoMs = 1_000, velocidadMs = 16.7))
            avanzarReloj(14_000)
            assertTrue(vm.uiState.value.sinSenalGps)
            for (i in 0 until 3) {
                emitir(fijacion(tiempoMs = 15_000 + i * 1_000L, velocidadMs = 16.7))
                avanzarReloj(1_000)
            }
            assertFalse(vm.uiState.value.sinSenalGps)
            assertTrue(locutor.dichas.isEmpty())
        }

    @Test
    fun `dos perdidas largas seguidas solo cantan una vez por el enfriamiento`() =
        runTest {
            ajustes.mutable.value = AjustesConduccion(voz = VozCopiloto.DAVEFX)
            navegacion.publicar(EstadoNavegacion.Libre(0, 0.0))
            val vm = crear()
            recoger(vm)
            origenRelojMs = 1_000
            emitir(fijacion(tiempoMs = 1_000, velocidadMs = 16.7))
            avanzarReloj(40_000)
            for (i in 0 until 3) {
                emitir(fijacion(tiempoMs = 41_000 + i * 1_000L, velocidadMs = 16.7))
                avanzarReloj(1_000)
            }
            assertEquals(listOf("Sin señal GPS", "GPS recuperado"), locutor.dichas)
            // Segunda perdida larga a los pocos segundos: dentro de los 3 min, sin voz.
            avanzarReloj(45_000)
            assertTrue(vm.uiState.value.sinSenalGps)
            assertEquals(2, locutor.dichas.size)
        }

    @Test
    fun `con la voz sin elegir el copiloto no avisa de la perdida de GPS`() =
        runTest {
            navegacion.publicar(EstadoNavegacion.Libre(0, 0.0))
            val vm = crear()
            recoger(vm)
            origenRelojMs = 1_000
            emitir(fijacion(tiempoMs = 1_000, velocidadMs = 16.7))
            avanzarReloj(60_000)
            assertTrue(vm.uiState.value.sinSenalGps)
            assertTrue(locutor.dichas.isEmpty())
        }

    @Test
    fun `revocar y volver a conceder el permiso reinicia el detector sin aviso falso`() =
        runTest {
            ajustes.mutable.value = AjustesConduccion(voz = VozCopiloto.DAVEFX)
            navegacion.publicar(EstadoNavegacion.Libre(0, 0.0))
            val vm = crear()
            recoger(vm)
            origenRelojMs = 1_000
            emitir(fijacion(tiempoMs = 1_000, velocidadMs = 16.7))
            vm.onPermisoUbicacion(false)
            avanzarReloj(60_000)
            vm.onPermisoUbicacion(true)
            avanzarReloj(5_000)
            assertFalse(vm.uiState.value.sinSenalGps)
            assertTrue(locutor.dichas.isEmpty())
        }

    @Test
    fun `sin ninguna fijacion no hay aviso de GPS`() =
        runTest {
            navegacion.publicar(EstadoNavegacion.Libre(0, 0.0))
            val vm = crear()
            recoger(vm)
            avanzarReloj(30_000)
            assertFalse(vm.uiState.value.sinSenalGps)
            assertTrue(locutor.dichas.isEmpty())
        }
}
