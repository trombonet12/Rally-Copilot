package app.trombito.rallycopilot.ui.mapa

import androidx.lifecycle.SavedStateHandle
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.ui.comun.FalsaFuenteUbicacion
import app.trombito.rallycopilot.ui.comun.FalsoLocutor
import app.trombito.rallycopilot.ui.comun.FalsoProveedorVia
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioAjustesConduccion
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioDatosMapa
import app.trombito.rallycopilot.ui.comun.fijacion
import app.trombito.rallycopilot.ui.navegacion.FalsoControlNavegacion
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapaViewModelEstelaTest {
    private val repositorio =
        FalsoRepositorioDatosMapa(
            EstadoDatosMapa(instalados = DatosInstalados("2026.10.1", 1_000_000, conSatelite = true)),
            EstilosMapa(vectorial = "{\"v\":1}", satelite = "{\"s\":1}"),
        )
    private val ubicacion = FalsaFuenteUbicacion()
    private val navegacion = FalsoControlNavegacion()

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun TestScope.crear(): MapaViewModel {
        val vm =
            MapaViewModel(
                repositorio,
                ubicacion,
                FalsoProveedorVia(),
                FalsoRepositorioAjustesConduccion(),
                navegacion,
                SavedStateHandle(),
                FalsoLocutor(),
            )
        vm.reloj = { testScheduler.currentTime }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    /** Una fijación más al este que la anterior (unos 11 m de paso con el valor por defecto). */
    private suspend fun TestScope.fijar(
        indice: Int,
        pasoLon: Double = PASO_LON,
    ) {
        ubicacion.flujo.emit(fijacion(testScheduler.currentTime, 10.0, lon = 2.90 + indice * pasoLon))
    }

    @Test
    fun `la estela crece con las fijaciones, a como mucho una publicacion por segundo`() =
        runTest {
            val vm = crear()
            fijar(0)
            assertEquals(
                1,
                vm.estela.value
                    .flatten()
                    .size,
            )
            fijar(1)
            // Mismo segundo: se guarda pero aún no se publica.
            assertEquals(
                1,
                vm.estela.value
                    .flatten()
                    .size,
            )
            advanceTimeBy(1_000)
            fijar(2)
            assertEquals(
                3,
                vm.estela.value
                    .flatten()
                    .size,
            )
        }

    @Test
    fun `las fijaciones a menos de 5 m no alargan la estela`() =
        runTest {
            val vm = crear()
            fijar(0)
            advanceTimeBy(1_000)
            fijar(1, pasoLon = 2.0 / 85_500.0)
            assertEquals(
                1,
                vm.estela.value
                    .flatten()
                    .size,
            )
        }

    @Test
    fun `la estela se borra al terminar la conduccion`() =
        runTest {
            navegacion.publicar(EstadoNavegacion.Libre(0, 0.0))
            val vm = crear()
            fijar(0)
            advanceTimeBy(1_000)
            fijar(1)
            assertEquals(
                2,
                vm.estela.value
                    .flatten()
                    .size,
            )
            navegacion.publicar(EstadoNavegacion.Inactiva)
            assertTrue(vm.estela.value.isEmpty())
        }

    private companion object {
        const val PASO_LON = 11.0 / 85_500.0
    }
}
