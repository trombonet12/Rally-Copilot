package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.testTag
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.copiloto.AccionesCopiloto
import app.trombito.rallycopilot.ui.copiloto.CopilotoEnMapa
import app.trombito.rallycopilot.ui.copiloto.CopilotoUiState
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_MODO_BOTON
import app.trombito.rallycopilot.ui.navegacion.NavegacionEnMapa
import app.trombito.rallycopilot.ui.navegacion.NavegacionUiState
import app.trombito.rallycopilot.ui.navegacion.SOLLER
import app.trombito.rallycopilot.ui.navegacion.panelGuiadoMapa
import app.trombito.rallycopilot.ui.navegacion.rutaDePrueba
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import app.trombito.rallycopilot.ui.tramos.cronoConocido
import app.trombito.rallycopilot.ui.tramos.cronoSinTramo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UX-25: recorre la pantalla de conducción en sus variantes y falla si un nodo pulsable no tiene texto ni
 * `contentDescription`: un botón de solo icono sin descripción es invisible para TalkBack.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class NombresPulsablesMapaTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val datos = EstadoDatosMapa(instalados = DatosInstalados("2026.10.1", 1_000_000, conSatelite = true))
    private val mapa =
        MapaUiState(
            cargando = false,
            datos = datos,
            estilos = EstilosMapa("{}", "{}"),
            velocidadKmh = 0,
            via = ViaActual("Carretera", "Ma-10", null),
        )

    private fun poner(
        navegacion: NavegacionUiState,
        crono: CronoEnCurso? = null,
    ) = banco.poner {
        RallyCopilotTheme {
            MapaScreen(
                estado = mapa,
                vista = EstadoVistaMapa(),
                acciones = AccionesMapa(),
                mapa = { _, m -> Box(m.fillMaxSize().testTag("mapa-falso")) },
                navegacion = NavegacionEnMapa(navegacion, panelGuiado = panelGuiadoMapa()),
                copiloto = CopilotoEnMapa(CopilotoUiState(cargado = true), AccionesCopiloto()),
                crono = crono,
            )
        }
    }

    private fun comprobar(situacion: String) {
        val sin = banco.pulsablesSinNombre().map { "${banco.nodos().indexOf(it)}@${it.boundsInRoot}" }
        assertTrue("$situacion: pulsables sin texto ni descripción: $sin", sin.isEmpty())
    }

    private val libre = NavegacionUiState(control = EstadoNavegacion.Libre(0L, 800.0))

    private fun guiando() =
        NavegacionUiState(
            control =
                EstadoNavegacion.Guiando(Destino("Sóller", SOLLER), PerfilRuta.MAS_RAPIDA, rutaDePrueba(), null, false),
        )

    @Test
    fun `el mapa en reposo`() {
        poner(NavegacionUiState())
        comprobar("reposo")
    }

    @Test
    fun `libre con cronometro y panel de capas abierto`() {
        poner(libre, cronoSinTramo())
        banco.pulsar(banco.conEtiqueta(ETIQUETA_CAPAS_BOTON))
        comprobar("libre con capas")
    }

    @Test
    fun `guiando con HUD de tramo conocido`() {
        poner(guiando(), cronoConocido())
        comprobar("guiando")
    }

    @Test
    fun `con la hoja del copiloto abierta`() {
        poner(libre)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_MODO_BOTON))
        assertTrue(banco.hay(ETIQUETA_RAPIDOS_PANEL))
        comprobar("hoja del copiloto")
    }

    @Test
    fun `con el aviso de toque otra vez armado`() {
        poner(guiando(), cronoSinTramo())
        banco.pulsar(banco.conEtiqueta(app.trombito.rallycopilot.ui.navegacion.ETIQUETA_TERMINAR))
        assertTrue(banco.hay(ETIQUETA_CONFIRMAR_TERMINAR))
        comprobar("aviso armado")
    }
}
