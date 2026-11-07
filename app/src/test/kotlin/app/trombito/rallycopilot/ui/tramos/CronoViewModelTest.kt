package app.trombito.rallycopilot.ui.tramos

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.tramos.ControlSalidaCrono
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import app.trombito.rallycopilot.domain.voz.PrioridadVoz
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.FalsoLocutor
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioAjustesConduccion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FalsoProveedorCrono : ProveedorCrono {
    val estado = MutableStateFlow<CronoEnCurso?>(null)
    override val crono: StateFlow<CronoEnCurso?> = estado
}

private class FalsoControlSalida : ControlSalidaCrono {
    var salidas = 0

    override fun darSalida() {
        salidas++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CronoViewModelTest {
    private val proveedor = FalsoProveedorCrono()
    private val control = FalsoControlSalida()
    private val locutor = FalsoLocutor()
    private val ajustes = FalsoRepositorioAjustesConduccion(AjustesConduccion(voz = VozCopiloto.DAVEFX))
    private lateinit var viewModel: CronoViewModel

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = CronoViewModel(proveedor, control, locutor, ajustes)
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    @Test
    fun `dar la salida llega al control y decir llega al locutor`() {
        viewModel.decir("cinco")
        viewModel.darSalida()
        assertEquals(listOf("cinco"), locutor.dichas)
        assertEquals(1, control.salidas)
    }

    @Test
    fun `los numeros de la cuenta atras van como urgentes`() {
        viewModel.decir("tres")
        viewModel.decir("ya")
        assertEquals(listOf(PrioridadVoz.URGENTE, PrioridadVoz.URGENTE), locutor.frases.map { it.prioridad })
    }

    @Test
    fun `sin voz elegida la cuenta atras es solo luces`() {
        ajustes.mutable.value = AjustesConduccion(voz = null)
        viewModel.decir("cinco")
        assertTrue(locutor.dichas.isEmpty())
    }

    @Test
    fun `en silencio la cuenta atras es solo luces`() {
        ajustes.mutable.value = AjustesConduccion(modoCopiloto = ModoCopiloto.SILENCIO, voz = VozCopiloto.DAVEFX)
        viewModel.decir("cinco")
        assertTrue(locutor.dichas.isEmpty())
    }

    @Test
    fun `sin conduccion no hay cronometro`() {
        assertNull(viewModel.crono.value)
    }

    @Test
    fun `publica el cronometro del proveedor y su fin`() {
        proveedor.estado.value = cronoConocido()
        assertEquals(cronoConocido(), viewModel.crono.value)
        proveedor.estado.value = null
        assertNull(viewModel.crono.value)
    }
}
