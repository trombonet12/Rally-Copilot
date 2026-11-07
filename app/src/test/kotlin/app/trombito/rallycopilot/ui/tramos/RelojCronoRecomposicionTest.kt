package app.trombito.rallycopilot.ui.tramos

import androidx.compose.runtime.SideEffect
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** REN-7: el paso del tiempo del cronómetro no recompone al HUD, solo a quien lee la cifra. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RelojCronoRecomposicionTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private var ahora = AHORA_PRUEBA_MS

    @Test
    fun `el periodo del HUD es una decima`() {
        assertEquals(100L, PERIODO_HUD_MS)
    }

    @Test
    fun `dos segundos de cronometro no recomponen a quien lo pide y la cifra sigue contando`() {
        var composiciones = 0
        lateinit var transcurrido: () -> Long
        banco.poner {
            transcurrido = rememberTranscurridoMs(cronoConocido(transcurridoMs = 150_400L), { ahora }, PERIODO_HUD_MS)
            SideEffect { composiciones++ }
        }
        val antes = composiciones
        val inicio = transcurrido()
        ahora += 2_000L
        banco.esperar(2_000L)
        assertEquals("el llamador no se recompone con el tiempo", antes, composiciones)
        assertTrue("la cifra avanza: ${transcurrido() - inicio}", transcurrido() - inicio >= 1_900L)
    }

    @Test
    fun `el HUD completo mantiene su texto al dia sin recomponerse entero`() {
        banco.poner { androidx.compose.material3.MaterialTheme { HudCrono(cronoConocido(), reloj = { ahora }) } }
        ahora += 3_000L
        banco.esperar(3_000L)
        assertEquals(listOf("02:33,4"), banco.textos(banco.conEtiqueta(ETIQUETA_CRONO_TIEMPO)))
    }
}
