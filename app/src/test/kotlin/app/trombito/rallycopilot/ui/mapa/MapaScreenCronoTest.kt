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
import app.trombito.rallycopilot.ui.alertas.AlertasUiState
import app.trombito.rallycopilot.ui.alertas.AvisoAlerta
import app.trombito.rallycopilot.ui.alertas.ClaseRadar
import app.trombito.rallycopilot.ui.alertas.ETIQUETA_ALERTA_BANNER
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.navegacion.NavegacionEnMapa
import app.trombito.rallycopilot.ui.navegacion.NavegacionUiState
import app.trombito.rallycopilot.ui.navegacion.SOLLER
import app.trombito.rallycopilot.ui.navegacion.notas.ETIQUETA_FRANJA_NOTAS
import app.trombito.rallycopilot.ui.navegacion.notas.NotasUiState
import app.trombito.rallycopilot.ui.navegacion.notas.deNota
import app.trombito.rallycopilot.ui.navegacion.notas.nota
import app.trombito.rallycopilot.ui.navegacion.notas.visible
import app.trombito.rallycopilot.ui.navegacion.panelGuiadoMapa
import app.trombito.rallycopilot.ui.navegacion.rutaDePrueba
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import app.trombito.rallycopilot.ui.tramos.ETIQUETA_CRONO_HUD
import app.trombito.rallycopilot.ui.tramos.ETIQUETA_CRONO_META
import app.trombito.rallycopilot.ui.tramos.ETIQUETA_CRONO_MINI
import app.trombito.rallycopilot.ui.tramos.cronoConocido
import app.trombito.rallycopilot.ui.tramos.cronoSinReferencia
import app.trombito.rallycopilot.ui.tramos.cronoSinTramo
import app.trombito.rallycopilot.ui.tramos.cronoTerminado
import app.trombito.rallycopilot.ui.tramos.sector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El cronómetro montado en [MapaScreen]: HUD arriba (con tramo conocido) o cronómetro pequeño en el panel. */
abstract class MapaScreenCronoBase {
    @get:Rule val limpieza = LimpiezaCompose()

    protected val banco = BancoCompose()

    private val datos = EstadoDatosMapa(instalados = DatosInstalados("2026.10.1", 1_000_000, conSatelite = true))
    private val mapa = MapaUiState(cargando = false, datos = datos, estilos = EstilosMapa("{\"v\":1}", null))

    protected val libre = NavegacionUiState(control = EstadoNavegacion.Libre(0L, 0.0))

    protected fun guiando() =
        NavegacionUiState(
            control =
                EstadoNavegacion.Guiando(Destino("Sóller", SOLLER), PerfilRuta.MAS_RAPIDA, rutaDePrueba(), null, false),
        )

    protected val notas =
        NotasUiState(listOf(visible(deNota(nota(4, siguienteM = 50))), visible(deNota(nota(2)), 300.0)))
    protected val radar = AlertasUiState(aviso = AvisoAlerta.Radar(ClaseRadar.FIJO, 500, 50))

    protected fun poner(
        navegacion: NavegacionUiState,
        crono: CronoEnCurso?,
        alertas: AlertasUiState = AlertasUiState(),
    ) = banco.poner {
        RallyCopilotTheme {
            MapaScreen(
                estado = mapa.copy(velocidadKmh = 63),
                vista = EstadoVistaMapa(),
                acciones = AccionesMapa(),
                mapa = { _, modificador -> Box(modificador.fillMaxSize().testTag("mapa-falso")) },
                navegacion = NavegacionEnMapa(navegacion, panelGuiado = panelGuiadoMapa(), notas = notas),
                alertas = alertas,
                crono = crono,
            )
        }
    }

    protected fun bounds(etiqueta: String) = banco.conEtiqueta(etiqueta).boundsInRoot
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class MapaScreenCronoVerticalTest : MapaScreenCronoBase() {
    @Test
    fun `guiando con tramo conocido el HUD va bajo la franja de notas y el banner de alerta`() {
        poner(guiando(), cronoConocido(parciales = listOf(sector(1, -300L))), radar)
        val hud = bounds(ETIQUETA_CRONO_HUD)
        assertTrue("bajo la franja", hud.top >= bounds(ETIQUETA_FRANJA_NOTAS).bottom)
        assertTrue("bajo el banner de alerta", hud.top >= bounds(ETIQUETA_ALERTA_BANNER).bottom)
        assertTrue("sobre el panel de velocidad", hud.bottom <= bounds("panel").top)
        assertFalse("con HUD no hay cronómetro pequeño", banco.hay(ETIQUETA_CRONO_MINI))
    }

    @Test
    fun `en modo libre el HUD sale bajo las notas`() {
        poner(libre, cronoConocido())
        assertTrue(bounds(ETIQUETA_CRONO_HUD).top >= bounds(ETIQUETA_FRANJA_NOTAS).bottom)
    }

    @Test
    fun `con un tramo nuevo solo hay cronometro pequeno en el panel junto a la velocidad`() {
        poner(guiando(), cronoSinReferencia())
        assertFalse(banco.hay(ETIQUETA_CRONO_HUD))
        val mini = bounds(ETIQUETA_CRONO_MINI)
        val panel = bounds("panel")
        assertTrue("dentro del panel: $mini vs $panel", mini.top >= panel.top && mini.bottom <= panel.bottom)
        assertTrue("bajo la velocidad", mini.top >= bounds(ETIQUETA_VELOCIDAD).bottom)
    }

    @Test
    fun `sin tramo el cronometro pequeno no toca la franja de notas`() {
        poner(libre, cronoSinTramo())
        assertTrue(banco.hay(ETIQUETA_CRONO_MINI))
        assertFalse(banco.hay(ETIQUETA_CRONO_HUD))
        assertTrue(bounds(ETIQUETA_CRONO_MINI).top > bounds(ETIQUETA_FRANJA_NOTAS).bottom)
    }

    @Test
    fun `sin cronometro el panel no lleva nada extra`() {
        poner(libre, null)
        assertFalse(banco.hay(ETIQUETA_CRONO_MINI))
        assertFalse(banco.hay(ETIQUETA_CRONO_HUD))
    }

    @Test
    fun `en la meta sale la tarjeta y el cronometro pequeno se retira`() {
        poner(
            guiando(),
            cronoTerminado(ResultadoCrono(148_100L, -2_300L, nuevoMejor = true, mejorAnteriorMs = 150_400L)),
        )
        assertTrue(banco.hay(ETIQUETA_CRONO_META))
        assertFalse(banco.hay(ETIQUETA_CRONO_MINI))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w891dp-h411dp-xxhdpi")
class MapaScreenCronoHorizontalTest : MapaScreenCronoBase() {
    @Test
    fun `en horizontal el HUD compacto cabe en la zona del mapa, bajo las notas`() {
        poner(guiando(), cronoConocido(parciales = listOf(sector(1, -300L), sector(2, 1_200L))), radar)
        val hud = bounds(ETIQUETA_CRONO_HUD)
        val panel = bounds("panel")
        assertTrue("no pasa al panel lateral: $hud vs $panel", hud.right <= panel.left)
        assertTrue("bajo la franja", hud.top >= bounds(ETIQUETA_FRANJA_NOTAS).bottom)
        assertTrue("es bajo: $hud", hud.height < 411 * 3 * 0.25f)
        assertTrue("cabe en la pantalla", hud.bottom <= 411 * 3f)
    }

    @Test
    fun `en horizontal el cronometro pequeno cabe en el panel lateral sin pisar los botones`() {
        poner(guiando(), cronoSinTramo())
        val mini = bounds(ETIQUETA_CRONO_MINI)
        val panel = bounds("panel")
        assertTrue("dentro del panel: $mini vs $panel", mini.left >= panel.left && mini.right <= panel.right)
        assertTrue("dentro del alto: $mini vs $panel", mini.top >= panel.top && mini.bottom <= panel.bottom)
        listOf(ETIQUETA_CAPAS_BOTON, ETIQUETA_ORIENTACION).forEach {
            assertFalse("$it no pisa el cronómetro", bounds(it).overlaps(mini))
        }
    }

    @Test
    fun `en horizontal la tarjeta de meta compacta cabe en la zona del mapa`() {
        poner(
            guiando(),
            cronoTerminado(ResultadoCrono(148_100L, -2_300L, nuevoMejor = true, mejorAnteriorMs = 150_400L)),
        )
        val meta = bounds(ETIQUETA_CRONO_META)
        assertTrue(meta.right <= bounds("panel").left)
        assertTrue("es baja: $meta", meta.height < 411 * 3 * 0.3f)
    }
}
