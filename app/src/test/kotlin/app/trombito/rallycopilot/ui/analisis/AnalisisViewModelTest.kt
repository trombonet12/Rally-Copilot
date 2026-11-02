package app.trombito.rallycopilot.ui.analisis

import androidx.lifecycle.SavedStateHandle
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.tramos.PasadaDetallada
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioDatosMapa
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import app.trombito.rallycopilot.ui.comun.resumenFalso
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AnalisisViewModelTest {
    // Tres pasadas del mismo tramo: la 2 es la mejor (la más rápida), la 1 la más lenta.
    private val lenta = FixtureAnalisis.detallada(1, kmh = 50.0, inicioMs = 1_000_000_000_000L)
    private val rapida = FixtureAnalisis.detallada(2, kmh = 70.0, inicioMs = 1_001_000_000_000L, esMejor = true)
    private val media = FixtureAnalisis.detallada(3, kmh = 60.0, inicioMs = 1_002_000_000_000L)

    private val estilos = EstilosMapa(vectorial = "{\"version\":8}", satelite = null)

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun repositorio(pasadas: List<PasadaDetallada> = listOf(media, rapida, lenta)) =
        FalsoRepositorioTramos(
            resumenes = listOf(resumenFalso("t1", "Ma-2141 · Escorca", pasadas = pasadas.size)),
            pasadas = mapOf("t1" to pasadas),
            trazas = mapOf("t1" to FixtureAnalisis.tramo),
        )

    private fun TestScope.crear(
        pasadaId: Long,
        repo: FalsoRepositorioTramos = repositorio(),
        estilosMapa: EstilosMapa? = estilos,
    ): AnalisisViewModel {
        val datos = FalsoRepositorioDatosMapa(estilosDisponibles = estilosMapa)
        val vm = AnalisisViewModel(SavedStateHandle(mapOf("tramoId" to "t1", "pasadaId" to pasadaId)), repo, datos)
        vm.calculos = UnconfinedTestDispatcher(testScheduler)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `por defecto compara con la mejor pasada`() =
        runTest {
            val vm = crear(pasadaId = 1)
            val estado = vm.uiState.value
            assertFalse(estado.cargando)
            assertNull(estado.problema)
            assertEquals("Ma-2141 · Escorca", estado.tramoNombre)
            assertEquals(lenta.inicioMs, estado.inicioMs)
            assertEquals(2L, estado.comparadaId)
            assertEquals(70.0 / 50.0, estado.analisis!!.totalMs.toDouble() / rapida.totalMs, 0.01)
            // La analizada es más lenta que la mejor: delta positivo (pierde tiempo)
            assertTrue(estado.analisis.deltaTotalMs!! > 0L)
            assertEquals("{\"version\":8}", estado.estiloMapaJson)
        }

    @Test
    fun `si la analizada es la mejor compara con la segunda mejor`() =
        runTest {
            val vm = crear(pasadaId = 2)
            assertEquals(3L, vm.uiState.value.comparadaId)
            // Más rápida que la comparada: delta negativo
            assertTrue(
                vm.uiState.value.analisis!!
                    .deltaTotalMs!! < 0L,
            )
        }

    @Test
    fun `las opciones son las demas pasadas con la mejor primero`() =
        runTest {
            val vm = crear(pasadaId = 3)
            assertEquals(
                listOf(2L, 1L),
                vm.uiState.value.opciones
                    .map { it.id },
            )
            assertEquals(
                listOf(true, false),
                vm.uiState.value.opciones
                    .map { it.esMejor },
            )
            assertEquals(
                rapida.totalMs,
                vm.uiState.value.opciones
                    .first()
                    .totalMs,
            )
            assertEquals(
                2L,
                vm.uiState.value.comparada
                    ?.id,
            )
        }

    @Test
    fun `una unica pasada no tiene con quien compararse`() =
        runTest {
            val vm = crear(pasadaId = 2, repo = repositorio(listOf(rapida)))
            val estado = vm.uiState.value
            assertNull(estado.comparadaId)
            assertNull(estado.analisis!!.deltaTotalMs)
            assertTrue(estado.opciones.isEmpty())
            assertTrue(estado.velocidadComparada.isEmpty())
        }

    @Test
    fun `elegir otra comparada recalcula el delta y ninguna lo quita`() =
        runTest {
            val vm = crear(pasadaId = 1)
            val conMejor =
                vm.uiState.value.analisis!!
                    .deltaTotalMs!!
            vm.onComparar(3)
            assertEquals(3L, vm.uiState.value.comparadaId)
            val conMedia =
                vm.uiState.value.analisis!!
                    .deltaTotalMs!!
            assertTrue("contra la media pierde menos: $conMedia < $conMejor", conMedia in 1 until conMejor)
            assertTrue(
                vm.uiState.value.velocidadComparada
                    .isNotEmpty(),
            )
            vm.onComparar(null)
            assertNull(vm.uiState.value.comparadaId)
            assertNull(
                vm.uiState.value.analisis!!
                    .deltaTotalMs,
            )
            assertTrue(
                vm.uiState.value.analisis!!
                    .delta
                    .isEmpty(),
            )
            assertTrue(
                vm.uiState.value.velocidadComparada
                    .isEmpty(),
            )
        }

    @Test
    fun `no se puede comparar una pasada consigo misma ni con una que no existe`() =
        runTest {
            val vm = crear(pasadaId = 1)
            vm.onComparar(1)
            assertNull(vm.uiState.value.comparadaId)
            vm.onComparar(99)
            assertNull(vm.uiState.value.comparadaId)
        }

    @Test
    fun `la comparada por defecto es la mejor y si la analizada lo es, la mas rapida de las demas`() {
        assertEquals(2L, comparadaPorDefecto(1, listOf(media, rapida, lenta)))
        assertEquals(3L, comparadaPorDefecto(2, listOf(media, rapida, lenta)))
        assertNull(comparadaPorDefecto(2, listOf(rapida)))
        assertNull(comparadaPorDefecto(1, emptyList()))
    }

    @Test
    fun `una pasada que no existe queda como no encontrada`() =
        runTest {
            val vm = crear(pasadaId = 42)
            assertFalse(vm.uiState.value.cargando)
            assertEquals(ProblemaAnalisis.NO_ENCONTRADA, vm.uiState.value.problema)
            assertNull(vm.uiState.value.analisis)
        }

    @Test
    fun `sin mapa instalado el analisis sigue y no hay estilo`() =
        runTest {
            val vm = crear(pasadaId = 1, estilosMapa = null)
            assertNotNull(vm.uiState.value.analisis)
            assertNull(vm.uiState.value.estiloMapaJson)
        }

    @Test
    fun `el total del analisis coincide con la pasada cronometrada`() =
        runTest {
            val vm = crear(pasadaId = 1)
            assertEquals(
                lenta.totalMs,
                vm.uiState.value.analisis!!
                    .totalMs,
            )
            assertEquals(
                lenta.totalMs.toDouble(),
                vm.uiState.value.analisis!!
                    .sectores
                    .sumOf { it.tiempoMs }
                    .toDouble(),
                5.0,
            )
        }

    @Test
    fun `sin tocar nada no hay cursor ni marcador`() =
        runTest {
            val vm = crear(pasadaId = 1)
            assertNull(vm.uiState.value.repeticion.tMs)
            assertNull(vm.uiState.value.cursorM)
            assertNull(vm.uiState.value.posicion)
        }

    @Test
    fun `reproducir avanza el marcador y el cursor con el tiempo y al llegar al final se para`() =
        runTest {
            val vm = crear(pasadaId = 1)
            vm.onAlternarRepeticion()
            assertTrue(vm.uiState.value.repeticion.reproduciendo)
            assertEquals(0L, vm.uiState.value.repeticion.tMs)
            advanceTimeBy(10_050L)
            val t = vm.uiState.value.repeticion.tMs!!
            assertEquals(10_000.0, t.toDouble(), 100.0)
            val posicion = vm.uiState.value.posicion!!
            assertEquals(t, posicion.tMs)
            // A velocidad constante, la distancia es velocidad × tiempo
            assertEquals(50.0 / 3.6 * t / 1000.0, posicion.distanciaM, 30.0)
            assertEquals(posicion.distanciaM, vm.uiState.value.cursorM!!, 30.0)
            // La posición queda sobre el tramo: misma longitud, norte
            assertEquals(
                39.80 + posicion.distanciaM / FixtureAnalisis.tramo.longitudM * 0.04,
                posicion.punto.lat,
                0.001,
            )
            vm.onVelocidad(VelocidadRepeticion.X16)
            advanceTimeBy(lenta.totalMs)
            assertFalse(vm.uiState.value.repeticion.reproduciendo)
            assertEquals(lenta.totalMs, vm.uiState.value.repeticion.tMs)
        }

    @Test
    fun `la velocidad x4 avanza cuatro veces mas deprisa`() =
        runTest {
            val vm = crear(pasadaId = 1)
            vm.onVelocidad(VelocidadRepeticion.X4)
            vm.onAlternarRepeticion()
            advanceTimeBy(5_050L)
            assertEquals(
                20_000.0,
                vm.uiState.value.repeticion.tMs!!
                    .toDouble(),
                400.0,
            )
        }

    @Test
    fun `pausar detiene el marcador donde esta`() =
        runTest {
            val vm = crear(pasadaId = 1)
            vm.onAlternarRepeticion()
            advanceTimeBy(2_050L)
            vm.onAlternarRepeticion()
            val t = vm.uiState.value.repeticion.tMs
            advanceTimeBy(5_000L)
            assertEquals(t, vm.uiState.value.repeticion.tMs)
            assertFalse(vm.uiState.value.repeticion.reproduciendo)
        }

    @Test
    fun `tocar un grafico pausa y lleva el cursor y el marcador a esa distancia`() =
        runTest {
            val vm = crear(pasadaId = 1)
            vm.onAlternarRepeticion()
            vm.onCursor(2_000.0)
            val estado = vm.uiState.value
            assertFalse(estado.repeticion.reproduciendo)
            assertEquals(2_000.0, estado.cursorM!!, 5.0)
            // A 50 km/h, 2 km son unos 144 s
            assertEquals(144_000.0, estado.repeticion.tMs!!.toDouble(), 1_500.0)
            assertEquals(2_000.0, estado.posicion!!.distanciaM, 40.0)
        }

    @Test
    fun `el cursor se acota al tramo y la posicion de la barra a la duracion`() =
        runTest {
            val vm = crear(pasadaId = 1)
            vm.onCursor(1_000_000.0)
            assertEquals(lenta.totalMs, vm.uiState.value.repeticion.tMs)
            vm.onPosicion(-5L)
            assertEquals(0L, vm.uiState.value.repeticion.tMs)
            vm.onPosicion(1_000_000_000L)
            assertEquals(lenta.totalMs, vm.uiState.value.repeticion.tMs)
        }

    @Test
    fun `cambiar la comparada no mueve la repeticion`() =
        runTest {
            val vm = crear(pasadaId = 1)
            vm.onPosicion(30_000L)
            vm.onComparar(3)
            assertEquals(30_000L, vm.uiState.value.repeticion.tMs)
        }
}
