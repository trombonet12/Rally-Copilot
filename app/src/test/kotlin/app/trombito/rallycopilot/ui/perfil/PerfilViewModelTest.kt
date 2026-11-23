package app.trombito.rallycopilot.ui.perfil

import android.database.sqlite.SQLiteException
import app.trombito.rallycopilot.domain.perfil.CalculoTablero
import app.trombito.rallycopilot.domain.perfil.CalculoTemporada
import app.trombito.rallycopilot.domain.perfil.TableroPiloto
import app.trombito.rallycopilot.domain.perfil.TemporadaPiloto
import app.trombito.rallycopilot.domain.perfil.UltimoViaje
import app.trombito.rallycopilot.motor.analisis.Conclusion
import app.trombito.rallycopilot.motor.analisis.TipoConclusion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/** Cálculo en memoria: emite lo que se le ponga en [tablero], o lanza [fallo] al observarlo. */
private class CalculoTableroFalso(
    inicial: TableroPiloto = TableroPiloto(),
    private val fallo: Throwable? = null,
) : CalculoTablero {
    val tablero = MutableStateFlow(inicial)

    override fun observar(): Flow<TableroPiloto> =
        if (fallo == null) {
            tablero
        } else {
            flow { throw fallo }
        }
}

/** Temporada en memoria: emite lo que se le ponga en [temporada], o lanza [fallo] al observarla. */
private class CalculoTemporadaFalso(
    inicial: TemporadaPiloto = temporadaDePrueba(),
    private val fallo: Throwable? = null,
) : CalculoTemporada {
    val temporada = MutableStateFlow(inicial)

    override fun observar(): Flow<TemporadaPiloto> =
        if (fallo == null) {
            temporada
        } else {
            flow { throw fallo }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PerfilViewModelTest {
    private val ritmo = Conclusion(TipoConclusion.RITMO, "71 km/h", "Vas a 71 km/h de media en marcha.")
    private val records = Conclusion(TipoConclusion.RECORDS, "2", "Has mejorado tu mejor tiempo 2 veces.")
    private val tablero =
        TableroPiloto(
            viajes = 3,
            kmTotales = 42.5,
            primerViajeMs = 1_000L,
            ultimoViaje = UltimoViaje(7L, 3_000L, 12_000.0, "Sóller"),
            // Desordenadas a propósito: el estado las ordena como el tablero.
            conclusiones = listOf(records, ritmo),
        )

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun TestScope.crear(
        calculo: CalculoTablero,
        temporada: CalculoTemporada = CalculoTemporadaFalso(),
    ): PerfilViewModel {
        val vm = PerfilViewModel(calculo, temporada, UnconfinedTestDispatcher(testScheduler))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `empieza cargando y luego ensena el tablero`() =
        runTest {
            val vm =
                PerfilViewModel(
                    CalculoTableroFalso(tablero),
                    CalculoTemporadaFalso(),
                    UnconfinedTestDispatcher(testScheduler),
                )
            assertTrue(vm.uiState.value.cargando)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            val estado = vm.uiState.value
            assertFalse(estado.cargando)
            assertFalse(estado.error)
            assertEquals(3, estado.tablero.viajes)
            assertEquals(42.5, estado.tablero.kmTotales, 1e-9)
            assertEquals(7L, estado.tablero.ultimoViaje?.id)
        }

    @Test
    fun `las conclusiones salen en el orden del tablero`() =
        runTest {
            val vm = crear(CalculoTableroFalso(tablero))
            assertEquals(
                listOf(TipoConclusion.RITMO, TipoConclusion.RECORDS),
                vm.uiState.value.conclusiones
                    .map { it.tipo },
            )
        }

    @Test
    fun `sin viajes el perfil esta vacio`() =
        runTest {
            val vm = crear(CalculoTableroFalso())
            assertFalse(vm.uiState.value.cargando)
            assertTrue(vm.uiState.value.vacio)
        }

    @Test
    fun `con un viaje pero sin conclusiones no esta vacio`() =
        runTest {
            val vm = crear(CalculoTableroFalso(TableroPiloto(viajes = 1, kmTotales = 2.0)))
            assertFalse(vm.uiState.value.vacio)
            assertTrue(
                vm.uiState.value.conclusiones
                    .isEmpty(),
            )
        }

    @Test
    fun `se actualiza cuando termina un viaje mas`() =
        runTest {
            val calculo = CalculoTableroFalso(tablero)
            val vm = crear(calculo)
            calculo.tablero.value = tablero.copy(viajes = 4, kmTotales = 50.0)
            assertEquals(4, vm.uiState.value.tablero.viajes)
            assertEquals(50.0, vm.uiState.value.tablero.kmTotales, 1e-9)
        }

    @Test
    fun `un fallo de la base pasa a error sin cerrar la pantalla`() =
        runTest {
            val vm = crear(CalculoTableroFalso(fallo = SQLiteException("disco")))
            assertTrue(vm.uiState.value.error)
            assertFalse(vm.uiState.value.cargando)
            assertFalse(vm.uiState.value.vacio)
        }

    @Test
    fun `un fallo de lectura de puntos tambien pasa a error`() =
        runTest {
            val vm = crear(CalculoTableroFalso(fallo = IOException("puntos")))
            assertTrue(vm.uiState.value.error)
        }

    @Test
    fun `la temporada llega con el tablero y se actualiza`() =
        runTest {
            val calculoTemporada = CalculoTemporadaFalso()
            val vm = crear(CalculoTableroFalso(tablero), calculoTemporada)
            val primera = vm.uiState.value.temporada
            assertEquals(3, primera?.temporada?.viajes)
            assertFalse(vm.uiState.value.temporadaError)
            calculoTemporada.temporada.value = temporadaDePrueba().let { it.copy(carne = it.carne.take(1)) }
            assertEquals(
                1,
                vm.uiState.value.temporada
                    ?.carne
                    ?.size,
            )
        }

    @Test
    fun `la temporada tarda mas que el tablero y mientras tanto no hay temporada`() =
        runTest {
            val lenta = MutableSharedFlow<TemporadaPiloto>()
            val vm =
                crear(
                    CalculoTableroFalso(tablero),
                    object : CalculoTemporada {
                        override fun observar(): Flow<TemporadaPiloto> = lenta
                    },
                )
            val estado = vm.uiState.value
            assertFalse(estado.cargando)
            assertEquals(3, estado.tablero.viajes)
            assertNull(estado.temporada)
            assertFalse(estado.temporadaError)
            lenta.emit(temporadaDePrueba())
            assertEquals(
                3,
                vm.uiState.value.temporada
                    ?.temporada
                    ?.viajes,
            )
        }

    @Test
    fun `un fallo solo de la temporada deja el tablero y lo avisa`() =
        runTest {
            val vm = crear(CalculoTableroFalso(tablero), CalculoTemporadaFalso(fallo = IOException("puntos")))
            val estado = vm.uiState.value
            assertFalse(estado.error)
            assertTrue(estado.temporadaError)
            assertNull(estado.temporada)
            assertEquals(3, estado.tablero.viajes)
        }
}
