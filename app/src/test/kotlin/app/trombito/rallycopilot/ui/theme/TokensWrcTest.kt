package app.trombito.rallycopilot.ui.theme

import androidx.compose.ui.text.font.FontStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tokens del sistema visual: un solo rojo, una sola tabla de radios, cifras tabulares y secundario sin azul. */
class TokensWrcTest {
    @Test
    fun `un solo rojo de marca en franja y ruta`() {
        assertEquals(RojoAcento, RallyOscuro.franja)
        assertEquals(RojoAcento, RallyClaro.franja)
        assertEquals(RojoAcento, RojoRuta)
    }

    @Test
    fun `los estilos de cifra llevan cifras tabulares y cursiva`() {
        listOf(CifraGrande, CifraMedia, CifraPequena, Tiempo).forEach {
            assertEquals("tnum", it.fontFeatureSettings)
            assertEquals(FontStyle.Italic, it.fontStyle)
        }
        assertEquals(40f, CifraMedia.fontSize.value, 0f)
        assertTrue(CifraGrande.fontSize.value > CifraMedia.fontSize.value)
        assertTrue(CifraMedia.fontSize.value > CifraPequena.fontSize.value)
    }

    @Test
    fun `las formas de material salen de la tabla de radios`() {
        assertEquals(Radios.formaTarjeta, Radios.formasMaterial.medium)
        assertEquals(Radios.formaPildora, Radios.formasMaterial.extraLarge)
    }

    @Test
    fun `el secundario no es azul`() {
        listOf(
            DarkColors.secondary,
            DarkColors.secondaryContainer,
            LightColors.secondary,
            LightColors.secondaryContainer,
        ).forEach { assertTrue("azul dominante: $it", it.blue - it.red < 0.08f) }
    }
}
