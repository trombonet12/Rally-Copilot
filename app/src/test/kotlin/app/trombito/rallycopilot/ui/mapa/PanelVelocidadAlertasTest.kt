package app.trombito.rallycopilot.ui.mapa

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.ui.alertas.AlertasPanel
import app.trombito.rallycopilot.ui.alertas.MediaTramoUi
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyClaro
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import app.trombito.rallycopilot.ui.theme.RallyOscuro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PanelVelocidadAlertasTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun poner(
        velocidad: Int? = 63,
        limite: Int? = 50,
        alertas: AlertasPanel = AlertasPanel(),
        enColumna: Boolean = false,
    ) = banco.poner {
        RallyCopilotTheme {
            PanelVelocidad(
                velocidad,
                limite,
                ViaActual(null, null, limite),
                enColumna,
                alertas = alertas,
            )
        }
    }

    @Test
    fun `sin alertas no hay icono de exceso ni media`() {
        poner()
        assertFalse(banco.hay(ETIQUETA_EXCESO))
        assertFalse(banco.hay(ETIQUETA_MEDIA))
    }

    @Test
    fun `la cifra va en rojo solo si pasa del limite`() {
        val normal = Color.White
        val aviso = RallyOscuro.deltaPeor
        assertEquals(aviso, colorVelocidad(true, normal, aviso))
        assertEquals(normal, colorVelocidad(false, normal, aviso))
        // En claro y en oscuro el rojo es el de la paleta de la marca para «peor».
        assertTrue(RallyClaro.deltaPeor != RallyOscuro.deltaPeor)
    }

    @Test
    fun `en rojo aparece el icono de exceso como region viva y TalkBack lo anuncia`() {
        poner(alertas = AlertasPanel(rojo = true))
        val icono = banco.conEtiqueta(ETIQUETA_EXCESO)
        assertEquals("Por encima del límite de velocidad", banco.descripcion(icono))
        assertEquals(LiveRegionMode.Polite, icono.config.getOrNull(SemanticsProperties.LiveRegion))
        // La cifra sigue siendo la velocidad: la descripción de la velocidad no se anuncia en cada cambio.
        val velocidad = banco.conEtiqueta(ETIQUETA_VELOCIDAD)
        assertEquals("Velocidad: 63 kilómetros por hora", banco.descripcion(velocidad))
        assertEquals(null, velocidad.config.getOrNull(SemanticsProperties.LiveRegion))
    }

    @Test
    fun `con exceso del diez por ciento sigue en rojo con el marco parpadeante`() {
        poner(alertas = AlertasPanel(rojo = true, exceso = true))
        assertTrue(banco.hay(ETIQUETA_EXCESO))
        banco.esperar(2_000)
        assertTrue(banco.hay(ETIQUETA_EXCESO))
        assertTrue("63" in banco.textos(banco.conEtiqueta(ETIQUETA_VELOCIDAD)))
    }

    @Test
    fun `sin velocidad no hay rojo aunque el dominio lo diga`() {
        poner(velocidad = null, alertas = AlertasPanel(rojo = true))
        assertFalse(banco.hay(ETIQUETA_EXCESO))
    }

    @Test
    fun `la media del tramo sale junto a la senal del limite`() {
        poner(alertas = AlertasPanel(tramo = MediaTramoUi(78, supera = false, terminado = false)))
        val media = banco.conEtiqueta(ETIQUETA_MEDIA)
        assertEquals(listOf("MEDIA", "78"), banco.textos(media))
        assertEquals("Velocidad media del tramo: 78 kilómetros por hora", banco.descripcion(media))
        assertTrue(banco.hay(ETIQUETA_LIMITE))
    }

    @Test
    fun `la media que supera el limite lo dice`() {
        poner(alertas = AlertasPanel(tramo = MediaTramoUi(91, supera = true, terminado = false)))
        assertEquals(
            "Velocidad media del tramo: 91 kilómetros por hora, por encima del límite",
            banco.descripcion(banco.conEtiqueta(ETIQUETA_MEDIA)),
        )
    }

    @Test
    fun `la media final se rotula FINAL`() {
        poner(alertas = AlertasPanel(tramo = MediaTramoUi(74, supera = false, terminado = true)))
        val media = banco.conEtiqueta(ETIQUETA_MEDIA)
        assertEquals(listOf("FINAL", "74"), banco.textos(media))
        assertEquals("Velocidad media final del tramo: 74 kilómetros por hora", banco.descripcion(media))
    }

    @Test
    fun `antes de recorrer 100 m la media lleva una raya`() {
        poner(alertas = AlertasPanel(tramo = MediaTramoUi(null, supera = false, terminado = false)))
        val media = banco.conEtiqueta(ETIQUETA_MEDIA)
        assertEquals(listOf("MEDIA", "—"), banco.textos(media))
        assertEquals("Calculando la velocidad media del tramo", banco.descripcion(media))
    }

    @Test
    fun `sin limite de la via la media del tramo sale igual`() {
        poner(limite = null, alertas = AlertasPanel(tramo = MediaTramoUi(60, supera = false, terminado = false)))
        assertFalse(banco.hay(ETIQUETA_LIMITE))
        assertTrue(banco.hay(ETIQUETA_MEDIA))
    }

    @Test
    fun `en la columna lateral tambien salen la media y el exceso`() {
        poner(
            alertas =
                AlertasPanel(
                    rojo = true,
                    exceso = true,
                    tramo = MediaTramoUi(80, supera = true, terminado = false),
                ),
            enColumna = true,
        )
        assertTrue(banco.hay(ETIQUETA_MEDIA))
        assertTrue(banco.hay(ETIQUETA_EXCESO))
        assertTrue(banco.hay(ETIQUETA_LIMITE))
    }
}
