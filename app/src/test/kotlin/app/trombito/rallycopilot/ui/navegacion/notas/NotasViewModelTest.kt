package app.trombito.rallycopilot.ui.navegacion.notas

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.domain.notas.OrigenNotas
import app.trombito.rallycopilot.domain.notas.ProveedorNotas
import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.notas.TipoAviso
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioAjustesConduccion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FalsoProveedorNotas(
    inicial: NotasEnCurso? = null,
) : ProveedorNotas {
    val mutable = MutableStateFlow(inicial)
    override val notas: StateFlow<NotasEnCurso?> = mutable
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotasViewModelTest {
    private val proveedor = FalsoProveedorNotas()
    private val ajustes = FalsoRepositorioAjustesConduccion()

    private val hoja =
        HojaNotas(
            listOf(
                deNota(nota(3, posicionM = 200.0)),
                deNota(nota(5, posicionM = 300.0)),
                deAviso(TipoAviso.PUENTE),
                deNota(nota(1, posicionM = 900.0)),
            ),
        )

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun TestScope.crear(): NotasViewModel {
        val vm = NotasViewModel(proveedor, ajustes)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private fun en(
        posicionM: Double,
        vMs: Double? = 20.0,
    ) = NotasEnCurso(hoja, posicionM, vMs, OrigenNotas.GUIADO)

    @Test
    fun `sin notas en curso la franja esta vacia`() =
        runTest {
            val vm = crear()
            assertTrue(
                vm.uiState.value.visibles
                    .isEmpty(),
            )
        }

    @Test
    fun `elige las tres proximas por delante de la posicion`() =
        runTest {
            val vm = crear()
            proveedor.mutable.value = en(100.0)
            val elementos =
                vm.uiState.value.visibles
                    .map { it.elemento }
            assertEquals(hoja.elementos.take(3), elementos)
            // La nota ya pasada sale al avanzar.
            proveedor.mutable.value = en(250.0)
            assertEquals(
                hoja.elementos.drop(1),
                vm.uiState.value.visibles
                    .map { it.elemento },
            )
        }

    @Test
    fun `la activa depende de la velocidad y de la anticipacion`() =
        runTest {
            val vm = crear()
            // A 20 m/s y NORMAL (5 s + 1 s de voz) se avisa a 120 m: la nota a 100 m ya toca.
            proveedor.mutable.value = en(100.0)
            assertTrue(
                vm.uiState.value.visibles
                    .first()
                    .activa,
            )
            // Muy tarde (2,5 s + 1 s) son 70 m: aún no.
            ajustes.mutable.value = AjustesConduccion(anticipacion = NivelAnticipacion.MUY_TARDE)
            assertFalse(
                vm.uiState.value.visibles
                    .first()
                    .activa,
            )
            ajustes.mutable.value = AjustesConduccion(anticipacion = NivelAnticipacion.MUY_PRONTO)
            assertTrue(
                vm.uiState.value.visibles
                    .first()
                    .activa,
            )
        }

    @Test
    fun `sin velocidad usa la distancia minima de anticipacion`() =
        runTest {
            val vm = crear()
            proveedor.mutable.value = en(100.0, vMs = null)
            // Nota a 100 m con el mínimo de 40 m: no toca.
            assertFalse(
                vm.uiState.value.visibles
                    .first()
                    .activa,
            )
            proveedor.mutable.value = en(170.0, vMs = null)
            assertTrue(
                vm.uiState.value.visibles
                    .first()
                    .activa,
            )
        }

    @Test
    fun `las notas simplificadas de los ajustes llegan al estado`() =
        runTest {
            val vm = crear()
            proveedor.mutable.value = en(100.0)
            assertFalse(vm.uiState.value.simplificadas)
            ajustes.mutable.value = AjustesConduccion(notasSimplificadas = true)
            assertTrue(vm.uiState.value.simplificadas)
        }

    @Test
    fun `al acabar la hoja la franja se vacia`() =
        runTest {
            val vm = crear()
            proveedor.mutable.value = en(5_000.0)
            assertTrue(
                vm.uiState.value.visibles
                    .isEmpty(),
            )
            proveedor.mutable.value = null
            assertTrue(
                vm.uiState.value.visibles
                    .isEmpty(),
            )
        }

    private val hojaConAvisos =
        HojaNotas(
            listOf(
                deAviso(TipoAviso.CRUCE),
                deAviso(TipoAviso.ROTONDA),
                deNota(nota(3, posicionM = 320.0), TipoAviso.CRUCE, TipoAviso.ROTONDA),
                ElementoHoja.DeAviso(Aviso(400.0, TipoAviso.PUENTE)),
            ),
        )

    private fun tiposVisibles(vm: NotasViewModel) =
        vm.uiState.value.visibles
            .map { it.elemento }
            .filterIsInstance<ElementoHoja.DeAviso>()
            .map { it.aviso.tipo }

    private fun frasePrimeraNota(vm: NotasViewModel) =
        vm.uiState.value.visibles
            .map { it.elemento }
            .filterIsInstance<ElementoHoja.DeNota>()
            .first()
            .frase

    @Test
    fun `con todo activo la franja ensena cruces y rotondas como siempre`() =
        runTest {
            val vm = crear()
            proveedor.mutable.value = NotasEnCurso(hojaConAvisos, 100.0, 20.0, OrigenNotas.GUIADO)
            assertEquals(listOf(TipoAviso.CRUCE, TipoAviso.ROTONDA), tiposVisibles(vm))
            assertEquals("cruce, rotonda, derecha tres", frasePrimeraNota(vm))
        }

    @Test
    fun `sin cruces la franja no ensena el aviso de cruce, suelto ni unido, y deja el resto`() =
        runTest {
            ajustes.mutable.value = AjustesConduccion(avisarCruces = false)
            val vm = crear()
            proveedor.mutable.value = NotasEnCurso(hojaConAvisos, 100.0, 20.0, OrigenNotas.GUIADO)
            assertEquals(listOf(TipoAviso.ROTONDA, TipoAviso.PUENTE), tiposVisibles(vm))
            assertEquals("rotonda, derecha tres", frasePrimeraNota(vm))
        }

    @Test
    fun `sin rotondas la franja no ensena el aviso de rotonda, suelto ni unido, y deja el resto`() =
        runTest {
            ajustes.mutable.value = AjustesConduccion(avisarRotondas = false)
            val vm = crear()
            proveedor.mutable.value = NotasEnCurso(hojaConAvisos, 100.0, 20.0, OrigenNotas.GUIADO)
            assertEquals(listOf(TipoAviso.CRUCE, TipoAviso.PUENTE), tiposVisibles(vm))
            assertEquals("cruce, derecha tres", frasePrimeraNota(vm))
        }

    @Test
    fun `sin salidas de autopista la franja no cambia`() =
        runTest {
            ajustes.mutable.value = AjustesConduccion(avisarSalidasAutopista = false)
            val vm = crear()
            proveedor.mutable.value = NotasEnCurso(hojaConAvisos, 100.0, 20.0, OrigenNotas.GUIADO)
            assertEquals(listOf(TipoAviso.CRUCE, TipoAviso.ROTONDA), tiposVisibles(vm))
        }

    @Test
    fun `cambiar un interruptor cambia la franja al momento`() =
        runTest {
            val vm = crear()
            proveedor.mutable.value = NotasEnCurso(hojaConAvisos, 100.0, 20.0, OrigenNotas.GUIADO)
            assertEquals(listOf(TipoAviso.CRUCE, TipoAviso.ROTONDA), tiposVisibles(vm))
            ajustes.mutable.value = AjustesConduccion(avisarCruces = false, avisarRotondas = false)
            assertEquals(listOf(TipoAviso.PUENTE), tiposVisibles(vm))
            ajustes.mutable.value = AjustesConduccion()
            assertEquals(listOf(TipoAviso.CRUCE, TipoAviso.ROTONDA), tiposVisibles(vm))
        }
}
