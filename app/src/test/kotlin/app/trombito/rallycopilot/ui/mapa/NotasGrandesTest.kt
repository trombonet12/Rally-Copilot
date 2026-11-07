package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.ui.alertas.AlertasUiState
import app.trombito.rallycopilot.ui.alertas.AvisoAlerta
import app.trombito.rallycopilot.ui.alertas.ClaseRadar
import app.trombito.rallycopilot.ui.alertas.ETIQUETA_ALERTA_BANNER
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.copiloto.AccionesCopiloto
import app.trombito.rallycopilot.ui.copiloto.CopilotoEnMapa
import app.trombito.rallycopilot.ui.copiloto.CopilotoUiState
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_MODO_BOTON
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_MODO_PREFIJO
import app.trombito.rallycopilot.ui.navegacion.NavegacionEnMapa
import app.trombito.rallycopilot.ui.navegacion.NavegacionUiState
import app.trombito.rallycopilot.ui.navegacion.SOLLER
import app.trombito.rallycopilot.ui.navegacion.notas.ETIQUETA_FRANJA_NOTAS
import app.trombito.rallycopilot.ui.navegacion.notas.ETIQUETA_NOTA_ENORME
import app.trombito.rallycopilot.ui.navegacion.notas.ETIQUETA_NOTA_ENORME_DISTANCIA
import app.trombito.rallycopilot.ui.navegacion.notas.ETIQUETA_NOTA_PREFIJO
import app.trombito.rallycopilot.ui.navegacion.notas.NotasUiState
import app.trombito.rallycopilot.ui.navegacion.notas.deNota
import app.trombito.rallycopilot.ui.navegacion.notas.nota
import app.trombito.rallycopilot.ui.navegacion.notas.visible
import app.trombito.rallycopilot.ui.navegacion.panelGuiadoMapa
import app.trombito.rallycopilot.ui.navegacion.rutaDePrueba
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import app.trombito.rallycopilot.ui.tramos.ETIQUETA_CRONO_HUD
import app.trombito.rallycopilot.ui.tramos.cronoConocido
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ETIQUETA_MAPA_FALSO = "mapa-falso"

/** El modo de notas grandes (roadbook) en [MapaScreen]: sustituye al mapa, se activa desde el panel y se recuerda. */
abstract class NotasGrandesBase {
    @get:Rule val limpieza = LimpiezaCompose()

    protected val banco = BancoCompose()
    protected var copiloto by mutableStateOf(CopilotoUiState(cargado = true, notasGrandes = true))
    protected val pedidas = mutableListOf<Boolean>()

    private val datos = EstadoDatosMapa(instalados = DatosInstalados("2026.10.1", 1_000_000, conSatelite = false))
    protected val notas =
        NotasUiState(
            listOf(
                visible(deNota(nota(4, siguienteM = 50)), 120.0, activa = true),
                visible(deNota(nota(2)), 300.0),
                visible(deNota(nota(6)), 450.0),
            ),
        )

    protected val guiando =
        NavegacionUiState(
            control =
                EstadoNavegacion.Guiando(Destino("Sóller", SOLLER), PerfilRuta.MAS_RAPIDA, rutaDePrueba(), null, false),
        )

    protected fun poner(
        navegacion: NavegacionUiState = NavegacionUiState(control = EstadoNavegacion.Libre(0L, 0.0)),
        notas: NotasUiState = this.notas,
        crono: CronoEnCurso? = null,
        alertas: AlertasUiState = AlertasUiState(),
    ) = banco.poner {
        RallyCopilotTheme {
            MapaScreen(
                estado =
                    MapaUiState(
                        cargando = false,
                        datos = datos,
                        estilos = EstilosMapa("{}", null),
                        velocidadKmh = 0,
                    ),
                vista = EstadoVistaMapa(),
                acciones = AccionesMapa(),
                mapa = { _, m -> Box(m.fillMaxSize().testTag(ETIQUETA_MAPA_FALSO)) },
                navegacion = NavegacionEnMapa(navegacion, panelGuiado = panelGuiadoMapa(), notas = notas),
                copiloto =
                    CopilotoEnMapa(
                        copiloto,
                        AccionesCopiloto(onNotasGrandes = {
                            pedidas += it
                            copiloto = copiloto.copy(notasGrandes = it)
                        }),
                    ),
                alertas = alertas,
                crono = crono,
            )
        }
    }

    protected fun bounds(etiqueta: String) = banco.conEtiqueta(etiqueta).boundsInRoot

    protected fun comprobarRoadbookEntero() {
        val raiz = banco.raiz().boundsInRoot
        listOf(ETIQUETA_NOTA_ENORME, ETIQUETA_NOTA_PREFIJO + 1, ETIQUETA_NOTA_PREFIJO + 2).forEach {
            val b = bounds(it)
            assertTrue("$it sale de la pantalla: $b vs $raiz", b.left >= raiz.left && b.right <= raiz.right)
            assertTrue("$it sale de la pantalla: $b vs $raiz", b.top >= raiz.top && b.bottom <= raiz.bottom)
            assertFalse("$it tapa la velocidad", b.overlaps(bounds(ETIQUETA_VELOCIDAD)))
        }
        assertFalse(
            "la nota enorme y la siguiente se pisan",
            bounds(ETIQUETA_NOTA_ENORME).overlaps(
                bounds(ETIQUETA_NOTA_PREFIJO + 1),
            ),
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class NotasGrandesVerticalTest : NotasGrandesBase() {
    @Test
    fun `con notas grandes no hay mapa ni franja pequena y salen la nota enorme y las dos siguientes`() {
        poner()
        assertTrue(banco.hay(ETIQUETA_ROADBOOK))
        assertFalse(banco.hay(ETIQUETA_MAPA_FALSO))
        assertFalse(banco.hay(ETIQUETA_FRANJA_NOTAS))
        val enorme = banco.textos(banco.conEtiqueta(ETIQUETA_NOTA_ENORME))
        assertTrue("DERECHA 4 · 50" in enorme.joinToString(" ").replace(' ', ' '))
        assertEquals(
            "120 m",
            banco.textos(banco.conEtiqueta(ETIQUETA_NOTA_ENORME_DISTANCIA)).single().replace(' ', ' '),
        )
        assertTrue(banco.hay(ETIQUETA_NOTA_PREFIJO + 1))
        assertTrue(banco.hay(ETIQUETA_NOTA_PREFIJO + 2))
    }

    @Test
    fun `la nota enorme es mucho mas alta que las siguientes`() {
        poner()
        assertTrue(bounds(ETIQUETA_NOTA_ENORME).height > 3 * bounds(ETIQUETA_NOTA_PREFIJO + 1).height)
    }

    @Test
    fun `la velocidad sigue en el panel y la pantalla de notas entra entera en vertical`() {
        poner()
        assertTrue(banco.hay(ETIQUETA_VELOCIDAD))
        comprobarRoadbookEntero()
    }

    @Test
    fun `guiando salen el banner de maniobra, la alerta y el HUD del cronometro y todo entra`() {
        poner(
            navegacion = guiando,
            crono = cronoConocido(),
            alertas = AlertasUiState(aviso = AvisoAlerta.Radar(ClaseRadar.FIJO, 500, 50)),
        )
        assertTrue(banco.hay(ETIQUETA_CRONO_HUD))
        assertTrue(banco.hay(ETIQUETA_ALERTA_BANNER))
        assertTrue(bounds(ETIQUETA_CRONO_HUD).bottom <= bounds(ETIQUETA_NOTA_ENORME).top + 1f)
        comprobarRoadbookEntero()
    }

    @Test
    fun `sin notas por delante sale el estado vacio y no una tarjeta`() {
        poner(notas = NotasUiState())
        assertTrue(banco.hay(ETIQUETA_ROADBOOK_VACIO))
        assertFalse(banco.hay(ETIQUETA_NOTA_ENORME))
        assertTrue("Sin notas por delante" in banco.todosLosTextos())
    }

    @Test
    fun `en este modo no hay botones de capas ni de rumbo y si el del copiloto y el de notas grandes`() {
        poner()
        assertFalse(banco.hay(ETIQUETA_CAPAS_BOTON))
        assertFalse(banco.hay(ETIQUETA_ORIENTACION))
        assertTrue(banco.hay(ETIQUETA_MODO_BOTON))
        assertTrue(banco.hay(ETIQUETA_NOTAS_GRANDES_BOTON))
    }

    @Test
    fun `el boton esta marcado y al tocarlo pide volver al mapa, que reaparece`() {
        poner()
        assertEquals("Activadas", banco.descripcionEstado(ETIQUETA_NOTAS_GRANDES_BOTON))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_NOTAS_GRANDES_BOTON))
        assertEquals(listOf(false), pedidas)
        assertTrue(banco.hay(ETIQUETA_MAPA_FALSO))
        assertFalse(banco.hay(ETIQUETA_ROADBOOK))
        assertTrue(banco.hay(ETIQUETA_CAPAS_BOTON))
        assertEquals("Desactivadas", banco.descripcionEstado(ETIQUETA_NOTAS_GRANDES_BOTON))
    }

    @Test
    fun `con el mapa, el boton lo pasa a notas grandes`() {
        copiloto = copiloto.copy(notasGrandes = false)
        poner()
        assertTrue(banco.hay(ETIQUETA_MAPA_FALSO))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_NOTAS_GRANDES_BOTON))
        assertEquals(listOf(true), pedidas)
        assertTrue(banco.hay(ETIQUETA_ROADBOOK))
        assertFalse(banco.hay(ETIQUETA_MAPA_FALSO))
    }

    @Test
    fun `la hoja del copiloto se abre tambien sobre las notas grandes`() {
        poner()
        banco.pulsar(banco.conEtiqueta(ETIQUETA_MODO_BOTON))
        assertTrue(banco.hay(ETIQUETA_RAPIDOS_PANEL))
        assertEquals(4, banco.etiquetas(ETIQUETA_MODO_PREFIJO).size)
    }

    @Test
    fun `el panel del modo de notas grandes no es mas alto que el del mapa`() {
        poner(navegacion = guiando)
        val conNotas = bounds("panel").height
        copiloto = copiloto.copy(notasGrandes = false)
        banco.reposo()
        assertTrue("$conNotas > ${bounds("panel").height}", conNotas <= bounds("panel").height)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
class NotasGrandesHorizontalTest : NotasGrandesBase() {
    @Test
    fun `en horizontal la nota enorme va a la izquierda, las siguientes a la derecha y todo entra`() {
        poner()
        assertTrue(bounds(ETIQUETA_NOTA_ENORME).right <= bounds(ETIQUETA_NOTA_PREFIJO + 1).left)
        comprobarRoadbookEntero()
    }

    @Test
    fun `en horizontal guiando con alerta y cronometro tampoco se sale nada`() {
        poner(
            navegacion = guiando,
            crono = cronoConocido(),
            alertas = AlertasUiState(aviso = AvisoAlerta.Radar(ClaseRadar.FIJO, 500, 50)),
        )
        assertTrue(banco.hay(ETIQUETA_CRONO_HUD))
        comprobarRoadbookEntero()
    }
}
