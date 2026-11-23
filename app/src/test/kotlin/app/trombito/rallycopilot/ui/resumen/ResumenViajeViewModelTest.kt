package app.trombito.rallycopilot.ui.resumen

import androidx.lifecycle.SavedStateHandle
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.resumen.CalculoResumenViaje
import app.trombito.rallycopilot.domain.resumen.ViajeResumido
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioDatosMapa
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ResumenViajeViewModelTest {
    private val estilos = EstilosMapa(vectorial = "{\"version\":8}", satelite = null)

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun crear(
        estilosMapa: EstilosMapa? = estilos,
        recorridoId: Long = 7L,
        calculo: CalculoResumenViaje,
    ) = ResumenViajeViewModel(
        SavedStateHandle(mapOf("recorridoId" to recorridoId)),
        calculo,
        FalsoRepositorioDatosMapa(estilosDisponibles = estilosMapa),
    )

    @Test
    fun `empieza cargando y al terminar enseña el resumen con el estilo del mapa`() =
        runTest {
            val espera = CompletableDeferred<ViajeResumido?>()
            val vm = crear { espera.await() }
            assertTrue(vm.uiState.value.cargando)
            assertNull(vm.uiState.value.viaje)

            val viaje = FixtureResumen.viaje()
            espera.complete(viaje)
            val estado = vm.uiState.value
            assertFalse(estado.cargando)
            assertFalse(estado.error)
            assertEquals(viaje, estado.viaje)
            assertEquals("{\"version\":8}", estado.estiloMapaJson)
        }

    @Test
    fun `pide el resumen del recorrido de la ruta`() =
        runTest {
            val pedidos = mutableListOf<Long>()
            crear(recorridoId = 42L) { id ->
                pedidos += id
                null
            }
            assertEquals(listOf(42L), pedidos)
        }

    @Test
    fun `un recorrido que ya no existe deja el estado vacio`() =
        runTest {
            val vm = crear { null }
            val estado = vm.uiState.value
            assertFalse(estado.cargando)
            assertFalse(estado.error)
            assertNull(estado.viaje)
            assertNull(estado.estiloMapaJson)
        }

    @Test
    fun `sin datos de mapa el resumen sale sin estilo`() =
        runTest {
            val vm = crear(estilosMapa = null) { FixtureResumen.viaje() }
            assertNull(vm.uiState.value.estiloMapaJson)
            assertEquals(FixtureResumen.viaje(), vm.uiState.value.viaje)
        }

    @Test
    fun `si falla la lectura pasa a estado de error en vez de cerrar la app`() =
        runTest {
            val vm = crear { throw IOException("disco") }
            val estado = vm.uiState.value
            assertFalse(estado.cargando)
            assertTrue(estado.error)
            assertNull(estado.viaje)
        }
}
