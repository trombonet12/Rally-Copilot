package app.trombito.rallycopilot.ui.mapa

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecentrarSoloTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private var sigue by mutableStateOf(false)
    private var enMarcha by mutableStateOf(true)
    private var gestos by mutableStateOf<Int?>(1)
    private var recentrados = 0

    private fun poner() {
        banco.poner {
            RecentrarSolo(sigue, enMarcha, gestos) {
                recentrados++
                sigue = true
                gestos = null
            }
        }
    }

    @Test
    fun `en marcha y con el mapa movido recentra a los 10 s`() {
        poner()
        banco.esperar(9_500)
        assertEquals(0, recentrados)
        banco.esperar(1_000)
        assertEquals(1, recentrados)
    }

    @Test
    fun `un gesto a los 8 s reinicia la cuenta`() {
        poner()
        banco.esperar(8_000)
        gestos = 2
        banco.esperar(8_000)
        assertEquals("a los 16 s aun no", 0, recentrados)
        banco.esperar(2_500)
        assertEquals("10 s despues del ultimo gesto", 1, recentrados)
    }

    @Test
    fun `parado no recentra`() {
        enMarcha = false
        poner()
        banco.esperar(60_000)
        assertEquals(0, recentrados)
    }

    @Test
    fun `si el coche para antes de los 10 s se cancela`() {
        poner()
        banco.esperar(8_000)
        enMarcha = false
        banco.esperar(10_000)
        assertEquals(0, recentrados)
    }

    @Test
    fun `sin ningun gesto del usuario no recentra (vista previa o punto elegido)`() {
        gestos = null
        poner()
        banco.esperar(60_000)
        assertEquals(0, recentrados)
    }

    @Test
    fun `siguiendo al coche no hace nada`() {
        sigue = true
        poner()
        banco.esperar(60_000)
        assertEquals(0, recentrados)
    }
}
