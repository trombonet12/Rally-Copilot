package app.trombito.rallycopilot.ui.comun

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** UX-16 (el borde de exceso pulsa tres veces y queda fijo) y UX-24 (con TalkBack no hay regiones vivas dobles). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PulsoExcesoYLectorTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    @Test
    fun `el borde de exceso hace tres pulsos de 1,2 segundos y queda fijo`() {
        assertEquals("empieza opaco", 1f, alfaBordeExceso(0), 0.001f)
        assertEquals("primer minimo", 0.25f, alfaBordeExceso(600), 0.001f)
        assertEquals("fin del primer pulso", 1f, alfaBordeExceso(1_200), 0.001f)
        assertEquals("segundo minimo", 0.25f, alfaBordeExceso(1_800), 0.001f)
        assertEquals("tercer minimo", 0.25f, alfaBordeExceso(3_000), 0.001f)
        assertTrue("entre pulsos baja y sube", alfaBordeExceso(300) in 0.25f..1f)
    }

    @Test
    fun `pasados los tres pulsos el borde esta fijo en opaco para siempre`() {
        listOf(3_600L, 3_601L, 5_000L, 60_000L, 3_600_000L).forEach {
            assertEquals("a los $it ms", 1f, alfaBordeExceso(it), 0f)
        }
        assertEquals(3, PULSOS_EXCESO)
    }

    @Test
    fun `la pildora con exceso compone, pulsa y se calma sin dejar animaciones infinitas`() {
        banco.poner { RallyCopilotTheme { PildoraVelocidad(velocidadKmh = 90, limiteKmh = 50) } }
        banco.esperar(2_000)
        banco.esperar(2_500)
        assertTrue(banco.hay(ETIQUETA_PILDORA_VELOCIDAD))
        assertEquals(
            "Velocidad: 90 kilómetros por hora. Límite de velocidad: 50 kilómetros por hora. Por encima del límite",
            banco.descripcion(banco.conEtiqueta(ETIQUETA_PILDORA_VELOCIDAD)),
        )
    }

    @Test
    fun `sin lector de pantalla la banda de GPS es region viva educada`() {
        banco.poner { RallyCopilotTheme { BandaSinGps(sinSenal = true) } }
        val nodo = banco.conEtiqueta(ETIQUETA_SIN_GPS)
        assertEquals(LiveRegionMode.Polite, nodo.config.getOrNull(SemanticsProperties.LiveRegion))
    }

    @Test
    fun `con lector de pantalla la banda de GPS no es region viva y sigue en pantalla`() {
        banco.poner { RallyCopilotTheme { BandaSinGps(sinSenal = true, lectorActivo = true) } }
        val nodo = banco.conEtiqueta(ETIQUETA_SIN_GPS)
        assertNull(nodo.config.getOrNull(SemanticsProperties.LiveRegion))
        assertTrue(banco.textos(nodo).contains("Sin señal GPS"))
    }

    @Test
    fun `se detecta el lector de pantalla y se sigue su activacion`() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        val gestor = contexto.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        shadowOf(gestor).setTouchExplorationEnabled(false)
        var visto by mutableStateOf<Boolean?>(null)
        banco.poner { visto = rememberLectorPantallaActivo() }
        assertEquals(false, visto)
        shadowOf(gestor).setTouchExplorationEnabled(true)
        banco.reposo()
        assertEquals(true, visto)
        assertFalse(banco.hay("nada"))
    }
}
