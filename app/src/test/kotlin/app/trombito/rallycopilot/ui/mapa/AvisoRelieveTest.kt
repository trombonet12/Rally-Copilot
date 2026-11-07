package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.datos.ProgresoDescarga
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AvisoRelieveTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun poner(progreso: ProgresoDescarga?) =
        banco.poner { RallyCopilotTheme { AvisoRelieveDescargando(progreso) } }

    @Test
    fun `sin descarga no se pinta nada`() {
        poner(null)
        assertFalse(banco.hay(ETIQUETA_AVISO_RELIEVE))
    }

    @Test
    fun `mientras se descarga dice descargando relieve con su barra`() {
        poner(ProgresoDescarga(25, 100, false))
        val aviso = banco.conEtiqueta(ETIQUETA_AVISO_RELIEVE)
        assertTrue("Descargando relieve y curvas de nivel…" in banco.textos(aviso))
    }

    @Test
    fun `esperando red lo dice en vez de descargando`() {
        poner(ProgresoDescarga(0, 0, true))
        assertTrue("Esperando conexión" in banco.textos(banco.conEtiqueta(ETIQUETA_AVISO_RELIEVE)))
    }

    @Test
    fun `la fraccion solo se sabe con total y con red`() {
        assertEquals(0.25f, fraccionDescarga(ProgresoDescarga(25, 100, false))!!, 0.001f)
        assertEquals(1f, fraccionDescarga(ProgresoDescarga(500, 100, false))!!, 0.001f)
        assertNull(fraccionDescarga(ProgresoDescarga(10, 0, false)))
        assertNull(fraccionDescarga(ProgresoDescarga(10, 100, true)))
    }
}
