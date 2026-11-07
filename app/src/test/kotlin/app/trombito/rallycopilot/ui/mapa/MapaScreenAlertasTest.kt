package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.motor.alertas.TipoIncidencia
import app.trombito.rallycopilot.ui.alertas.AlertasPanel
import app.trombito.rallycopilot.ui.alertas.AlertasUiState
import app.trombito.rallycopilot.ui.alertas.AvisoAlerta
import app.trombito.rallycopilot.ui.alertas.ClaseRadar
import app.trombito.rallycopilot.ui.alertas.ETIQUETA_ALERTA_BANNER
import app.trombito.rallycopilot.ui.alertas.ETIQUETA_INCIDENCIA_CERRAR
import app.trombito.rallycopilot.ui.alertas.ETIQUETA_INCIDENCIA_TARJETA
import app.trombito.rallycopilot.ui.alertas.MarcadorIncidencia
import app.trombito.rallycopilot.ui.alertas.MediaTramoUi
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_MODO_BOTON
import app.trombito.rallycopilot.ui.navegacion.NavegacionEnMapa
import app.trombito.rallycopilot.ui.navegacion.NavegacionUiState
import app.trombito.rallycopilot.ui.navegacion.SOLLER
import app.trombito.rallycopilot.ui.navegacion.guiado.ETIQUETA_PANEL_GUIADO
import app.trombito.rallycopilot.ui.navegacion.notas.ETIQUETA_FRANJA_NOTAS
import app.trombito.rallycopilot.ui.navegacion.notas.NotasUiState
import app.trombito.rallycopilot.ui.navegacion.notas.deNota
import app.trombito.rallycopilot.ui.navegacion.notas.nota
import app.trombito.rallycopilot.ui.navegacion.notas.visible
import app.trombito.rallycopilot.ui.navegacion.panelGuiadoMapa
import app.trombito.rallycopilot.ui.navegacion.rutaDePrueba
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ETIQUETA_MAPA_FALSO = "mapa-falso"

/** Las alertas montadas en [MapaScreen]: banner, panel de velocidad y tarjeta de la incidencia tocada. */
abstract class MapaScreenAlertasBase {
    @get:Rule val limpieza = LimpiezaCompose()

    protected val banco = BancoCompose()
    protected var cerradas = 0

    private val datos = EstadoDatosMapa(instalados = DatosInstalados("2026.10.1", 1_000_000, conSatelite = true))
    private val mapa = MapaUiState(cargando = false, datos = datos, estilos = EstilosMapa("{\"v\":1}", null))

    protected val libre = NavegacionUiState(control = EstadoNavegacion.Libre(0L, 0.0))

    protected fun guiando() =
        NavegacionUiState(
            control =
                EstadoNavegacion.Guiando(
                    Destino("Sóller", SOLLER),
                    PerfilRuta.MAS_RAPIDA,
                    rutaDePrueba(),
                    null,
                    false,
                ),
        )

    protected val notas =
        NotasUiState(listOf(visible(deNota(nota(4, siguienteM = 50))), visible(deNota(nota(2)), 300.0)))

    protected val radar = AlertasUiState(aviso = AvisoAlerta.Radar(ClaseRadar.FIJO, 500, 50))

    protected fun poner(
        navegacion: NavegacionUiState,
        alertas: AlertasUiState,
        notasEnFranja: NotasUiState = NotasUiState(),
        velocidad: Int? = 63,
    ) = banco.poner {
        RallyCopilotTheme {
            MapaScreen(
                estado = mapa.copy(velocidadKmh = velocidad),
                vista = EstadoVistaMapa(),
                acciones = AccionesMapa(onCerrarIncidencia = { cerradas++ }),
                mapa = { _, modificador -> Box(modificador.fillMaxSize().testTag(ETIQUETA_MAPA_FALSO)) },
                navegacion = NavegacionEnMapa(navegacion, panelGuiado = panelGuiadoMapa(), notas = notasEnFranja),
                alertas = alertas,
            )
        }
    }

    protected fun banner() = banco.conEtiqueta(ETIQUETA_ALERTA_BANNER).boundsInRoot

    /** Con exceso, el borde pulsa en la píldora (sin relleno) y los botones redondos no la pisan. */
    protected fun comprobarExcesoEnLaPildora() {
        poner(guiando(), AlertasUiState(panel = AlertasPanel(rojo = true, exceso = true)), velocidad = 148)
        val raiz = banco.raiz().boundsInRoot
        val hoja = banco.conEtiqueta("panel").boundsInRoot
        val pastilla = banco.conEtiqueta(ETIQUETA_EXCESO).boundsInRoot
        val velocidad = banco.conEtiqueta(ETIQUETA_VELOCIDAD).boundsInRoot
        assertTrue("la pastilla dentro de la pantalla: $pastilla vs $raiz", pastilla.dentroDe(raiz))
        assertTrue("la pastilla sobre la hoja: $pastilla vs $hoja", pastilla.bottom <= hoja.top + 1)
        assertTrue("velocidad dentro de la pastilla: $velocidad vs $pastilla", velocidad.dentroDe(pastilla))
        // Con exceso se va deprisa: capas se esconde y los botones de conducción y la brújula se quedan.
        listOf(ETIQUETA_MODO_BOTON, ETIQUETA_ORIENTACION).forEach {
            val boton = banco.conEtiqueta(it).boundsInRoot
            assertTrue("$it dentro de la pantalla: $boton", boton.dentroDe(raiz))
            assertFalse("$it no pisa la pastilla", boton.overlaps(pastilla))
        }
    }

    private fun Rect.dentroDe(otro: Rect) =
        left >= otro.left && right <= otro.right && top >= otro.top && bottom <= otro.bottom
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class MapaScreenAlertasVerticalTest : MapaScreenAlertasBase() {
    @Test
    fun `sin alertas no hay banner`() {
        poner(libre, AlertasUiState())
        assertFalse(banco.hay(ETIQUETA_ALERTA_BANNER))
    }

    @Test
    fun `en modo libre el banner va bajo la franja de notas`() {
        poner(libre, radar, notas)
        val franja = banco.conEtiqueta(ETIQUETA_FRANJA_NOTAS).boundsInRoot
        assertTrue("el banner baja de la franja: ${banner()} vs $franja", banner().top >= franja.bottom)
        assertTrue("ocupa todo el ancho", banner().width > 411 * 3 * 0.8f)
    }

    @Test
    fun `en modo libre sin notas el banner sale igual`() {
        poner(libre, radar)
        assertTrue(banco.hay(ETIQUETA_ALERTA_BANNER))
    }

    @Test
    fun `guiando el banner va bajo el banner de maniobra y la franja de notas`() {
        poner(guiando(), radar, notas)
        val franja = banco.conEtiqueta(ETIQUETA_FRANJA_NOTAS).boundsInRoot
        assertTrue(banco.hay(ETIQUETA_PANEL_GUIADO))
        assertTrue("bajo la franja: ${banner()} vs $franja", banner().top >= franja.bottom)
    }

    @Test
    fun `el banner no tapa el panel de velocidad`() {
        poner(guiando(), radar, notas)
        val panel = banco.conEtiqueta("panel").boundsInRoot
        assertTrue(banner().bottom <= panel.top)
    }

    @Test
    fun `el panel recibe la velocidad en rojo y la media del tramo`() {
        val tramo = MediaTramoUi(78, supera = false, terminado = false)
        poner(guiando(), AlertasUiState(panel = AlertasPanel(rojo = true, exceso = true, tramo = tramo)))
        assertTrue(banco.hay(ETIQUETA_EXCESO))
        assertEquals(listOf("MEDIA", "78"), banco.textos(banco.conEtiqueta(ETIQUETA_MEDIA)))
    }

    @Test
    fun `en vertical el exceso se ve en la pildora junto a los botones`() = comprobarExcesoEnLaPildora()

    @Test
    fun `la incidencia tocada abre su tarjeta y se cierra desde la X`() {
        val marcador =
            MarcadorIncidencia("a", TipoIncidencia.OBRAS, 39.7, 2.9, "Obras en Ma-10", "Ma-10", enRuta = false)
        poner(libre, AlertasUiState(marcadores = listOf(marcador), seleccionada = marcador))
        assertTrue(banco.hay(ETIQUETA_INCIDENCIA_TARJETA))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_INCIDENCIA_CERRAR))
        assertEquals(1, cerradas)
    }

    @Test
    fun `sin incidencia elegida no hay tarjeta`() {
        poner(libre, AlertasUiState())
        assertFalse(banco.hay(ETIQUETA_INCIDENCIA_TARJETA))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w891dp-h411dp-xxhdpi")
class MapaScreenAlertasHorizontalTest : MapaScreenAlertasBase() {
    @Test
    fun `en horizontal el banner compacto cabe en la zona del mapa`() {
        poner(libre, radar, notas)
        val b = banner()
        val raiz = banco.raiz().boundsInRoot
        assertTrue("cabe en la pantalla: $b vs $raiz", b.right <= raiz.right)
        assertTrue("es bajo: $b", b.height < 411 * 3 * 0.25f)
    }

    @Test
    fun `en horizontal el exceso cabe en la pildora`() = comprobarExcesoEnLaPildora()

    @Test
    fun `guiando en horizontal el banner sale bajo la franja`() {
        poner(guiando(), radar, notas)
        val franja = banco.conEtiqueta(ETIQUETA_FRANJA_NOTAS).boundsInRoot
        assertTrue(banner().top >= franja.bottom)
    }
}
