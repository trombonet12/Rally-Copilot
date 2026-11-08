package app.trombito.rallycopilot.ui.miscarreteras

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.trombito.rallycopilot.motor.analisis.Carretera
import app.trombito.rallycopilot.motor.analisis.CurvasPorGrado
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.RutaMisCarreteras
import app.trombito.rallycopilot.ui.RutaMisTramos
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ETIQUETA_CARGANDO
import app.trombito.rallycopilot.ui.comun.ETIQUETA_VOLVER
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.mistramos.AccionesMisTramos
import app.trombito.rallycopilot.ui.mistramos.ETIQUETA_TRAMOS_CARRETERAS
import app.trombito.rallycopilot.ui.mistramos.MisTramosScreen
import app.trombito.rallycopilot.ui.mistramos.MisTramosUiState
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
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class MisCarreterasScreenTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private val trazos =
        listOf(
            TrazoCarretera(listOf(PuntoGeo(39.6, 2.9), PuntoGeo(39.61, 2.92)), pasadas = 1),
            TrazoCarretera(listOf(PuntoGeo(39.61, 2.92), PuntoGeo(39.63, 2.95)), pasadas = 12),
        )
    private val carreteras =
        listOf(
            Carretera("Ma-10", "Carretera de Valldemossa", pasadas = 3, metros = 12_400.0, curvas = 85),
            Carretera(null, "Camí de Sa Calobra", pasadas = 1, metros = 850.0, curvas = 1),
        )
    private val lleno =
        MisCarreterasUiState(
            cargando = false,
            recorridos = 4,
            metrosTotales = 123_400.0,
            curvas = CurvasPorGrado(listOf(1, 2, 3, 4, 5, 6), escuadras = 2, horquillas = 7),
            trazos = trazos,
            carreteras = carreteras,
        )

    private fun poner(
        estado: MisCarreterasUiState,
        alVolver: () -> Unit = {},
    ) = banco.poner { RallyCopilotTheme { MisCarreterasScreen(estado, alVolver) } }

    private fun filaCurvas(clave: String) =
        banco.descripcion(banco.conEtiqueta("$ETIQUETA_CARRETERAS_CURVAS_FILA_PREFIJO$clave"))

    @Test
    fun `con datos ensena mapa, leyenda, kilometros, recorridos y carreteras`() {
        poner(lleno)
        assertTrue(banco.hay(ETIQUETA_CARRETERAS_MAPA))
        assertFalse(banco.hay(ETIQUETA_CARRETERAS_MAPA_AVISO))
        assertTrue(banco.hay(ETIQUETA_CARRETERAS_LEYENDA))
        assertTrue("123,4 km" in banco.textos(banco.conEtiqueta(ETIQUETA_CARRETERAS_KM)))
        assertTrue("4" in banco.textos(banco.conEtiqueta(ETIQUETA_CARRETERAS_RECORRIDOS)))
        assertTrue("Carreteras con más curvas" in banco.todosLosTextos())
    }

    @Test
    fun `las barras de curvas describen cada categoria`() {
        poner(lleno)
        assertEquals("Grado 3: 3 curvas", filaCurvas("g3"))
        assertEquals("Escuadra: 2 curvas", filaCurvas("escuadra"))
        assertEquals("Horquilla: 7 curvas", filaCurvas("horquilla"))
        assertEquals(8, banco.etiquetas(ETIQUETA_CARRETERAS_CURVAS_FILA_PREFIJO).size)
    }

    @Test
    fun `la lista de carreteras da nombre, medidas y curvas`() {
        poner(lleno)
        val primera = banco.textos(banco.conEtiqueta("${ETIQUETA_CARRETERAS_TOP_FILA_PREFIJO}0"))
        assertTrue("Carretera de Valldemossa" in primera)
        assertTrue("12,4 km · 3 veces" in primera)
        assertTrue("85 curvas" in primera)
        val segunda = banco.textos(banco.conEtiqueta("${ETIQUETA_CARRETERAS_TOP_FILA_PREFIJO}1"))
        assertTrue("1 curva" in segunda)
        assertTrue("850 m · 1 vez" in segunda)
    }

    @Test
    fun `sin vias el mapa avisa en lugar de dibujar`() {
        poner(lleno.copy(trazos = emptyList(), hayVias = false))
        assertTrue(banco.hay(ETIQUETA_CARRETERAS_MAPA_AVISO))
        assertFalse(banco.hay(ETIQUETA_CARRETERAS_LEYENDA))
        val aviso = banco.textos(banco.conEtiqueta(ETIQUETA_CARRETERAS_MAPA)).first()
        assertTrue("Faltan los datos de carreteras de Mallorca" in aviso)
    }

    @Test
    fun `sin curvas la lista lo dice con amabilidad`() {
        poner(lleno.copy(carreteras = emptyList(), curvas = CurvasPorGrado.VACIO))
        assertTrue(banco.hay(ETIQUETA_CARRETERAS_TOP_VACIO))
    }

    @Test
    fun `cargando ensena el indicador`() {
        poner(MisCarreterasUiState())
        assertTrue(banco.hay(ETIQUETA_CARGANDO))
        assertFalse(banco.hay(ETIQUETA_CARRETERAS_LISTA))
    }

    @Test
    fun `sin recorridos ensena el estado vacio`() {
        poner(MisCarreterasUiState(cargando = false))
        assertTrue(banco.hay(ETIQUETA_CARRETERAS_VACIO))
        assertFalse(banco.hay(ETIQUETA_CARRETERAS_LISTA))
    }

    @Test
    fun `un fallo ensena el estado de error`() {
        poner(MisCarreterasUiState(cargando = false, error = true))
        assertTrue(banco.hay(ETIQUETA_CARRETERAS_ERROR))
    }

    @Test
    fun `volver llama a la accion`() {
        var volvio = 0
        poner(lleno) { volvio++ }
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        assertEquals(1, volvio)
    }

    @Test
    fun `todos los botones tienen nombre para TalkBack`() {
        poner(lleno)
        assertTrue(banco.pulsablesSinNombre().isEmpty())
    }

    @Test
    fun `los niveles de la escala parten en 1, 2, 5 y 10`() {
        assertEquals(NivelUso.UNA, NivelUso.de(1))
        assertEquals(NivelUso.POCAS, NivelUso.de(4))
        assertEquals(NivelUso.VARIAS, NivelUso.de(5))
        assertEquals(NivelUso.VARIAS, NivelUso.de(9))
        assertEquals(NivelUso.MUCHAS, NivelUso.de(10))
        assertEquals(NivelUso.MUCHAS, NivelUso.de(250))
    }

    @Composable
    private fun Navegacion() {
        val nav = rememberNavController()
        NavHost(nav, startDestination = RutaMisTramos) {
            composable<RutaMisTramos> {
                MisTramosScreen(
                    MisTramosUiState(cargando = false),
                    AccionesMisTramos(onMisCarreteras = { nav.navigate(RutaMisCarreteras) }),
                )
            }
            composable<RutaMisCarreteras> { MisCarreterasScreen(lleno, onVolver = { nav.popBackStack() }) }
        }
    }

    @Test
    fun `el icono de Mis tramos abre Mis carreteras y volver regresa`() {
        banco.poner { RallyCopilotTheme { Navegacion() } }
        val icono = banco.conEtiqueta(ETIQUETA_TRAMOS_CARRETERAS)
        assertTrue(banco.esPulsable(icono))
        banco.pulsar(icono)
        banco.esperar(1_000)
        assertTrue(banco.hay(ETIQUETA_CARRETERAS_LISTA))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        banco.esperar(1_000)
        assertTrue(banco.hay(ETIQUETA_TRAMOS_CARRETERAS))
        assertFalse(banco.hay(ETIQUETA_CARRETERAS_LISTA))
    }

    @Test
    fun `el icono de Mis tramos se llama Mis carreteras para TalkBack`() {
        banco.poner {
            RallyCopilotTheme {
                MisTramosScreen(MisTramosUiState(cargando = false), AccionesMisTramos(onMisCarreteras = {}))
            }
        }
        assertTrue(banco.nodos().any { banco.descripcion(it) == "Mis carreteras" })
    }

    @Test
    fun `sin la accion Mis tramos no ensena el icono`() {
        banco.poner { RallyCopilotTheme { MisTramosScreen(MisTramosUiState(cargando = false), AccionesMisTramos()) } }
        assertFalse(banco.hay(ETIQUETA_TRAMOS_CARRETERAS))
    }
}
