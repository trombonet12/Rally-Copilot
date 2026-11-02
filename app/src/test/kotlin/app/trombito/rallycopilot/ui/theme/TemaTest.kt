package app.trombito.rallycopilot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.trombito.rallycopilot.domain.ajustes.Tema
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.mapa.MapaUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TemaTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    @Test
    fun `el tema oscuro es el predeterminado y sistema sigue al movil`() {
        assertEquals(Tema.OSCURO, Tema.PREDETERMINADO)
        assertTrue(Tema.OSCURO.esOscuro(sistemaOscuro = false))
        assertFalse(Tema.CLARO.esOscuro(sistemaOscuro = true))
        assertTrue(Tema.SISTEMA.esOscuro(sistemaOscuro = true))
        assertFalse(Tema.SISTEMA.esOscuro(sistemaOscuro = false))
    }

    @Test
    fun `el tema claro pinta con la paleta clara y el oscuro con la oscura`() {
        var primario: Color? = null
        var rally: ColoresRally? = null
        var oscuro: Boolean? = null

        @Composable
        fun leer() {
            primario = MaterialTheme.colorScheme.primary
            rally = LocalColoresRally.current
            oscuro = LocalTemaOscuro.current
        }
        banco.poner { RallyCopilotTheme(darkTheme = false) { leer() } }
        assertEquals(LightColors.primary, primario)
        assertEquals(RallyClaro, rally)
        assertEquals(false, oscuro)

        banco.poner { RallyCopilotTheme(darkTheme = true) { leer() } }
        assertEquals(DarkColors.primary, primario)
        assertEquals(RallyOscuro, rally)
        assertEquals(true, oscuro)
    }

    @Test
    fun `por defecto el tema local es oscuro`() {
        var oscuro: Boolean? = null
        banco.poner { oscuro = LocalTemaOscuro.current }
        assertEquals(true, oscuro)
    }

    private val estilos = EstilosMapa(vectorial = "oscuro", satelite = "satelite", vectorialClaro = "claro")

    @Test
    fun `con tema claro el mapa usa el estilo claro si los datos lo traen`() {
        val estado = MapaUiState(cargando = false, estilos = estilos, temaOscuro = false)
        assertEquals("claro", estado.estiloJson)
        assertEquals("oscuro", estado.copy(temaOscuro = true).estiloJson)
    }

    @Test
    fun `sin estilo claro en los datos el mapa sigue oscuro con el tema claro`() {
        val sinClaro = estilos.copy(vectorialClaro = null)
        assertEquals("oscuro", MapaUiState(cargando = false, estilos = sinClaro, temaOscuro = false).estiloJson)
    }

    @Test
    fun `el satelite no depende del tema`() {
        val estado =
            MapaUiState(cargando = false, estilos = estilos, capaElegida = CapaMapa.SATELITE, temaOscuro = false)
        assertEquals("satelite", estado.estiloJson)
    }
}
