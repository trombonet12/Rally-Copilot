package app.trombito.rallycopilot.ui.miscarreteras

import android.database.sqlite.SQLiteException
import app.trombito.rallycopilot.data.carreteras.CalculadorCarreteras
import app.trombito.rallycopilot.data.carreteras.RecorridosFalsos
import app.trombito.rallycopilot.data.carreteras.lineaEste
import app.trombito.rallycopilot.data.carreteras.viaFalsa
import app.trombito.rallycopilot.data.carreteras.viasFalsas
import app.trombito.rallycopilot.data.recorridos.Recorrido
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.motor.analisis.OrdenCarreteras
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioDatosMapa
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
class MisCarreterasViewModelTest {
    private val ma10 = viaFalsa(1, "Ma-10", 0, 1_000)
    private val ma11 = viaFalsa(2, "Ma-11", 1_000, 2_000)

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun TestScope.calculador(
        repo: RecorridosFalsos,
        conVias: Boolean = true,
    ) = CalculadorCarreteras(
        repo,
        viasFalsas(if (conVias) listOf(ma10, ma11) else null),
        UnconfinedTestDispatcher(testScheduler),
    )

    private fun TestScope.crear(
        repo: RecorridosFalsos,
        conVias: Boolean = true,
        estilos: EstilosMapa? = EstilosMapa(vectorial = "{\"version\":8}", satelite = null),
    ): MisCarreterasViewModel {
        val vm =
            MisCarreterasViewModel(
                calculador(repo, conVias),
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
                MisCarreterasViewModel(
                    calculador(RecorridosFalsos(emptyMap()), conVias = false),
                    FalsoRepositorioDatosMapa(),
                    UnconfinedTestDispatcher(testScheduler),
                )
            assertTrue(vm.uiState.value.cargando)
        }

    @Test
    fun `con recorridos da trazos coloreables, fichas, mapa y estilo`() =
        runTest {
            val vm = crear(RecorridosFalsos(mapOf(1L to lineaEste(0, 900), 2L to lineaEste(0, 900))))
            val estado = vm.uiState.value
            assertFalse(estado.cargando)
            assertFalse(estado.vacio)
            assertEquals(2, estado.recorridos)
            assertEquals(2, estado.trazos.single().pasadas)
            assertEquals(36.0, estado.trazos.single().velMediaKmh!!, 0.5)
            assertTrue(estado.hayVias)
            val ficha = estado.fichas.single()
            assertEquals("MA10", ficha.id)
            assertEquals(2, ficha.viajes)
            assertEquals("{\"version\":8}", estado.estiloMapaJson)
            assertNotEquals(CALOR_GEOJSON_VACIO, estado.geoJson)
            assertEquals(2, estado.esquinas.size)
            assertTrue(estado.esquinas[0].lon < estado.esquinas[1].lon)
        }

    @Test
    fun `sin estilo instalado no hay estilo de mapa`() =
        runTest {
            val vm = crear(RecorridosFalsos(mapOf(1L to lineaEste(0, 900))), estilos = null)
            assertNull(vm.uiState.value.estiloMapaJson)
        }

    @Test
    fun `sin recorridos es el estado vacio`() =
        runTest {
            val vm = crear(RecorridosFalsos(emptyMap()))
            val estado = vm.uiState.value
            assertTrue(estado.vacio)
            assertTrue(estado.esquinas.isEmpty())
        }

    @Test
    fun `sin vias no hay trazos ni fichas pero se avisa`() =
        runTest {
            val vm = crear(RecorridosFalsos(mapOf(1L to lineaEste(0, 900))), conVias = false)
            val estado = vm.uiState.value
            assertTrue(estado.trazos.isEmpty())
            assertTrue(estado.fichas.isEmpty())
            assertFalse(estado.hayVias)
            assertEquals(1, estado.recorridos)
        }

    @Test
    fun `un fallo de la base pasa a error sin cerrar la pantalla`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            repo.fallo = SQLiteException("disco")
            val vm = crear(repo)
            assertTrue(vm.uiState.value.error)
            assertFalse(vm.uiState.value.cargando)
        }

    @Test
    fun `un recorrido nuevo actualiza el resumen`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            val vm = crear(repo)
            assertEquals(1, vm.uiState.value.recorridos)
            repo.trazas[2L] = lineaEste(0, 900)
            repo.lista.value = repo.lista.value + Recorrido(2L, 2L, 3L, 900.0, null)
            assertEquals(2, vm.uiState.value.recorridos)
        }

    @Test
    fun `el orden reordena las fichas sin rehacer el mapa`() =
        runTest {
            // Ma-10: 1 km en el viaje 1. Ma-11: 0,4 km en el viaje 1 y otros 0,4 km en el 2 (el más reciente).
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 1_400), 2L to lineaEste(1_100, 1_500)))
            val vm = crear(repo)
            val porUso = vm.uiState.value
            assertEquals(OrdenCarreteras.USO, porUso.orden)
            assertEquals(listOf("MA10", "MA11"), porUso.fichas.map { it.id })
            vm.onOrden(OrdenCarreteras.RECIENTE)
            val reciente = vm.uiState.value
            assertEquals(OrdenCarreteras.RECIENTE, reciente.orden)
            // El viaje 2 es el más reciente (empieza más tarde) y solo pasa por Ma-11.
            assertEquals("MA11", reciente.fichas.first().id)
            assertEquals(porUso.geoJson, reciente.geoJson)
        }

    @Test
    fun `cambiar el modo del mapa cambia el geojson y se conserva el orden`() =
        runTest {
            val vm = crear(RecorridosFalsos(mapOf(1L to lineaEste(0, 900))))
            val uso = vm.uiState.value.geoJson
            vm.onModo(ModoCalor.VELOCIDAD)
            val velocidad = vm.uiState.value
            assertEquals(ModoCalor.VELOCIDAD, velocidad.modoCalor)
            assertNotEquals(uso, velocidad.geoJson)
            vm.onModo(ModoCalor.USO)
            assertEquals(uso, vm.uiState.value.geoJson)
        }
}
