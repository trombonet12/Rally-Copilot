package app.trombito.rallycopilot.ui.mistramos

import androidx.lifecycle.SavedStateHandle
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.tramos.ExportaTramo
import app.trombito.rallycopilot.domain.tramos.SubtramoGuardado
import app.trombito.rallycopilot.domain.tramos.TramoExportado
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioDatosMapa
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import app.trombito.rallycopilot.ui.comun.detalladaFalsa
import app.trombito.rallycopilot.ui.comun.resumenFalso
import app.trombito.rallycopilot.ui.comun.tramoFalso
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DetalleTramoViewModelTest {
    private val resumen = resumenFalso("t1", "Ma-2141 · Escorca", pasadas = 3, mejorMs = 100_000L)
    private val repositorio =
        FalsoRepositorioTramos(
            resumenes = listOf(resumen, resumenFalso("t2")),
            pasadas =
                mapOf(
                    "t1" to
                        listOf(
                            detalladaFalsa(3, "t1", totalMs = 105_000L, inicioMs = 3_000L),
                            detalladaFalsa(2, "t1", totalMs = 100_000L, inicioMs = 2_000L, esMejor = true),
                            detalladaFalsa(1, "t1", totalMs = 112_500L, inicioMs = 1_000L),
                        ),
                ),
            trazas = mapOf("t1" to tramoFalso("t1")),
            subtramos =
                listOf(
                    SubtramoGuardado("t1", 1_200.0, 3_600.0, 2_400.0, 5_000L, 112_300L, 115_400L),
                    SubtramoGuardado("t1", 0.0, 1_500.0, 1_500.0, 9_000L, 70_000L, null),
                    SubtramoGuardado("t2", 0.0, 800.0, 800.0, 7_000L, 40_000L, 41_000L),
                ),
        )

    private val datosMapa = FalsoRepositorioDatosMapa(estilosDisponibles = EstilosMapa("{}", null))

    private val pedidos = mutableListOf<String>()
    private var exportado: TramoExportado? = TramoExportado("ma-2141.gpx", "<gpx/>")
    private val exportador =
        ExportaTramo {
            pedidos += it
            exportado
        }

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun TestScope.crear(id: String = "t1"): DetalleTramoViewModel {
        val vm = DetalleTramoViewModel(SavedStateHandle(mapOf("tramoId" to id)), repositorio, datosMapa, exportador)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `carga el tramo, su traza y las pasadas con la diferencia contra la mejor`() =
        runTest {
            val vm = crear()
            val estado = vm.uiState.value
            assertFalse(estado.cargando)
            assertEquals("Ma-2141 · Escorca", estado.tramo?.nombre)
            assertEquals(3, estado.traza.size)
            assertEquals(listOf(3L, 2L, 1L), estado.pasadas.map { it.id })
            assertEquals(listOf(5_000L, 0L, 12_500L), estado.pasadas.map { it.deltaMejorMs })
            assertEquals(listOf(false, true, false), estado.pasadas.map { it.esMejor })
            assertEquals(105_000L, estado.pasadas.first().totalMs)
            assertEquals(3_000L, estado.pasadas.first().inicioMs)
        }

    @Test
    fun `los subtramos del tramo salen el mas reciente primero con su delta contra el mejor de entonces`() =
        runTest {
            val filas = crear().uiState.value.subtramos
            assertEquals(listOf(9_000L, 5_000L), filas.map { it.inicioMs })
            assertEquals(listOf(null, -3_100L), filas.map { it.deltaMs })
            assertEquals(listOf(0.0, 1_200.0), filas.map { it.desdeM })
            assertEquals(112_300L, filas.last().tiempoMs)
            val deT2 = crear("t2").uiState.value.subtramos
            val deNadie = crear("fantasma").uiState.value.subtramos
            assertEquals(1, deT2.size)
            assertEquals(0, deNadie.size)
        }

    @Test
    fun `el detalle trae el estilo del mapa para dibujar la traza y sin datos de mapa no lo trae`() =
        runTest {
            assertEquals("{}", crear().uiState.value.estiloMapaJson)
            datosMapa.estilosDisponibles = null
            assertNull(crear().uiState.value.estiloMapaJson)
        }

    @Test
    fun `un tramo que no existe queda como no encontrado`() =
        runTest {
            val vm = crear("fantasma")
            assertFalse(vm.uiState.value.cargando)
            assertNull(vm.uiState.value.tramo)
            assertTrue(
                vm.uiState.value.pasadas
                    .isEmpty(),
            )
        }

    @Test
    fun `un tramo sin pasadas no inventa filas`() =
        runTest {
            val vm = crear("t2")
            assertEquals(
                "t2",
                vm.uiState.value.tramo
                    ?.id,
            )
            assertTrue(
                vm.uiState.value.pasadas
                    .isEmpty(),
            )
        }

    @Test
    fun `compartir exporta el tramo y entrega el archivo`() =
        runTest {
            val vm = crear()
            val recibidos = mutableListOf<TramoExportado>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.archivosParaCompartir.collect {
                    recibidos +=
                        it
                }
            }

            vm.onCompartir()

            assertEquals(listOf("t1"), pedidos)
            assertEquals(listOf("ma-2141.gpx"), recibidos.map { it.nombreArchivo })
            assertEquals(EstadoCompartir.LIBRE, vm.uiState.value.compartir)
        }

    @Test
    fun `si no se puede exportar avisa y el aviso se puede dar por visto`() =
        runTest {
            exportado = null
            val vm = crear()
            vm.onCompartir()
            assertEquals(EstadoCompartir.FALLO, vm.uiState.value.compartir)
            vm.onCompartirVisto()
            assertEquals(EstadoCompartir.LIBRE, vm.uiState.value.compartir)
            vm.onFalloAlCompartir()
            assertEquals(EstadoCompartir.FALLO, vm.uiState.value.compartir)
        }

    @Test
    fun `el favorito alterna y se ve en el detalle`() =
        runTest {
            val vm = crear()
            vm.onFavorito()
            assertEquals(listOf("t1" to true), repositorio.favoritos)
            assertTrue(
                vm.uiState.value.tramo
                    ?.favorito == true,
            )
            vm.onFavorito()
            assertEquals("t1" to false, repositorio.favoritos.last())
        }

    @Test
    fun `renombrar guarda el nombre y cierra el dialogo`() =
        runTest {
            val vm = crear()
            vm.onPedirRenombrar()
            assertTrue(vm.uiState.value.renombrando)
            vm.onRenombrar(" Mi Escorca ")
            assertFalse(vm.uiState.value.renombrando)
            assertEquals(listOf("t1" to "Mi Escorca"), repositorio.renombrados)
            assertEquals(
                "Mi Escorca",
                vm.uiState.value.tramo
                    ?.nombre,
            )
        }

    @Test
    fun `renombrar vacio vuelve al nombre automatico y cancelar no guarda`() =
        runTest {
            val vm = crear()
            vm.onPedirRenombrar()
            vm.onCancelarRenombrar()
            assertFalse(vm.uiState.value.renombrando)
            assertTrue(repositorio.renombrados.isEmpty())
            vm.onPedirRenombrar()
            vm.onRenombrar("")
            assertEquals(listOf("t1" to null), repositorio.renombrados)
        }
}
