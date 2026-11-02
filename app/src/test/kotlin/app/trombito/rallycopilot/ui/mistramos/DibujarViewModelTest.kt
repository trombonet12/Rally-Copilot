package app.trombito.rallycopilot.ui.mistramos

import app.trombito.rallycopilot.data.tramos.GuardadorTramos
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.ruta.Ruta
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class DibujarViewModelTest {
    /** Enrutador falso: la «carretera» es la poligonal que une los puntos con uno intermedio extra por tramo. */
    private class EnrutadorFalso : Enrutador {
        val llamadas = mutableListOf<List<PuntoGeo>>()
        var respuesta: ((List<PuntoGeo>) -> ResultadoRuta)? = null

        override suspend fun ruta(
            origen: PuntoGeo,
            destino: PuntoGeo,
            perfil: PerfilRuta,
        ): ResultadoRuta = rutaConParadas(origen, emptyList(), destino, perfil)

        override suspend fun rutaConParadas(
            origen: PuntoGeo,
            paradas: List<PuntoGeo>,
            destino: PuntoGeo,
            perfil: PerfilRuta,
        ): ResultadoRuta {
            val puntos = listOf(origen) + paradas + destino
            llamadas += puntos
            return respuesta?.invoke(puntos)
                ?: ResultadoRuta.Ok(Ruta(puntos, emptyList(), puntos.longitudMetros(), 60.0), 1L)
        }
    }

    private val enrutador = EnrutadorFalso()
    private val repositorio = FalsoRepositorioTramos()
    private var automatico: String? = "Ma-10 · Escorca"

    private fun vm() = DibujarViewModel(enrutador, GuardadorTramos(repositorio) { automatico })

    /** Puntos a lo largo de un meridiano, separados 0,01 grados (≈ 1,1 km). */
    private fun punto(i: Int) = PuntoGeo(39.70 + i * 0.01, 2.80)

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    @Test
    fun `con un solo punto no se calcula nada`() =
        runTest {
            val vm = vm()
            vm.onToque(punto(0))
            assertEquals(listOf(punto(0)), vm.uiState.value.puntos)
            assertTrue(enrutador.llamadas.isEmpty())
            assertTrue(
                vm.uiState.value.ruta
                    .isEmpty(),
            )
            assertFalse(vm.uiState.value.puedeGuardar)
        }

    @Test
    fun `el segundo punto calcula la ruta y los intermedios son paradas`() =
        runTest {
            val vm = vm()
            vm.onToque(punto(0))
            vm.onToque(punto(1))
            vm.onToque(punto(2))
            assertEquals(listOf(punto(0), punto(1)), enrutador.llamadas.first())
            assertEquals(listOf(punto(0), punto(1), punto(2)), enrutador.llamadas.last())
            val estado = vm.uiState.value
            assertEquals(3, estado.ruta.size)
            assertTrue(estado.longitudM > 2_000.0)
            assertFalse(estado.calculando)
            assertTrue(estado.puedeGuardar)
        }

    @Test
    fun `deshacer quita el ultimo punto y recalcula, y borrar todo vacia`() =
        runTest {
            val vm = vm()
            repeat(3) { vm.onToque(punto(it)) }
            vm.onDeshacer()
            assertEquals(listOf(punto(0), punto(1)), vm.uiState.value.puntos)
            assertEquals(listOf(punto(0), punto(1)), enrutador.llamadas.last())
            vm.onDeshacer()
            assertEquals(1, vm.uiState.value.puntos.size)
            assertTrue(
                "Con un punto ya no hay ruta",
                vm.uiState.value.ruta
                    .isEmpty(),
            )
            vm.onBorrarTodo()
            assertTrue(
                vm.uiState.value.puntos
                    .isEmpty(),
            )
            vm.onDeshacer()
            assertTrue(
                "Deshacer sin puntos no falla",
                vm.uiState.value.puntos
                    .isEmpty(),
            )
        }

    @Test
    fun `sin datos de rutas o con error de calculo se avisa y no se puede guardar`() =
        runTest {
            val vm = vm()
            enrutador.respuesta = { ResultadoRuta.SinDatos }
            vm.onToque(punto(0))
            vm.onToque(punto(1))
            assertEquals(ErrorDibujo.SIN_DATOS, vm.uiState.value.error)
            assertFalse(vm.uiState.value.puedeGuardar)
            enrutador.respuesta = { ResultadoRuta.Error("sin camino") }
            vm.onToque(punto(2))
            assertEquals(ErrorDibujo.RUTA, vm.uiState.value.error)
            vm.onErrorVisto()
            assertNull(vm.uiState.value.error)
            assertEquals(3, vm.uiState.value.puntos.size)
        }

    @Test
    fun `guardar crea un tramo sin pasadas con la geometria calculada y avisa con su id`() =
        runTest {
            val vm = vm()
            repeat(4) { vm.onToque(punto(it)) }
            vm.onGuardar()
            val guardado = repositorio.sinPasadas.single()
            assertEquals(vm.uiState.value.guardado, guardado.tramo.id)
            assertNull(guardado.nombre)
            assertEquals("Ma-10 · Escorca", guardado.nombreAutomatico)
            assertTrue(guardado.tramo.longitudM > 3_000.0)
            assertFalse(vm.uiState.value.guardando)
            assertNotNull(vm.uiState.value.guardado)
        }

    @Test
    fun `una ruta de menos de un kilometro no se guarda`() =
        runTest {
            val vm = vm()
            vm.onToque(PuntoGeo(39.700, 2.80))
            vm.onToque(PuntoGeo(39.703, 2.80))
            vm.onGuardar()
            assertEquals(ErrorDibujo.CORTA, vm.uiState.value.error)
            assertTrue(repositorio.sinPasadas.isEmpty())
            assertNull(vm.uiState.value.guardado)
        }

    @Test
    fun `si la base falla al guardar avisa y se puede reintentar`() =
        runTest {
            val vm = vm()
            repeat(3) { vm.onToque(punto(it)) }
            repositorio.fallarAlGuardar = true
            vm.onGuardar()
            assertEquals(ErrorDibujo.NO_GUARDADA, vm.uiState.value.error)
            assertTrue(vm.uiState.value.puedeGuardar)
            repositorio.fallarAlGuardar = false
            vm.onGuardar()
            assertEquals(1, repositorio.sinPasadas.size)
        }
}
