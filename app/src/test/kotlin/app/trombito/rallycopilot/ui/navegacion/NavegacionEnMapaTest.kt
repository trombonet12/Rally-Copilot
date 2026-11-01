package app.trombito.rallycopilot.ui.navegacion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.TipoAviso
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ETIQUETA_VOLVER
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.mapa.AccionesMapa
import app.trombito.rallycopilot.ui.mapa.EstadoVistaMapa
import app.trombito.rallycopilot.ui.mapa.MapaScreen
import app.trombito.rallycopilot.ui.mapa.MapaUiState
import app.trombito.rallycopilot.ui.navegacion.guiado.ETIQUETA_LLEGADA_CERRAR
import app.trombito.rallycopilot.ui.navegacion.guiado.ETIQUETA_TARJETA_LLEGADA
import app.trombito.rallycopilot.ui.navegacion.notas.ETIQUETA_FRANJA_NOTAS
import app.trombito.rallycopilot.ui.navegacion.notas.ETIQUETA_NOTA_PREFIJO
import app.trombito.rallycopilot.ui.navegacion.notas.NotasUiState
import app.trombito.rallycopilot.ui.navegacion.notas.deAviso
import app.trombito.rallycopilot.ui.navegacion.notas.deNota
import app.trombito.rallycopilot.ui.navegacion.notas.nota
import app.trombito.rallycopilot.ui.navegacion.notas.visible
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
private const val ETIQUETA_PANEL_GUIADO = "panel-guiado-de-prueba"

/** Prueba la navegación (búsqueda, vista previa, terminar y hueco del panel) montada en [MapaScreen]. */
abstract class NavegacionEnMapaBase {
    @get:Rule val limpieza = LimpiezaCompose()

    protected val banco = BancoCompose()
    protected val destino = Destino("Sóller", SOLLER)
    private val datos = EstadoDatosMapa(instalados = DatosInstalados("2026.10.1", 1_000_000, conSatelite = true))
    private val mapa = MapaUiState(cargando = false, datos = datos, estilos = EstilosMapa("{\"v\":1}", null))

    protected val textoElegido = mutableListOf<String>()
    protected val elegidos = mutableListOf<Destino>()
    protected val perfiles = mutableListOf<PerfilRuta>()
    protected var empezadas = 0
    protected var terminadas = 0
    protected var canceladas = 0
    protected var panelesGuiado = 0
    protected var seguidas = 0
    protected var descartadas = 0

    private val acciones =
        AccionesNavegacion(
            onTexto = { textoElegido += it },
            onElegirDestino = { elegidos += it },
            onCancelar = { canceladas++ },
            onPerfil = { perfiles += it },
            onEmpezar = { empezadas++ },
            onTerminar = { terminadas++ },
            onSeguir = { seguidas++ },
            onDescartarSeguir = { descartadas++ },
        )

    private val panelGuiado: PanelGuiado = { _, _, _, _ ->
        panelesGuiado++
        Box(Modifier.fillMaxSize().testTag(ETIQUETA_PANEL_GUIADO)) { Text("banner") }
    }

    protected fun poner(
        estado: NavegacionUiState,
        notas: NotasUiState = NotasUiState(),
    ) = banco.poner {
        RallyCopilotTheme {
            MapaScreen(
                estado = mapa,
                vista = EstadoVistaMapa(),
                acciones = AccionesMapa(),
                mapa = { _, modificador -> Box(modificador.fillMaxSize().testTag(ETIQUETA_MAPA_FALSO)) },
                navegacion = NavegacionEnMapa(estado, acciones, panelGuiado, notas),
            )
        }
    }

    protected fun vistaPrevia(
        vararg opciones: Pair<PerfilRuta, OpcionRuta>,
        perfil: PerfilRuta = PerfilRuta.MAS_RAPIDA,
    ) = NavegacionUiState(
        texto = "Sóller",
        vistaPrevia =
            VistaPrevia(
                destino,
                perfil,
                PerfilRuta.entries.associateWith { OpcionRuta.Cargando } + opciones.toMap(),
            ),
    )

    protected fun guiando() =
        NavegacionUiState(
            control = EstadoNavegacion.Guiando(destino, PerfilRuta.MAS_RAPIDA, rutaDePrueba(), null, false),
        )

    protected val dosRutas =
        arrayOf(
            PerfilRuta.MAS_RAPIDA to OpcionRuta.Lista(rutaDePrueba(18_400.0, 1_380.0)),
            PerfilRuta.SINUOSA to OpcionRuta.Lista(rutaDePrueba(24_000.0, 4_500.0)),
        )
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class NavegacionEnMapaVerticalTest : NavegacionEnMapaBase() {
    @Test
    fun `en reposo hay barra de destino y ningun boton de terminar ni de volante`() {
        poner(NavegacionUiState())
        assertTrue(banco.hay(ETIQUETA_BUSCADOR))
        assertTrue("¿A dónde?" in banco.todosLosTextos())
        assertFalse(banco.hay(ETIQUETA_VISTA_PREVIA))
        assertFalse(banco.hay(ETIQUETA_TERMINAR))
        assertFalse(banco.hay(ETIQUETA_RESULTADOS))
        assertFalse("Conducir sin destino" in banco.nodos().mapNotNull { banco.descripcion(it) })
    }

    @Test
    fun `teclear en la barra avisa al ViewModel`() {
        poner(NavegacionUiState())
        val campo = banco.conEtiqueta(ETIQUETA_BUSCADOR)
        campo.config
            .getOrNull(SemanticsActions.SetText)!!
            .action!!
            .invoke(AnnotatedString("Sól"))
        assertEquals(listOf("Sól"), textoElegido)
    }

    @Test
    fun `con texto aparece borrar y cancela`() {
        poner(NavegacionUiState(texto = "Sól"))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_LIMPIAR))
        assertEquals(1, canceladas)
    }

    @Test
    fun `los resultados muestran nombre y categoria con municipio y se pueden elegir`() {
        val items =
            listOf(
                ResultadoBusqueda("Sóller", "pueblo", "Sóller", SOLLER),
                ResultadoBusqueda("Cala Millor", "playa", null, PALMA),
            )
        poner(NavegacionUiState(texto = "S", busqueda = BusquedaDestino.Resultados(items)))
        val textos = banco.textos(banco.conEtiqueta(ETIQUETA_RESULTADOS))
        assertTrue("Sóller" in textos)
        assertTrue("pueblo · Sóller" in textos)
        assertTrue("playa" in textos)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_RESULTADO + "1"))
        assertEquals(listOf(Destino("Cala Millor", PALMA)), elegidos)
    }

    @Test
    fun `sin resultados lo dice`() {
        poner(NavegacionUiState(texto = "zz", busqueda = BusquedaDestino.Resultados(emptyList())))
        assertTrue("Sin resultados en Mallorca" in banco.textos(banco.conEtiqueta(ETIQUETA_RESULTADOS)))
    }

    @Test
    fun `un enlace corto en curso muestra resolviendo`() {
        poner(NavegacionUiState(texto = "https://maps.app.goo.gl/x", busqueda = BusquedaDestino.Resolviendo))
        assertEquals("Resolviendo enlace…", banco.textos(banco.conEtiqueta(ETIQUETA_ENLACE_CORTO)).single())
    }

    @Test
    fun `un enlace corto sin red avisa de que hace falta internet`() {
        poner(NavegacionUiState(texto = "https://maps.app.goo.gl/x", busqueda = BusquedaDestino.SinRed))
        assertEquals(
            "Sin conexión: los enlaces cortos necesitan internet; pega el enlace completo o las coordenadas",
            banco.textos(banco.conEtiqueta(ETIQUETA_ENLACE_CORTO)).single(),
        )
    }

    @Test
    fun `un enlace corto que falla dice que no se pudo abrir`() {
        poner(NavegacionUiState(texto = "https://maps.app.goo.gl/x", busqueda = BusquedaDestino.EnlaceNoAbierto))
        assertEquals("No se pudo abrir el enlace", banco.textos(banco.conEtiqueta(ETIQUETA_ENLACE_CORTO)).single())
    }

    @Test
    fun `unas coordenadas se ofrecen como ubicacion pegada`() {
        val pegado = Destino("39.76670, 2.71540", SOLLER)
        poner(NavegacionUiState(busqueda = BusquedaDestino.Ubicacion(pegado, sinNombre = true)))
        val textos = banco.textos(banco.conEtiqueta(ETIQUETA_RESULTADOS))
        assertTrue("Ubicación pegada" in textos)
        assertTrue("39.76670, 2.71540" in textos)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_RESULTADO + "0"))
        assertEquals(listOf(pegado), elegidos)
    }

    @Test
    fun `la vista previa muestra tiempo y distancia de las dos rutas`() {
        poner(vistaPrevia(*dosRutas))
        val rapida = banco.textos(banco.conEtiqueta(ETIQUETA_RUTA_RAPIDA))
        val sinuosa = banco.textos(banco.conEtiqueta(ETIQUETA_RUTA_SINUOSA))
        assertEquals(listOf("23 min", "18,4 km"), rapida)
        assertEquals(listOf("1 h 15 min", "24,0 km"), sinuosa)
    }

    @Test
    fun `las opciones llevan icono sin rotulos y se describen para TalkBack`() {
        poner(vistaPrevia(*dosRutas))
        val rapida = banco.descripcion(banco.conEtiqueta(ETIQUETA_RUTA_RAPIDA))
        assertEquals("Ruta más rápida: 23 min, 18,4 km", rapida)
        assertTrue(banco.descripcion(banco.conEtiqueta(ETIQUETA_RUTA_SINUOSA))!!.startsWith("Ruta sinuosa"))
        listOf(ETIQUETA_EMPEZAR, ETIQUETA_CANCELAR).forEach {
            val nodo = banco.conEtiqueta(it)
            assertTrue("$it sin texto", banco.textos(nodo).isEmpty())
            assertTrue("$it con descripcion", !banco.descripcion(nodo).isNullOrBlank())
            assertTrue("$it >= 48 dp", nodo.size.width >= 48 * 3 && nodo.size.height >= 48 * 3)
        }
    }

    @Test
    fun `elegir una ruta empezar y cancelar llaman a las acciones`() {
        poner(vistaPrevia(*dosRutas))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_RUTA_SINUOSA))
        assertEquals(listOf(PerfilRuta.SINUOSA), perfiles)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_EMPEZAR))
        assertEquals(1, empezadas)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_CANCELAR))
        assertEquals(1, canceladas)
    }

    @Test
    fun `mientras calcula no se puede empezar`() {
        poner(vistaPrevia())
        assertFalse(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_EMPEZAR)))
    }

    @Test
    fun `una ruta sin datos o con error lo dice y no se puede elegir`() {
        poner(vistaPrevia(PerfilRuta.MAS_RAPIDA to OpcionRuta.SinDatos, PerfilRuta.SINUOSA to OpcionRuta.Error("x")))
        assertTrue("Sin datos de rutas" in banco.textos(banco.conEtiqueta(ETIQUETA_RUTA_RAPIDA)))
        assertTrue("No se pudo calcular" in banco.textos(banco.conEtiqueta(ETIQUETA_RUTA_SINUOSA)))
        assertFalse(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_EMPEZAR)))
    }

    @Test
    fun `sin fijacion GPS explica que espera`() {
        val estado = vistaPrevia().let { it.copy(vistaPrevia = it.vistaPrevia!!.copy(sinPosicion = true)) }
        poner(estado)
        assertTrue(banco.hay(ETIQUETA_SIN_POSICION))
        assertFalse(banco.hay(ETIQUETA_RUTA_RAPIDA))
    }

    @Test
    fun `guiando hay bandera de terminar y el hueco del panel, sin barra de destino`() {
        poner(guiando())
        assertTrue(banco.hay(ETIQUETA_TERMINAR))
        assertTrue(banco.hay(ETIQUETA_PANEL_GUIADO))
        assertFalse(banco.hay(ETIQUETA_BUSCADOR))
        val bandera = banco.conEtiqueta(ETIQUETA_TERMINAR)
        assertTrue(banco.textos(bandera).isEmpty())
        assertEquals("Terminar", banco.descripcion(bandera))
        assertTrue(bandera.size.width >= 48 * 3 && bandera.size.height >= 48 * 3)
        banco.pulsar(bandera)
        assertEquals(1, terminadas)
    }

    @Test
    fun `la bandera de terminar va en el panel inferior a la derecha de la velocidad`() {
        poner(guiando())
        val panel = banco.conEtiqueta("panel").boundsInRoot
        val bandera = banco.conEtiqueta(ETIQUETA_TERMINAR).boundsInRoot
        val velocidad = banco.conEtiqueta("panel-velocidad").boundsInRoot
        assertTrue(
            "bandera dentro del panel: $bandera vs $panel",
            bandera.top >= panel.top && bandera.bottom <= panel.bottom,
        )
        assertTrue("bandera a la derecha de la velocidad", bandera.left >= velocidad.right)
    }

    @Test
    fun `la barra de destino ocupa todo el ancho sin flecha de volver`() {
        poner(NavegacionUiState())
        val barra = banco.conEtiqueta(ETIQUETA_BUSCADOR).boundsInRoot
        // Llega casi hasta el borde derecho: ya no deja sitio a la columna de controles de antes (56 dp).
        assertTrue("barra a todo el ancho: $barra", barra.right > banco.raiz().boundsInRoot.right - 24 * 3f)
        assertFalse(banco.hay(ETIQUETA_VOLVER))
    }

    @Test
    fun `en modo libre hay barra de destino pero ni bandera de terminar ni panel de guiado`() {
        poner(NavegacionUiState(control = EstadoNavegacion.Libre(0L, 0.0)))
        assertTrue(banco.hay(ETIQUETA_BUSCADOR))
        assertFalse(banco.hay(ETIQUETA_TERMINAR))
        assertFalse(banco.hay(ETIQUETA_PANEL_GUIADO))
        assertEquals(0, panelesGuiado)
    }

    @Test
    fun `en modo libre se puede ver la vista previa de rutas y empezar`() {
        poner(vistaPrevia(*dosRutas).copy(control = EstadoNavegacion.Libre(0L, 0.0)))
        assertTrue(banco.hay(ETIQUETA_VISTA_PREVIA))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_EMPEZAR))
        assertEquals(1, empezadas)
    }

    @Test
    fun `en reposo el hueco del panel no se pinta`() {
        poner(NavegacionUiState())
        assertFalse(banco.hay(ETIQUETA_PANEL_GUIADO))
        assertEquals(0, panelesGuiado)
    }

    @Test
    fun `la llegada se avisa y se cierra con la bandera`() {
        poner(NavegacionUiState(control = EstadoNavegacion.Llegada(destino)))
        val textos = banco.textos(banco.conEtiqueta(ETIQUETA_TARJETA_LLEGADA))
        assertTrue("Has llegado" in textos && "Sóller" in textos)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_LLEGADA_CERRAR))
        assertEquals(1, terminadas)
    }

    @Test
    fun `un error de la conduccion se muestra y se puede cerrar`() {
        poner(NavegacionUiState(control = EstadoNavegacion.Error("Se ha perdido la señal GPS.")))
        assertTrue("Se ha perdido la señal GPS." in banco.textos(banco.conEtiqueta(ETIQUETA_ERROR)))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ERROR_CERRAR))
        assertEquals(1, terminadas)
    }

    private val notas =
        NotasUiState(
            listOf(
                visible(deNota(nota(4, modificadores = setOf(Modificador.LARGA), siguienteM = 50))),
                visible(deAviso(TipoAviso.PUENTE), 300.0),
            ),
        )

    private val libre = NavegacionUiState(control = EstadoNavegacion.Libre(0L, 0.0))

    @Test
    fun `en modo libre con notas la franja ocupa el sitio de la barra de destino`() {
        poner(libre, notas)
        val franja = banco.conEtiqueta(ETIQUETA_FRANJA_NOTAS).boundsInRoot
        assertFalse("la barra se esconde mientras hay notas", banco.hay(ETIQUETA_BUSCADOR))
        assertEquals(
            listOf("DERECHA 4 LARGA · 50", "120\u00A0m"),
            banco.textos(banco.conEtiqueta(ETIQUETA_NOTA_PREFIJO + 0)),
        )
        assertTrue("la franja sube al sitio de la barra: $franja", franja.top < 24 * 3f)
        assertTrue("la franja ocupa todo el ancho: $franja", franja.width > 411 * 3 * 0.8f)
    }

    @Test
    fun `al tocar el mapa reaparece la barra sobre la franja y a los 8 s sin toques se vuelve a esconder`() {
        poner(libre, notas)
        banco.tocarEn(200f, 1000f)
        val barra = banco.conEtiqueta(ETIQUETA_BUSCADOR).boundsInRoot
        val franja = banco.conEtiqueta(ETIQUETA_FRANJA_NOTAS).boundsInRoot
        assertTrue("la franja baja bajo la barra: $franja vs $barra", franja.top >= barra.bottom)
        banco.esperar(7_000)
        assertTrue("a los 7 s sigue", banco.hay(ETIQUETA_BUSCADOR))
        banco.esperar(1_500)
        assertFalse("a los 8 s se esconde", banco.hay(ETIQUETA_BUSCADOR))
        assertTrue(banco.hay(ETIQUETA_FRANJA_NOTAS))
    }

    @Test
    fun `cada toque reinicia la cuenta atras de la barra`() {
        poner(libre, notas)
        banco.tocarEn(200f, 1000f)
        banco.esperar(5_000)
        banco.tocarEn(200f, 1000f)
        banco.esperar(5_000)
        assertTrue("10 s tras el primer toque, 5 tras el segundo: sigue", banco.hay(ETIQUETA_BUSCADOR))
        banco.esperar(4_000)
        assertFalse(banco.hay(ETIQUETA_BUSCADOR))
    }

    @Test
    fun `con texto escrito la barra no se esconde aunque haya notas`() {
        poner(libre.copy(texto = "S"), notas)
        assertTrue(banco.hay(ETIQUETA_BUSCADOR))
        banco.tocarEn(200f, 1000f)
        banco.esperar(20_000)
        assertTrue(banco.hay(ETIQUETA_BUSCADOR))
    }

    @Test
    fun `con resultados o vista previa abiertos la barra se ve y no se esconde`() {
        poner(libre.copy(texto = "S", busqueda = BusquedaDestino.Resultados(emptyList())), notas)
        banco.tocarEn(200f, 1000f)
        banco.esperar(20_000)
        assertTrue(banco.hay(ETIQUETA_BUSCADOR))
        assertTrue(banco.hay(ETIQUETA_RESULTADOS))
        poner(vistaPrevia(*dosRutas).copy(control = EstadoNavegacion.Libre(0L, 0.0)), notas)
        banco.tocarEn(200f, 1000f)
        banco.esperar(20_000)
        assertTrue(banco.hay(ETIQUETA_BUSCADOR))
    }

    @Test
    fun `con el campo enfocado la barra no se esconde`() {
        poner(libre, notas)
        banco.tocarEn(200f, 1000f)
        banco
            .conEtiqueta(ETIQUETA_BUSCADOR)
            .config
            .getOrNull(SemanticsActions.RequestFocus)!!
            .action!!
            .invoke()
        banco.reposo()
        banco.esperar(20_000)
        assertTrue(banco.hay(ETIQUETA_BUSCADOR))
    }

    @Test
    fun `sin notas la barra se ve como siempre y tocar el mapa no cambia nada`() {
        poner(libre)
        assertTrue(banco.hay(ETIQUETA_BUSCADOR))
        banco.tocarEn(200f, 1000f)
        banco.esperar(10_000)
        assertTrue(banco.hay(ETIQUETA_BUSCADOR))
    }

    @Test
    fun `la franja se esconde con una busqueda o una vista previa abiertas y sin notas`() {
        poner(libre.copy(texto = "S", busqueda = BusquedaDestino.Resultados(emptyList())), notas)
        assertFalse(banco.hay(ETIQUETA_FRANJA_NOTAS))
        poner(vistaPrevia(*dosRutas).copy(control = EstadoNavegacion.Libre(0L, 0.0)), notas)
        assertFalse(banco.hay(ETIQUETA_FRANJA_NOTAS))
        poner(libre)
        assertFalse(banco.hay(ETIQUETA_FRANJA_NOTAS))
    }

    @Test
    fun `guiando las notas las pinta el panel de guiado y no se duplican arriba`() {
        poner(guiando(), notas)
        assertFalse(banco.hay(ETIQUETA_FRANJA_NOTAS))
    }

    @Test
    fun `con un guiado pendiente aparece seguir a destino bajo la barra y empieza con un toque`() {
        poner(NavegacionUiState(seguir = OfertaSeguir(destino)))
        val barra = banco.conEtiqueta(ETIQUETA_BUSCADOR).boundsInRoot
        val tarjeta = banco.conEtiqueta(ETIQUETA_SEGUIR)
        assertEquals(listOf("Seguir a Sóller"), banco.textos(tarjeta))
        assertTrue(tarjeta.boundsInRoot.top >= barra.bottom)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_SEGUIR_BOTON))
        assertEquals(1, seguidas)
        assertEquals(0, descartadas)
    }

    @Test
    fun `la x de seguir descarta el guiado pendiente y es un objetivo tactil de 48 dp`() {
        poner(NavegacionUiState(seguir = OfertaSeguir(destino)))
        val x = banco.conEtiqueta(ETIQUETA_SEGUIR_DESCARTAR)
        assertTrue(x.children.any { banco.descripcion(it) == "Descartar" } || banco.descripcion(x) == "Descartar")
        assertTrue(x.size.width >= 48 * 3 && x.size.height >= 48 * 3)
        banco.pulsar(x)
        assertEquals(1, descartadas)
        assertEquals(0, seguidas)
    }

    @Test
    fun `mientras calcula la ruta seguir lo dice y no admite otro toque`() {
        poner(NavegacionUiState(seguir = OfertaSeguir(destino, FaseSeguir.CALCULANDO)))
        assertEquals(listOf("Calculando la ruta"), banco.textos(banco.conEtiqueta(ETIQUETA_SEGUIR_ESTADO)))
        assertFalse(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_SEGUIR_BOTON)))
    }

    @Test
    fun `sin ubicacion o con error seguir explica el motivo`() {
        poner(NavegacionUiState(seguir = OfertaSeguir(destino, FaseSeguir.SIN_POSICION)))
        assertEquals(listOf("Esperando la ubicación"), banco.textos(banco.conEtiqueta(ETIQUETA_SEGUIR_ESTADO)))
        poner(NavegacionUiState(seguir = OfertaSeguir(destino, FaseSeguir.ERROR)))
        assertEquals(listOf("No se pudo calcular la ruta"), banco.textos(banco.conEtiqueta(ETIQUETA_SEGUIR_ESTADO)))
        assertTrue(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_SEGUIR_BOTON)))
    }

    @Test
    fun `guiando no se ofrece seguir aunque haya un pendiente`() {
        poner(guiando().copy(seguir = OfertaSeguir(destino)))
        assertFalse(banco.hay(ETIQUETA_SEGUIR))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w891dp-h411dp-xxhdpi")
class NavegacionEnMapaHorizontalTest : NavegacionEnMapaBase() {
    @Test
    fun `en horizontal la vista previa cabe en una fila y funciona`() {
        poner(vistaPrevia(*dosRutas))
        val panel = banco.conEtiqueta(ETIQUETA_VISTA_PREVIA).boundsInRoot
        val empezar = banco.conEtiqueta(ETIQUETA_EMPEZAR).boundsInRoot
        val cancelar = banco.conEtiqueta(ETIQUETA_CANCELAR).boundsInRoot
        // Una sola fila: cancelar y empezar a la misma altura que las opciones.
        assertTrue(panel.height < 411 * 3 / 2f)
        assertEquals(cancelar.center.y, empezar.center.y, 1f)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_EMPEZAR))
        assertEquals(1, empezadas)
    }

    @Test
    fun `en horizontal la barra y los resultados caben sobre el mapa`() {
        val items = List(6) { ResultadoBusqueda("Lugar $it", "pueblo", "Palma", PALMA) }
        poner(NavegacionUiState(texto = "L", busqueda = BusquedaDestino.Resultados(items)))
        val lista = banco.conEtiqueta(ETIQUETA_RESULTADOS).boundsInRoot
        assertTrue(banco.hay(ETIQUETA_BUSCADOR))
        assertTrue("La lista no debe pasar de la mitad de la pantalla: $lista", lista.height <= 411 * 3 * 0.6f)
    }

    @Test
    fun `en horizontal guiando tambien hay terminar en el panel lateral y el hueco del panel`() {
        poner(guiando())
        assertTrue(banco.hay(ETIQUETA_PANEL_GUIADO))
        val panel = banco.conEtiqueta("panel").boundsInRoot
        val bandera = banco.conEtiqueta(ETIQUETA_TERMINAR).boundsInRoot
        assertTrue(
            "bandera dentro del panel: $bandera vs $panel",
            bandera.left >= panel.left && bandera.right <= panel.right,
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w891dp-h411dp-xxhdpi")
class NavegacionEnMapaNotasHorizontalTest : NavegacionEnMapaBase() {
    @Test
    fun `en horizontal la franja y seguir caben sobre el mapa`() {
        val notas =
            NotasUiState(
                listOf(
                    visible(deNota(nota(4, siguienteM = 50))),
                    visible(deAviso(TipoAviso.PUENTE), 300.0),
                    visible(deNota(nota(2, Direccion.IZQUIERDA)), 500.0),
                ),
            )
        poner(NavegacionUiState(control = EstadoNavegacion.Libre(0L, 0.0), seguir = OfertaSeguir(destino)), notas)
        val franja = banco.conEtiqueta(ETIQUETA_FRANJA_NOTAS).boundsInRoot
        val tarjeta = banco.conEtiqueta(ETIQUETA_SEGUIR).boundsInRoot
        assertTrue("la franja no pasa de la mitad de la pantalla: $franja", franja.bottom <= 411 * 3 * 0.6f)
        assertTrue("seguir va bajo la franja", tarjeta.top >= franja.bottom)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_SEGUIR_BOTON))
        assertEquals(1, seguidas)
    }
}
