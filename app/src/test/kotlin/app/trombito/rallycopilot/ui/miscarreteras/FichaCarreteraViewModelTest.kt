package app.trombito.rallycopilot.ui.miscarreteras

import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import app.trombito.rallycopilot.data.carreteras.CalculadorCarreteras
import app.trombito.rallycopilot.data.carreteras.RecorridosFalsos
import app.trombito.rallycopilot.data.carreteras.lineaEste
import app.trombito.rallycopilot.data.carreteras.viaFalsa
import app.trombito.rallycopilot.data.carreteras.viasFalsas
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.tramos.DatosVia
import app.trombito.rallycopilot.motor.vias.SentidoVia
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioDatosMapa
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import app.trombito.rallycopilot.ui.comun.resumenFalso
import app.trombito.rallycopilot.ui.mapa.CALOR_GEOJSON_VACIO
import app.trombito.rallycopilot.ui.mapa.ModoCalor
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FichaCarreteraViewModelTest {
    private val ma10 = viaFalsa(1, "Ma-10", 0, 1_000)
    private val ma11 = viaFalsa(2, "Ma-11", 1_000, 2_000)

    @Suppress("LongParameterList") // cada prueba fija solo lo que le importa; el resto va por defecto
    private fun tramoVia(
        id: String,
        ref: String = "MA10",
        desde: Double? = 0.0,
        hasta: Double? = 5.0,
        sentido: SentidoVia = SentidoVia.CRECIENTE,
        destino: String? = null,
    ) = resumenFalso(id, via = DatosVia(ref, desde, hasta, sentido, destino, null))

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun TestScope.crear(
        ref: String = "MA10",
        repo: RecorridosFalsos = RecorridosFalsos(mapOf(1L to lineaEste(0, 1_900), 2L to lineaEste(0, 900))),
        tramos: FalsoRepositorioTramos = FalsoRepositorioTramos(),
        estilos: EstilosMapa? = EstilosMapa(vectorial = "{\"version\":8}", satelite = null),
    ): FichaCarreteraViewModel {
        val vm =
            FichaCarreteraViewModel(
                SavedStateHandle(mapOf("ref" to ref)),
                CalculadorCarreteras(repo, viasFalsas(listOf(ma10, ma11)), UnconfinedTestDispatcher(testScheduler)),
                tramos,
                FalsoRepositorioDatosMapa(estilosDisponibles = estilos),
                UnconfinedTestDispatcher(testScheduler),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `empieza cargando`() =
        runTest {
            val vm =
                FichaCarreteraViewModel(
                    SavedStateHandle(mapOf("ref" to "MA10")),
                    CalculadorCarreteras(
                        RecorridosFalsos(emptyMap()),
                        viasFalsas(null),
                        UnconfinedTestDispatcher(testScheduler),
                    ),
                    FalsoRepositorioTramos(),
                    FalsoRepositorioDatosMapa(),
                    UnconfinedTestDispatcher(testScheduler),
                )
            assertTrue(vm.uiState.value.cargando)
        }

    @Test
    fun `da la ficha de la carretera de la ruta y solo sus vias en el mapa`() =
        runTest {
            val estado = crear().uiState.value
            assertFalse(estado.cargando)
            val ficha = checkNotNull(estado.ficha)
            assertEquals("MA10", ficha.id)
            assertEquals(2, ficha.viajes)
            // Solo la vía de Ma-10: una línea en el trazo y sus esquinas.
            assertEquals(1, estado.trazos.size)
            assertEquals(2, estado.trazos.single().pasadas)
            assertEquals(2, estado.esquinas.size)
            assertNotEquals(CALOR_GEOJSON_VACIO, estado.geoJson)
            assertEquals("{\"version\":8}", estado.estiloMapaJson)
        }

    @Test
    fun `una carretera que no esta en los viajes es no encontrada`() =
        runTest {
            val estado = crear(ref = "MA99").uiState.value
            assertTrue(estado.noEncontrada)
            assertNull(estado.ficha)
        }

    @Test
    fun `solo entran los tramos de via de esta carretera, agrupados por sentido con su destino`() =
        runTest {
            val tramos =
                FalsoRepositorioTramos(
                    listOf(
                        tramoVia("a", desde = 0.0, hasta = 5.0, sentido = SentidoVia.CRECIENTE, destino = "Sóller"),
                        tramoVia("b", desde = 5.0, hasta = 12.0, sentido = SentidoVia.CRECIENTE, destino = "Sóller"),
                        tramoVia("c", desde = 0.0, hasta = 12.0, sentido = SentidoVia.DECRECIENTE, destino = "Palma"),
                        tramoVia("otra", ref = "MA11"),
                        resumenFalso("viaje"),
                    ),
                )
            val estado = crear(tramos = tramos).uiState.value
            assertEquals(listOf("a", "b", "c"), estado.tramos.map { it.id })
            assertEquals(2, estado.grupos.size)
            assertEquals("Sóller", estado.destinos[SentidoVia.CRECIENTE])
            assertEquals("Palma", estado.destinos[SentidoVia.DECRECIENTE])
            val regla = checkNotNull(estado.regla)
            assertEquals(0.0, regla.minKm, 0.0)
            assertEquals(12.0, regla.maxKm, 0.0)
            assertEquals(3, regla.franjas.size)
        }

    @Test
    fun `tocar una franja la elige y volver a tocarla la suelta`() =
        runTest {
            val tramos = FalsoRepositorioTramos(listOf(tramoVia("a"), tramoVia("b", desde = 5.0, hasta = 9.0)))
            val vm = crear(tramos = tramos)
            assertNull(vm.uiState.value.seleccionado)
            vm.onTramo("b")
            assertEquals("b", vm.uiState.value.seleccionado)
            vm.onTramo("a")
            assertEquals("a", vm.uiState.value.seleccionado)
            vm.onTramo("a")
            assertNull(vm.uiState.value.seleccionado)
        }

    @Test
    fun `una seleccion de un tramo que ya no existe se descarta`() =
        runTest {
            val tramos = FalsoRepositorioTramos(listOf(tramoVia("a")))
            val vm = crear(tramos = tramos)
            vm.onTramo("a")
            assertEquals("a", vm.uiState.value.seleccionado)
            tramos.resumenesMutable.value = emptyList()
            assertNull(vm.uiState.value.seleccionado)
        }

    @Test
    fun `cambiar el modo del mapa rehace el geojson pero no al elegir un tramo`() =
        runTest {
            val tramos = FalsoRepositorioTramos(listOf(tramoVia("a")))
            val vm = crear(tramos = tramos)
            val uso = vm.uiState.value.geoJson
            vm.onTramo("a")
            assertEquals(uso, vm.uiState.value.geoJson)
            vm.onModo(ModoCalor.VELOCIDAD)
            assertEquals(ModoCalor.VELOCIDAD, vm.uiState.value.modoCalor)
            assertNotEquals(uso, vm.uiState.value.geoJson)
        }

    @Test
    fun `un fallo de la base pasa a error`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            repo.fallo = SQLiteException("disco")
            val estado = crear(repo = repo).uiState.value
            assertTrue(estado.error)
            assertFalse(estado.cargando)
            assertFalse(estado.noEncontrada)
        }
}
