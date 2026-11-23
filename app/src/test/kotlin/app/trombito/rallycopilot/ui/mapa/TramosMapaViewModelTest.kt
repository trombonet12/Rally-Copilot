package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.tramos.ProveedorTramosPorDelante
import app.trombito.rallycopilot.domain.tramos.TramosPorDelante
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.TramoEnCamino
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TramosMapaViewModelTest {
    private val fuente = MutableStateFlow(TramosPorDelante())

    private val proveedor =
        object : ProveedorTramosPorDelante {
            override val porDelante: Flow<TramosPorDelante> = fuente
        }

    private val trozo =
        TramoEnCamino(
            "A",
            0.0,
            1_000.0,
            listOf(PuntoGeo(39.7, 2.9), PuntoGeo(39.71, 2.9)),
            PuntoGeo(39.7, 2.9),
            PuntoGeo(39.71, 2.9),
            true,
            true,
            0.0,
            1_000.0,
        )

    @Before
    fun preparar() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun limpiar() = Dispatchers.resetMain()

    @Test
    fun `publica los tramos y la distancia a la meta a pasos de 10 m`() =
        runTest {
            val vm = TramosMapaViewModel(proveedor)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.tramos.collect { } }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.finDeTramoM.collect { } }
            assertNull(vm.finDeTramoM.value)

            fuente.value = TramosPorDelante(listOf(trozo), 823.0)

            assertEquals(listOf(trozo), vm.tramos.value)
            assertEquals(820.0, vm.finDeTramoM.value!!, 0.0)
        }

    @Test
    fun `la distancia sola no vuelve a emitir los tramos`() =
        runTest {
            val vm = TramosMapaViewModel(proveedor)
            val emitidos = mutableListOf<List<TramoEnCamino>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.tramos.collect { emitidos += it } }
            val lista = listOf(trozo)
            fuente.value = TramosPorDelante(lista, 900.0)
            fuente.value = TramosPorDelante(lista, 850.0)
            fuente.value = TramosPorDelante(lista, 800.0)

            assertEquals(2, emitidos.size)
            assertSame(lista, emitidos.last())
        }
}
