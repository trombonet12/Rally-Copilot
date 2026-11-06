package app.trombito.rallycopilot.ui.tramos

import app.trombito.rallycopilot.domain.tramos.ControlSalidaCrono
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

private class FalsoLocutor : Locutor {
    override val hablando: StateFlow<Boolean> = MutableStateFlow(false)
    val dichas = mutableListOf<String>()

    override fun usarVoz(v: VozCopiloto) = Unit

    override fun preparar(textos: List<String>) = Unit

    override fun decir(texto: String) {
        dichas += texto
    }

    override fun duracionEstimadaS(texto: String) = 1.0

    override fun probar(v: VozCopiloto) = Unit

    override fun cerrar() = Unit
}

class CronoViewModelTest {
    private val proveedor = FalsoProveedorCrono()
    private val control = FalsoControlSalida()
    private val locutor = FalsoLocutor()
    private val viewModel = CronoViewModel(proveedor, control, locutor)

    @Test
    fun `dar la salida llega al control y decir llega al locutor`() {
        viewModel.decir("cinco")
        viewModel.darSalida()
        assertEquals(listOf("cinco"), locutor.dichas)
        assertEquals(1, control.salidas)
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
