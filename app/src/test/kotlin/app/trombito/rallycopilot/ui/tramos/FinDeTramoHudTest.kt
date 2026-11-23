package app.trombito.rallycopilot.ui.tramos

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «Fin de tramo en X m» del HUD: redondeo a 50 m, «Meta» a menos de 50 m y la fuente de la distancia. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FinDeTramoHudTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val recursos get() = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    private fun texto(restanteM: Double) = textoFinDeTramo(recursos, restanteM)

    private fun poner(
        crono: CronoEnCurso,
        delCamino: Double? = null,
        compacto: Boolean = false,
    ) {
        val estado = mutableStateOf(delCamino)
        banco.poner {
            RallyCopilotTheme {
                CompositionLocalProvider(LocalFinDeTramoM provides estado) {
                    HudCrono(crono, compacto = compacto, reloj = { AHORA_PRUEBA_MS })
                }
            }
        }
    }

    private fun fin() = banco.textos(banco.conEtiqueta(ETIQUETA_CRONO_FIN_TRAMO))

    @Test
    fun `redondea a 50 metros y dice la distancia`() {
        assertEquals("Fin de tramo en 800 m", texto(800.0))
        assertEquals("Fin de tramo en 800 m", texto(824.0))
        assertEquals("Fin de tramo en 850 m", texto(826.0))
        assertEquals("Fin de tramo en 50 m", texto(60.0))
    }

    @Test
    fun `desde el kilometro lo dice en kilometros`() {
        assertEquals("Fin de tramo en 2,2 km", texto(2_190.0))
        assertEquals("Fin de tramo en 1,0 km", texto(1_010.0))
    }

    @Test
    fun `a menos de 50 metros dice Meta`() {
        assertEquals("Meta", texto(49.0))
        assertEquals("Meta", texto(0.0))
        assertEquals("Fin de tramo en 50 m", texto(50.0))
    }

    @Test
    fun `un tramo conocido usa los metros que le faltan al cronometro`() {
        // Meta a 3.990 m, llevamos 3.190: faltan 800 m.
        poner(cronoConocido(progresoM = 3_190.0), delCamino = 123.0)
        assertEquals(listOf("Fin de tramo en 800 m"), fin())
    }

    @Test
    fun `un tramo conocido llegando a meta dice Meta`() {
        poner(cronoConocido(progresoM = 3_960.0))
        assertEquals(listOf("Meta"), fin())
    }

    @Test
    fun `un subtramo con referencia usa lo que falta de su trazado de referencia`() {
        // cronoConSubtramo: 4.000 m de referencia y 2.400 recorridos.
        poner(cronoConSubtramo(), delCamino = 123.0)
        assertEquals(listOf("Fin de tramo en 1,6 km"), fin())
    }

    @Test
    fun `sin la distancia del cronometro se usa la del camino`() {
        poner(cronoConSubtramo(deltaMs = null), delCamino = 820.0)
        assertEquals(listOf("Fin de tramo en 800 m"), fin())
    }

    @Test
    fun `sin ninguna fuente no hay linea de fin de tramo`() {
        poner(cronoConSubtramo(deltaMs = null), delCamino = null)
        assertFalse(banco.hay(ETIQUETA_CRONO_FIN_TRAMO))
    }

    @Test
    fun `en apaisado tambien sale junto al rotulo`() {
        poner(cronoConSubtramo(), compacto = true)
        assertEquals(listOf("Fin de tramo en 1,6 km"), fin())
        assertEquals(listOf("Subtramo · 2,4 km"), banco.textos(banco.conEtiqueta(ETIQUETA_CRONO_ROTULO)))
    }

    @Test
    fun `el segmento sin longitud conocida no tiene restante`() {
        assertNull(cronoConSubtramo(deltaMs = null).segmento!!.restanteM)
        assertEquals(1_600.0, cronoConSubtramo().segmento!!.restanteM!!, 0.001)
        assertEquals(2_190.0, cronoConocido(progresoM = 1_800.0).segmento!!.restanteM!!, 0.001)
    }
}
