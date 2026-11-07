package app.trombito.rallycopilot.ui.comun

import androidx.compose.ui.graphics.Color
import app.trombito.rallycopilot.ui.theme.CarbonOpaco
import app.trombito.rallycopilot.ui.theme.CarbonTranslucido
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los paneles de banner y de notas van sobre carbón opaco (sin ver el mapa detrás); el resto del mapa, translúcido. */
class FondoTarjetaWrcTest {
    private val superficie = Color(0xFF223344)

    @Test
    fun `opaca en el mapa es carbon sin transparencia`() {
        val fondo = fondoDeTarjeta(enMapa = true, opaca = true, superficie = superficie)
        assertEquals(CarbonOpaco, fondo)
        assertEquals(1f, fondo.alpha, 0f)
    }

    @Test
    fun `por defecto en el mapa sigue translucida`() {
        val fondo = fondoDeTarjeta(enMapa = true, opaca = false, superficie = superficie)
        assertEquals(CarbonTranslucido, fondo)
        assertTrue(fondo.alpha < 1f)
    }

    @Test
    fun `fuera del mapa usa la superficie del tema`() {
        assertEquals(superficie, fondoDeTarjeta(enMapa = false, opaca = true, superficie = superficie))
    }
}
