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
import app.trombito.rallycopilot.domain.tramos.ResultadoCrono
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.navegacion.AccionesNavegacion
import app.trombito.rallycopilot.ui.navegacion.ETIQUETA_SALIR_MAPA
import app.trombito.rallycopilot.ui.navegacion.ETIQUETA_TERMINAR
import app.trombito.rallycopilot.ui.navegacion.NavegacionEnMapa
import app.trombito.rallycopilot.ui.navegacion.NavegacionUiState
import app.trombito.rallycopilot.ui.navegacion.SOLLER
import app.trombito.rallycopilot.ui.navegacion.panelGuiadoMapa
import app.trombito.rallycopilot.ui.navegacion.rutaDePrueba
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import app.trombito.rallycopilot.ui.tramos.cronoListo
import app.trombito.rallycopilot.ui.tramos.cronoSinTramo
import app.trombito.rallycopilot.ui.tramos.cronoTerminado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** UX-6: la ✕ de la hoja pide un segundo toque cuando hay cronómetro o grabación en marcha. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ConfirmarTerminarTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private var salidas = 0
    private var terminadas = 0

    private val datos = EstadoDatosMapa(instalados = DatosInstalados("2026.10.1", 1_000_000, conSatelite = false))
    private val mapa = MapaUiState(cargando = false, datos = datos, estilos = EstilosMapa("{}", null))

    private fun libre(distanciaM: Double = 0.0) = NavegacionUiState(control = EstadoNavegacion.Libre(0L, distanciaM))

    private fun guiando() =
        NavegacionUiState(
            control =
                EstadoNavegacion.Guiando(Destino("Sóller", SOLLER), PerfilRuta.MAS_RAPIDA, rutaDePrueba(), null, false),
        )

    private fun poner(
        navegacion: NavegacionUiState,
        crono: CronoEnCurso?,
    ) = banco.poner {
        RallyCopilotTheme {
            MapaScreen(
                estado = mapa,
                vista = EstadoVistaMapa(),
                acciones = AccionesMapa(onSalir = { salidas++ }),
                mapa = { _, m -> Box(m.fillMaxSize().testTag("mapa-falso")) },
                navegacion =
                    NavegacionEnMapa(
                        navegacion,
                        AccionesNavegacion(onTerminar = { terminadas++ }),
                        panelGuiado = panelGuiadoMapa(),
                    ),
                crono = crono,
            )
        }
    }

    private fun toque(etiqueta: String) = banco.pulsar(banco.conEtiqueta(etiqueta))

    @Test
    fun `con el crono en marcha el primer toque a la X avisa y el segundo cierra el mapa`() {
        poner(libre(), cronoSinTramo())
        toque(ETIQUETA_SALIR_MAPA)
        assertEquals("el primer toque no cierra", 0, salidas)
        assertEquals(
            listOf("Toca otra vez para terminar"),
            banco.textos(banco.conEtiqueta(ETIQUETA_CONFIRMAR_TERMINAR)),
        )
        toque(ETIQUETA_SALIR_MAPA)
        assertEquals(1, salidas)
        assertFalse("el aviso se va al confirmar", banco.hay(ETIQUETA_CONFIRMAR_TERMINAR))
    }

    @Test
    fun `guiando con el crono en marcha la X de terminar tambien pide un segundo toque`() {
        poner(guiando(), cronoSinTramo())
        toque(ETIQUETA_TERMINAR)
        assertEquals(0, terminadas)
        assertTrue(banco.hay(ETIQUETA_CONFIRMAR_TERMINAR))
        toque(ETIQUETA_TERMINAR)
        assertEquals(1, terminadas)
    }

    @Test
    fun `el aviso dura 3 segundos y despues el toque vuelve a ser el primero`() {
        poner(guiando(), cronoSinTramo())
        toque(ETIQUETA_TERMINAR)
        banco.esperar(2_000)
        assertTrue("a los 2 s sigue", banco.hay(ETIQUETA_CONFIRMAR_TERMINAR))
        banco.esperar(1_500)
        assertFalse("a los 3,5 s se ha ido", banco.hay(ETIQUETA_CONFIRMAR_TERMINAR))
        toque(ETIQUETA_TERMINAR)
        assertEquals("pasados los 3 s hay que volver a confirmar", 0, terminadas)
        assertTrue(banco.hay(ETIQUETA_CONFIRMAR_TERMINAR))
    }

    @Test
    fun `sin crono ni grabacion un toque basta`() {
        poner(libre(distanciaM = 0.0), crono = null)
        toque(ETIQUETA_SALIR_MAPA)
        assertEquals(1, salidas)
        assertFalse(banco.hay(ETIQUETA_CONFIRMAR_TERMINAR))
    }

    @Test
    fun `con el crono listo y sin salida un toque basta`() {
        poner(guiando(), cronoListo())
        toque(ETIQUETA_TERMINAR)
        assertEquals(1, terminadas)
    }

    @Test
    fun `con el tramo ya terminado un toque basta`() {
        poner(
            guiando(),
            cronoTerminado(ResultadoCrono(148_100L, -2_300L, nuevoMejor = true, mejorAnteriorMs = 150_400L)),
        )
        toque(ETIQUETA_TERMINAR)
        assertEquals(1, terminadas)
    }

    @Test
    fun `en libre con recorrido grabado pide confirmacion aunque no haya crono`() {
        poner(libre(distanciaM = 800.0), crono = null)
        toque(ETIQUETA_SALIR_MAPA)
        assertEquals(0, salidas)
        assertTrue(banco.hay(ETIQUETA_CONFIRMAR_TERMINAR))
        toque(ETIQUETA_SALIR_MAPA)
        assertEquals(1, salidas)
    }
}
