package app.trombito.rallycopilot.ui.navegacion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.mapa.AccionesMapa
import app.trombito.rallycopilot.ui.mapa.EstadoVistaMapa
import app.trombito.rallycopilot.ui.mapa.MapaScreen
import app.trombito.rallycopilot.ui.mapa.MapaUiState
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

/** Base: la lupa del panel y la búsqueda encima del mapa mientras se guía, montadas en [MapaScreen]. */
abstract class BusquedaGuiandoBase {
    @get:Rule val limpieza = LimpiezaCompose()

    protected val banco = BancoCompose()
    protected val destino = Destino("Sóller", SOLLER)
    protected val valldemossa = Destino("Valldemossa", PALMA)
    protected var abierta = 0
    protected var cerrada = 0
    protected val elegidos = mutableListOf<Destino>()
    protected val paradas = mutableListOf<Destino>()

    private val datos = EstadoDatosMapa(instalados = DatosInstalados("2026.10.1", 1_000_000, conSatelite = true))
    private val mapa =
        MapaUiState(cargando = false, datos = datos, estilos = EstilosMapa("{}", null), velocidadKmh = 60)

    private val acciones =
        AccionesNavegacion(
            onAbrirBusqueda = { abierta++ },
            onCerrarBusqueda = { cerrada++ },
            onElegirDestino = { elegidos += it },
            onAnadirParada = { paradas += it },
        )

    protected fun guiando(
        buscando: Boolean = false,
        busqueda: BusquedaDestino = BusquedaDestino.Inactiva,
    ) = NavegacionUiState(
        texto = if (buscando) "Valld" else "",
        busqueda = busqueda,
        control = EstadoNavegacion.Guiando(destino, PerfilRuta.MAS_RAPIDA, rutaDePrueba(), null, false),
        buscando = buscando,
    )

    protected fun resultados() =
        BusquedaDestino.Resultados(listOf(ResultadoBusqueda("Valldemossa", "pueblo", "Valldemossa", PALMA)))

    protected fun poner(navegacion: NavegacionUiState) =
        banco.poner {
            RallyCopilotTheme {
                MapaScreen(
                    estado = mapa,
                    vista = EstadoVistaMapa(),
                    acciones = AccionesMapa(),
                    mapa = { _, m -> Box(m.fillMaxSize().testTag(ETIQUETA_MAPA_FALSO)) },
                    navegacion = NavegacionEnMapa(navegacion, acciones),
                )
            }
        }

    @Test
    fun `guiando hay una lupa en el panel y abre la busqueda`() {
        poner(guiando())
        val lupa = banco.conEtiqueta(ETIQUETA_BUSCAR_GUIANDO)
        assertEquals("Buscar un lugar", banco.descripcion(lupa))
        assertTrue("Objetivo táctil de 48 dp", lupa.size.width >= 48 * 3 && lupa.size.height >= 48 * 3)
        assertFalse(banco.hay(ETIQUETA_BUSQUEDA_GUIANDO))
        banco.pulsar(lupa)
        assertEquals(1, abierta)
    }

    @Test
    fun `en reposo no hay lupa`() {
        poner(NavegacionUiState())
        assertFalse(banco.hay(ETIQUETA_BUSCAR_GUIANDO))
    }

    @Test
    fun `con la busqueda abierta cada resultado ofrece anadir parada e ir aqui y la X la cierra`() {
        poner(guiando(buscando = true, busqueda = resultados()))
        assertTrue(banco.hay(ETIQUETA_BUSQUEDA_GUIANDO))
        assertTrue(banco.hay(ETIQUETA_BUSCADOR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_RESULTADO_PARADA + "0"))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_RESULTADO_IR + "0"))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_RESULTADO + "0"))
        assertEquals(listOf(valldemossa), paradas)
        assertEquals(listOf(valldemossa, valldemossa), elegidos)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_BUSQUEDA_GUIANDO_CERRAR))
        assertEquals(1, cerrada)
    }

    @Test
    fun `sin la busqueda abierta guiando no hay barra ni resultados`() {
        poner(guiando(buscando = false, busqueda = resultados()))
        assertFalse(banco.hay(ETIQUETA_BUSCADOR))
        assertFalse(banco.hay(ETIQUETA_RESULTADOS))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BusquedaGuiandoVerticalTest : BusquedaGuiandoBase()

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
class BusquedaGuiandoHorizontalTest : BusquedaGuiandoBase()
