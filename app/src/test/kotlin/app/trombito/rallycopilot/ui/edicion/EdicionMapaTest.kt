package app.trombito.rallycopilot.ui.edicion

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.edicion.EdicionNotasEnVivo
import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.domain.notas.OrigenNotas
import app.trombito.rallycopilot.domain.notas.ProveedorNotas
import app.trombito.rallycopilot.motor.edicion.AccionEdicion
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioAjustesConduccion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EdicionMapaTest {
    private val curva = Nota(400.0, 440.0, Direccion.IZQUIERDA, 3, cuidado = true)
    private val lejana = Nota(9_000.0, 9_040.0, Direccion.DERECHA, 4)

    private fun notas(vMs: Double?) =
        NotasEnCurso(
            HojaNotas(listOf(ElementoHoja.DeNota(curva), ElementoHoja.DeNota(lejana))),
            posicionM = 380.0,
            vMs = vMs,
            origen = OrigenNotas.GUIADO,
            puntoEn = { PuntoGeo(39.6, 2.9 + it / 100_000.0) },
        )

    private val proveedor =
        object : ProveedorNotas {
            override val notas = MutableStateFlow<NotasEnCurso?>(notas(0.0))
        }
    private val editadas = mutableListOf<Pair<Double, AccionEdicion>>()
    private val edicion =
        EdicionNotasEnVivo { posicion, accion ->
            editadas += posicion to accion
            true
        }

    @Before
    fun preparar() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun limpiar() = Dispatchers.resetMain()

    private fun viewModel() =
        EdicionMapaViewModel(proveedor, FalsoRepositorioAjustesConduccion(AjustesConduccion()), edicion).also {
            CoroutineScope(UnconfinedTestDispatcher()).launch { it.uiState.collect {} }
        }

    @Test
    fun `parado se pintan las notas cercanas y conduciendo ninguna`() {
        val parado = marcadoresDeNotas(notas(0.5))
        assertEquals(listOf(400.0), parado.map { it.posicionM })
        assertTrue(parado.single().cuidado)

        assertTrue(marcadoresDeNotas(notas(10.0)).isEmpty())
        assertTrue(marcadoresDeNotas(notas(null)).isEmpty())
        assertTrue(marcadoresDeNotas(null).isEmpty())
    }

    @Test
    fun `solo parado se puede tocar una nota y el editor la sustituye o la quita`() {
        val vm = viewModel()
        proveedor.notas.value = notas(10.0)
        vm.onTocarNota(400.0)
        assertNull(vm.uiState.value.editor)

        proveedor.notas.value = notas(0.0)
        vm.onTocarNota(400.0)
        assertEquals(
            curva,
            vm.uiState.value.editor
                ?.nota,
        )
        val nueva = Nota(0.0, 0.0, Direccion.DERECHA, 2)
        vm.onAplicar(nueva)
        assertNull(vm.uiState.value.editor)

        vm.onTocarNota(400.0)
        vm.onQuitar()

        assertEquals(listOf(400.0 to AccionEdicion.Sustituir(nueva), 400.0 to AccionEdicion.Quitar), editadas)
    }

    @Test
    fun `el borrador construye la nota y los modificadores opuestos se excluyen`() {
        val borrador =
            BorradorNota(Direccion.IZQUIERDA, null)
                .conModificador(Modificador.LARGA)
                .conModificador(Modificador.MUY_LARGA)
                .conModificador(Modificador.CIERRA)
                .copy(cuidado = true)

        val nota = borrador.aNota()
        assertEquals(setOf(Modificador.MUY_LARGA, Modificador.CIERRA), nota.modificadores)
        assertNull(nota.grado)
        assertEquals(Direccion.IZQUIERDA, nota.direccion)
        assertTrue(nota.cuidado)
        assertEquals(borrador, BorradorNota.de(nota))
        assertEquals(BorradorNota(), BorradorNota.de(null))
        assertEquals(
            emptySet<Modificador>(),
            borrador.conModificador(Modificador.MUY_LARGA).conModificador(Modificador.CIERRA).modificadores,
        )
    }
}
