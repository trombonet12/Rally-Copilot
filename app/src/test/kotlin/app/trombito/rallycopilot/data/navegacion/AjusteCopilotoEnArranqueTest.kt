package app.trombito.rallycopilot.data.navegacion

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioAjustesConduccion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AjusteCopilotoEnArranqueTest {
    @Test
    fun `vale false hasta que se empieza a seguir y luego refleja el ajuste`() {
        val ajustes = FalsoRepositorioAjustesConduccion(AjustesConduccion(activarConCopiloto = true))
        val ajuste = AjusteCopilotoEnArranque(ajustes)
        assertFalse("sin seguir, el valor inicial es false (sin microfono)", ajuste.activo)

        val alcance = TestScope(UnconfinedTestDispatcher())
        ajuste.seguir(alcance)
        assertTrue(ajuste.activo)

        ajustes.mutable.value = AjustesConduccion(activarConCopiloto = false)
        assertFalse(ajuste.activo)
        ajustes.mutable.value = AjustesConduccion(activarConCopiloto = true)
        assertTrue(ajuste.activo)
    }

    @Test
    fun `al cancelar el alcance deja de seguir`() {
        val ajustes = FalsoRepositorioAjustesConduccion(AjustesConduccion(activarConCopiloto = false))
        val ajuste = AjusteCopilotoEnArranque(ajustes)
        val alcance = TestScope(UnconfinedTestDispatcher())
        ajuste.seguir(alcance).cancel()
        ajustes.mutable.value = AjustesConduccion(activarConCopiloto = true)
        assertFalse(ajuste.activo)
    }
}
