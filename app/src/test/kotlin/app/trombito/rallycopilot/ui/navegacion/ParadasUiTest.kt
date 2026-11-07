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
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.EstadoRuta
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.mapa.AccionesMapa
import app.trombito.rallycopilot.ui.mapa.ETIQUETA_IR_A_CASA
import app.trombito.rallycopilot.ui.mapa.ETIQUETA_LIMITE
import app.trombito.rallycopilot.ui.mapa.ETIQUETA_VELOCIDAD
import app.trombito.rallycopilot.ui.mapa.ETIQUETA_VIA
import app.trombito.rallycopilot.ui.mapa.EstadoVistaMapa
import app.trombito.rallycopilot.ui.mapa.MapaScreen
import app.trombito.rallycopilot.ui.mapa.MapaUiState
import app.trombito.rallycopilot.ui.navegacion.guiado.ETIQUETA_PIE_FINAL
import app.trombito.rallycopilot.ui.navegacion.guiado.ETIQUETA_PIE_GUIADO
import app.trombito.rallycopilot.ui.navegacion.guiado.ETIQUETA_PIE_HORA
import app.trombito.rallycopilot.ui.navegacion.guiado.ETIQUETA_PIE_PARADA
import app.trombito.rallycopilot.ui.navegacion.guiado.PieGuiado
import app.trombito.rallycopilot.ui.navegacion.guiado.ProximaParada
import app.trombito.rallycopilot.ui.navegacion.guiado.proximaParada
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

private const val ETIQUETA_MAPA_FALSO = "mapa-falso"

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ParadasUiTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val destino = Destino("Sóller", SOLLER)
    private val valldemossa = Destino("Valldemossa", PuntoGeo(39.7106, 2.6213))
    private val inca = Destino("Inca", PuntoGeo(39.7201, 2.9112))
    private val datos = EstadoDatosMapa(instalados = DatosInstalados("2026.10.1", 1_000_000, conSatelite = true))
    private val mapa = MapaUiState(cargando = false, datos = datos, estilos = EstilosMapa("{\"v\":1}", null))

    private var irAqui = 0
    private var anadirDelPunto = 0
    private var cerrarPunto = 0
    private var fijarCasa = 0
    private val elegidos = mutableListOf<Destino>()
    private val quitadas = mutableListOf<Int>()
    private val movidas = mutableListOf<Pair<Int, Int>>()
    private val anadidas = mutableListOf<Destino>()

    private val acciones =
        AccionesNavegacion(
            onIrAqui = { irAqui++ },
            onAnadirParadaDelPunto = { anadirDelPunto++ },
            onCerrarPunto = { cerrarPunto++ },
            onFijarCasa = { fijarCasa++ },
            onElegirDestino = { elegidos += it },
            onAnadirParada = { anadidas += it },
            onQuitarParada = { quitadas += it },
            onMoverParada = { desde, hasta -> movidas += desde to hasta },
        )

    private fun ponerMapa(
        navegacion: NavegacionUiState,
        mapaUi: MapaUiState = mapa,
    ) = banco.poner {
        RallyCopilotTheme {
            MapaScreen(
                estado = mapaUi,
                vista = EstadoVistaMapa(),
                acciones = AccionesMapa(),
                mapa = { _, modificador -> Box(modificador.fillMaxSize().testTag(ETIQUETA_MAPA_FALSO)) },
                navegacion = NavegacionEnMapa(navegacion, acciones),
            )
        }
    }

    private fun previaConParadas(vararg paradas: Destino) =
        NavegacionUiState(
            texto = "Sóller",
            vistaPrevia =
                VistaPrevia(
                    destino,
                    opciones = PerfilRuta.entries.associateWith { OpcionRuta.Lista(rutaDePrueba()) },
                    paradas = paradas.toList(),
                ),
        )

    private fun guiandoConParadas(
        hechas: Int,
        vararg paradas: Destino,
        punto: PuntoElegido? = null,
    ) = NavegacionUiState(
        control =
            EstadoNavegacion.Guiando(
                destino,
                PerfilRuta.MAS_RAPIDA,
                rutaDePrueba(),
                null,
                false,
                paradas = paradas.toList(),
                paradasHechas = hechas,
            ),
        puntoElegido = punto,
    )

    // --- Tarjeta del punto ---

    @Test
    fun `la tarjeta del punto muestra el nombre y dos botones que funcionan`() {
        ponerMapa(NavegacionUiState(puntoElegido = PuntoElegido(SOLLER, "Port de Sóller")))
        val textos = banco.textos(banco.conEtiqueta(ETIQUETA_TARJETA_PUNTO))
        assertTrue("Port de Sóller" in textos)
        assertTrue("Ir aquí" in textos)
        assertTrue("Añadir parada" in textos)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_PUNTO_IR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_PUNTO_PARADA))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_PUNTO_CERRAR))
        assertEquals(1, irAqui)
        assertEquals(1, anadirDelPunto)
        assertEquals(1, cerrarPunto)
    }

    @Test
    fun `la tarjeta del punto tiene el boton Casa que la guarda`() {
        ponerMapa(NavegacionUiState(puntoElegido = PuntoElegido(SOLLER, "Port de Sóller")))
        assertTrue("Casa" in banco.textos(banco.conEtiqueta(ETIQUETA_TARJETA_PUNTO)))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_PUNTO_CASA))
        assertEquals(1, fijarCasa)
    }

    @Test
    fun `con casa fijada la barra ofrece Ir a casa y lleva a ella`() {
        ponerMapa(NavegacionUiState(casa = PALMA))
        assertTrue(banco.hay(ETIQUETA_IR_A_CASA))
        assertTrue("Ir a casa" in banco.textos(banco.conEtiqueta(ETIQUETA_IR_A_CASA)))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_IR_A_CASA))
        assertEquals(listOf(Destino("Casa", PALMA)), elegidos)
    }

    @Test
    fun `sin casa o con la busqueda en marcha no hay Ir a casa`() {
        ponerMapa(NavegacionUiState())
        assertFalse(banco.hay(ETIQUETA_IR_A_CASA))
        ponerMapa(NavegacionUiState(casa = PALMA, texto = "Sól"))
        assertFalse(banco.hay(ETIQUETA_IR_A_CASA))
    }

    @Test
    fun `sin nombre la tarjeta dice punto del mapa y enseña las coordenadas cortas`() {
        ponerMapa(NavegacionUiState(puntoElegido = PuntoElegido(PuntoGeo(39.5696, 2.6502))))
        val textos = banco.textos(banco.conEtiqueta(ETIQUETA_TARJETA_PUNTO))
        assertTrue("Punto del mapa" in textos)
        assertTrue("39.5696, 2.6502" in textos)
    }

    @Test
    fun `sin tarjeta no hay nada del punto`() {
        ponerMapa(NavegacionUiState())
        assertFalse(banco.hay(ETIQUETA_TARJETA_PUNTO))
    }

    @Test
    fun `guiando la tarjeta ofrece ir aqui y anadir parada`() {
        ponerMapa(guiandoConParadas(0, punto = PuntoElegido(SOLLER, "Sóller")))
        assertTrue(banco.hay(ETIQUETA_PUNTO_IR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_PUNTO_IR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_PUNTO_PARADA))
        assertEquals(1, irAqui)
        assertEquals(1, anadirDelPunto)
    }

    @Test
    fun `con el maximo de paradas anadir parada esta desactivado`() {
        val llenas = List(18) { Destino("P$it", PALMA) }
        ponerMapa(previaConParadas(*llenas.toTypedArray()).copy(puntoElegido = PuntoElegido(SOLLER, "Sóller")))
        assertFalse(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_PUNTO_PARADA)))
    }

    // --- Lista de paradas en la vista previa ---

    @Test
    fun `la vista previa lista las paradas numeradas con el final`() {
        ponerMapa(previaConParadas(valldemossa, inca))
        val textos = banco.textos(banco.conEtiqueta(ETIQUETA_PARADAS))
        assertEquals(listOf("1", "Valldemossa", "2", "Inca", "Sóller"), textos)
        assertTrue(banco.hay(ETIQUETA_PARADA_FINAL))
    }

    @Test
    fun `sin paradas la vista previa no pinta la lista`() {
        ponerMapa(previaConParadas())
        assertFalse(banco.hay(ETIQUETA_PARADAS))
    }

    @Test
    fun `quitar una parada avisa con su indice`() {
        ponerMapa(previaConParadas(valldemossa, inca))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_PARADA_QUITAR + 1))
        assertEquals(listOf(1), quitadas)
    }

    @Test
    fun `subir y bajar mueven la parada una posicion`() {
        ponerMapa(previaConParadas(valldemossa, inca))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_PARADA_SUBIR + 1))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_PARADA_BAJAR + 0))
        assertEquals(listOf(1 to 0, 0 to 1), movidas)
    }

    @Test
    fun `la primera no se puede subir ni la ultima bajar`() {
        ponerMapa(previaConParadas(valldemossa, inca))
        assertFalse(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_PARADA_SUBIR + 0)))
        assertFalse(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_PARADA_BAJAR + 1)))
        assertTrue(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_PARADA_SUBIR + 1)))
    }

    // --- Resultados de búsqueda con ruta ---

    @Test
    fun `con una ruta cada resultado ofrece anadir como parada`() {
        val items =
            listOf(ResultadoBusqueda("Inca", "pueblo", null, inca.punto))
        ponerMapa(previaConParadas().copy(texto = "Inc", busqueda = BusquedaDestino.Resultados(items)))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_RESULTADO_PARADA + 0))
        assertEquals(listOf(Destino("Inca", inca.punto)), anadidas)
    }

    @Test
    fun `sin ruta los resultados no ofrecen anadir como parada`() {
        val items =
            listOf(ResultadoBusqueda("Inca", "pueblo", null, inca.punto))
        ponerMapa(NavegacionUiState(texto = "Inc", busqueda = BusquedaDestino.Resultados(items)))
        assertTrue(banco.hay(ETIQUETA_RESULTADO + 0))
        assertFalse(banco.hay(ETIQUETA_RESULTADO_PARADA + 0))
    }

    // --- Guiado ---

    @Test
    fun `guiando el banner dice parada 1 de 3 y abre la lista al tocarlo`() {
        ponerMapa(guiandoConParadas(1, valldemossa, inca))
        assertTrue("Parada 2 de 3" in banco.textos(banco.conEtiqueta(ETIQUETA_BANNER_PARADA)))
        assertFalse(banco.hay(ETIQUETA_PARADAS))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_BANNER_PARADA))
        assertEquals(
            listOf("2", "Valldemossa", "3", "Inca", "Sóller"),
            banco.textos(banco.conEtiqueta(ETIQUETA_PARADAS)),
        )
        banco.pulsar(banco.conEtiqueta(ETIQUETA_PARADA_QUITAR + 0))
        assertEquals(listOf(0), quitadas)
    }

    @Test
    fun `sin paradas pendientes no hay banner de parada`() {
        ponerMapa(guiandoConParadas(2))
        assertFalse(banco.hay(ETIQUETA_BANNER_PARADA))
    }

    private fun progreso() =
        EstadoRuta(
            progresoM = 100.0,
            distanciaRestanteM = 12_400.0,
            tiempoRestanteS = 3900.0,
            llegadaMs = 1_700_000_000_000L,
            maniobraActual = null,
            distanciaAManiobraM = 352.0,
            maniobraSiguiente = null,
            distanciaALaRutaM = 3.0,
            llegado = false,
            paradasAlcanzadas = 1,
            distanciaAProximaParadaM = 2_300.0,
            tiempoAProximaParadaS = 240.0,
        )

    @Test
    fun `la hora de llegada a la proxima parada sale de ahora mas el tiempo hasta ella`() {
        val estado =
            EstadoNavegacion.Guiando(
                destino,
                PerfilRuta.MAS_RAPIDA,
                rutaDePrueba(),
                progreso(),
                false,
                paradas = listOf(valldemossa),
                paradasHechas = 1,
            )
        val proxima = proximaParada(estado, progreso())!!
        assertEquals(2, proxima.numero)
        assertEquals(1_700_000_000_000L - 3_900_000L + 240_000L, proxima.llegadaMs)
        assertEquals(2_300.0, proxima.distanciaM, 0.0)
        // Recalculando no hay datos fiables hasta la parada.
        assertNull(proximaParada(estado.copy(recalculando = true), progreso()))
        assertNull(proximaParada(estado.copy(paradas = emptyList()), progreso()))
    }

    @Test
    fun `el pie con proxima parada enseña sus datos y, mas pequeno, los del final`() {
        val proxima = ProximaParada(2, 1_699_996_340_000L, 240.0, 2_300.0)
        banco.poner {
            RallyCopilotTheme { PieGuiado(progreso(), Modifier, ZoneId.of("UTC"), proxima) }
        }
        assertTrue(banco.hay(ETIQUETA_PIE_PARADA))
        assertEquals(listOf("2"), banco.textos(banco.conEtiqueta(ETIQUETA_PIE_PARADA)))
        assertEquals(listOf("21:12"), banco.textos(banco.conEtiqueta(ETIQUETA_PIE_HORA)))
        val final = banco.textos(banco.conEtiqueta(ETIQUETA_PIE_FINAL)).single()
        assertTrue(final, final.startsWith("Final"))
        assertTrue(final, "22:13" in final && "1 h 05 min" in final && "12,4 km" in final)
    }

    @Test
    fun `el pie sin proxima parada es el de siempre`() {
        banco.poner { RallyCopilotTheme { PieGuiado(progreso(), Modifier, ZoneId.of("UTC")) } }
        assertTrue(banco.hay(ETIQUETA_PIE_GUIADO))
        assertFalse(banco.hay(ETIQUETA_PIE_PARADA))
        assertFalse(banco.hay(ETIQUETA_PIE_FINAL))
        assertEquals(listOf("22:13"), banco.textos(banco.conEtiqueta(ETIQUETA_PIE_HORA)))
    }

    @Test
    fun `el pie enseña la hora de llegada en hora de Madrid, con horario de invierno y de verano`() {
        val madrid = ZoneId.of("Europe/Madrid")
        // 2023-11-14T22:13:20Z: CET (UTC+1).
        banco.poner { RallyCopilotTheme { PieGuiado(progreso(), Modifier, madrid) } }
        assertEquals(listOf("23:13"), banco.textos(banco.conEtiqueta(ETIQUETA_PIE_HORA)))
    }

    @Test
    fun `en verano la hora de llegada de Madrid es la de UTC mas dos horas`() {
        val madrid = ZoneId.of("Europe/Madrid")
        // 2024-07-03T09:46:40Z: CEST (UTC+2).
        val verano = progreso().copy(llegadaMs = 1_720_000_000_000L)
        banco.poner { RallyCopilotTheme { PieGuiado(verano, Modifier, madrid) } }
        assertEquals(listOf("11:46"), banco.textos(banco.conEtiqueta(ETIQUETA_PIE_HORA)))
    }

    // --- Hoja inferior y tarjetas que la sustituyen ---

    private val conVia = mapa.copy(velocidadKmh = 0, via = ViaActual("Carretera", "Ma-1110", 50))

    @Test
    fun `parado sin planificar el panel es el completo con la placa de la via`() {
        ponerMapa(NavegacionUiState(), conVia)
        assertTrue(banco.hay(ETIQUETA_VIA))
    }

    @Test
    fun `con la tarjeta de un punto la tarjeta sustituye a la hoja y la pildora sigue`() {
        ponerMapa(NavegacionUiState(puntoElegido = PuntoElegido(SOLLER, "Sóller")), conVia)
        assertFalse(banco.hay("panel"))
        assertTrue(banco.hay(ETIQUETA_TARJETA_PUNTO))
        assertTrue(banco.hay(ETIQUETA_VELOCIDAD))
        assertTrue(banco.hay(ETIQUETA_LIMITE))
    }

    @Test
    fun `con la vista previa la hoja se retira y se ve la vista previa`() {
        ponerMapa(previaConParadas(), conVia)
        assertFalse(banco.hay("panel"))
        assertTrue(banco.hay(ETIQUETA_VISTA_PREVIA))
    }

    @Test
    fun `con la busqueda abierta la hoja sigue abajo`() {
        ponerMapa(NavegacionUiState(texto = "Sól", busqueda = BusquedaDestino.Resultados(emptyList())), conVia)
        assertTrue(banco.hay("panel"))
        assertTrue(banco.hay(ETIQUETA_VIA))
    }

    @Test
    fun `la placa de la via solo sale si la via coincide con el camino de la posicion`() {
        ponerMapa(NavegacionUiState(), conVia.copy(viaCoincide = false))
        assertFalse(banco.hay(ETIQUETA_VIA))
    }

    @Test
    fun `al empezar la ruta la hoja es la del guiado y lleva la X de terminar`() {
        ponerMapa(guiandoConParadas(0, valldemossa), conVia)
        assertNotNull(banco.conEtiqueta("panel"))
        assertTrue(banco.hay(ETIQUETA_TERMINAR))
    }
}
