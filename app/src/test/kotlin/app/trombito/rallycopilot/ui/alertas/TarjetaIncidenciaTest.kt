package app.trombito.rallycopilot.ui.alertas

import app.trombito.rallycopilot.motor.alertas.TipoIncidencia
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LADO_TACTIL
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
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
class TarjetaIncidenciaTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private var cerradas = 0

    private val marcador =
        MarcadorIncidencia(
            id = "a",
            tipo = TipoIncidencia.ACCIDENTE,
            lat = 39.8,
            lon = 2.8,
            descripcion = "Accidente en Ma-13, un carril cortado",
            carretera = "Ma-13",
            enRuta = false,
        )

    private fun poner(
        m: MarcadorIncidencia = marcador,
        frescura: FrescuraIncidencias? = null,
    ) = banco.poner { RallyCopilotTheme { TarjetaIncidencia(m, frescura, onCerrar = { cerradas++ }) } }

    @Test
    fun `muestra el tipo, la carretera y la descripcion`() {
        poner()
        val textos = banco.textos(banco.conEtiqueta(ETIQUETA_INCIDENCIA_TARJETA))
        assertTrue("Accidente" in textos)
        assertTrue("Ma-13" in textos)
        assertEquals(
            listOf("Accidente en Ma-13, un carril cortado"),
            banco.textos(banco.conEtiqueta(ETIQUETA_INCIDENCIA_DESCRIPCION)),
        )
    }

    @Test
    fun `sin carretera solo salen el tipo y la descripcion`() {
        poner(marcador.copy(carretera = null, tipo = TipoIncidencia.OBRAS))
        val textos = banco.textos(banco.conEtiqueta(ETIQUETA_INCIDENCIA_TARJETA))
        assertTrue("Obras" in textos)
        assertFalse("Ma-13" in textos)
    }

    @Test
    fun `con datos viejos dice cuanto hace`() {
        poner(frescura = FrescuraIncidencias(2 * 3_600_000L, false))
        assertEquals(listOf("hace 2 h"), banco.textos(banco.conEtiqueta(ETIQUETA_ALERTA_FRESCURA)))
    }

    @Test
    fun `con datos al dia no lleva indicador`() {
        poner()
        assertFalse(banco.hay(ETIQUETA_ALERTA_FRESCURA))
    }

    @Test
    fun `la X cierra la tarjeta y es un objetivo tactil de 48 dp`() {
        poner()
        val cerrar = banco.conEtiqueta(ETIQUETA_INCIDENCIA_CERRAR)
        assertEquals("Cerrar incidencia", banco.descripcion(cerrar))
        assertTrue(cerrar.size.width >= LADO_TACTIL.value * 3 && cerrar.size.height >= LADO_TACTIL.value * 3)
        banco.pulsar(cerrar)
        assertEquals(1, cerradas)
    }
}
