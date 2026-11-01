package app.trombito.rallycopilot.ui.tramos

import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FalsoProveedorCrono : ProveedorCrono {
    val estado = MutableStateFlow<CronoEnCurso?>(null)
    override val crono: StateFlow<CronoEnCurso?> = estado
}

class CronoViewModelTest {
    private val proveedor = FalsoProveedorCrono()
    private val viewModel = CronoViewModel(proveedor)

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
