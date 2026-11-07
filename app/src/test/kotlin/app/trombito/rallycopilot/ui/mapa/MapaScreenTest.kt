package app.trombito.rallycopilot.ui.mapa

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ETIQUETA_SENAL_LIMITE_DESCONOCIDO
import app.trombito.rallycopilot.ui.comun.ETIQUETA_VOLVER
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.comun.fijacion
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_MODO_BOTON
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_SILENCIO_BOTON
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
private const val ETIQUETA_PANEL = "panel"

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class MapaScreenTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val estilos = EstilosMapa(vectorial = "{\"v\":1}", satelite = "{\"s\":1}")
    private val datos = EstadoDatosMapa(instalados = DatosInstalados("2026.10.1", 1_000_000, conSatelite = true))
    private val estilosPedidos = mutableListOf<String>()

    private fun conDatos(
        estilosMapa: EstilosMapa? = estilos,
        resto: MapaUiState.() -> MapaUiState = { this },
    ) = MapaUiState(cargando = false, datos = datos, estilos = estilosMapa).resto()

    private val mapaFalso: @Composable (String, Modifier) -> Unit = { json, modificador ->
        estilosPedidos += json
        Box(modificador.fillMaxSize().testTag(ETIQUETA_MAPA_FALSO))
    }

    private fun poner(
        estado: MapaUiState,
        vista: EstadoVistaMapa = EstadoVistaMapa(),
        acciones: AccionesMapa = AccionesMapa(),
    ) = banco.poner {
        RallyCopilotTheme {
            MapaScreen(estado, vista, acciones, mapaFalso)
        }
    }

    @Test
    fun `con datos monta el mapa con el estilo vectorial y sin atribucion encima`() {
        poner(conDatos())
        assertTrue(banco.hay(ETIQUETA_MAPA_FALSO))
        assertEquals(estilos.vectorial, estilosPedidos.last())
        assertFalse(banco.textos(banco.raiz()).any { "OpenStreetMap" in it || "PNOA" in it })
    }

    @Test
    fun `en satelite se pide el estilo de satelite y tampoco hay atribucion`() {
        poner(conDatos { copy(capaElegida = CapaMapa.SATELITE) })
        assertEquals(estilos.satelite, estilosPedidos.last())
        assertFalse(banco.textos(banco.raiz()).any { "OpenStreetMap" in it || "PNOA" in it })
    }

    private fun abrirCapas() = banco.pulsar(banco.conEtiqueta(ETIQUETA_CAPAS_BOTON))

    @Test
    fun `el menu de capas empieza cerrado y el boton lo abre y lo cierra`() {
        poner(conDatos())
        assertFalse(banco.hay(ETIQUETA_CAPAS_PANEL))
        assertTrue(banco.hay(ETIQUETA_CAPAS_BOTON))
        abrirCapas()
        assertTrue(banco.hay(ETIQUETA_CAPAS_PANEL))
        assertTrue(banco.hay(ETIQUETA_CAPA_MAPA))
        assertTrue(banco.hay(ETIQUETA_CAPA_SATELITE))
        assertTrue(banco.hay(ETIQUETA_RELIEVE))
        abrirCapas()
        assertFalse(banco.hay(ETIQUETA_CAPAS_PANEL))
    }

    @Test
    fun `los controles del mapa no llevan texto visible solo descripciones`() {
        poner(conDatos())
        abrirCapas()
        listOf(
            ETIQUETA_CAPAS_BOTON,
            ETIQUETA_CAPAS_PANEL,
            ETIQUETA_CAPA_MAPA,
            ETIQUETA_CAPA_SATELITE,
            ETIQUETA_RELIEVE,
            ETIQUETA_ORIENTACION,
        ).forEach { etiqueta ->
            val nodo = banco.conEtiqueta(etiqueta)
            assertTrue("$etiqueta no debe llevar texto", banco.textos(nodo).isEmpty())
            assertTrue("$etiqueta necesita descripcion", !banco.descripcion(nodo).isNullOrBlank())
        }
    }

    @Test
    fun `los botones fijos del mapa tienen objetivo tactil de al menos 48 dp`() {
        poner(conDatos())
        val minimo = 48 * 3f // xxhdpi
        listOf(ETIQUETA_CAPAS_BOTON, ETIQUETA_ORIENTACION).forEach { etiqueta ->
            val tamano = banco.conEtiqueta(etiqueta).size
            assertTrue("$etiqueta mide $tamano", tamano.width >= minimo && tamano.height >= minimo)
        }
    }

    @Test
    fun `sin satelite instalado el icono de satelite esta deshabilitado`() {
        poner(conDatos(EstilosMapa(vectorial = estilos.vectorial, satelite = null)))
        abrirCapas()
        assertFalse(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_CAPA_SATELITE)))
        assertTrue(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_CAPA_MAPA)))
    }

    @Test
    fun `el menu de capas cambia la base`() {
        val pedidas = mutableListOf<CapaMapa>()
        poner(conDatos(), acciones = AccionesMapa(onCapa = { pedidas += it }))
        abrirCapas()
        banco.pulsar(banco.conEtiqueta(ETIQUETA_CAPA_SATELITE))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_CAPA_MAPA))
        assertEquals(listOf(CapaMapa.SATELITE, CapaMapa.VECTORIAL), pedidas)
    }

    @Test
    fun `el interruptor de relieve pide lo contrario de su estado`() {
        val pedidos = mutableListOf<Boolean>()
        val acciones = AccionesMapa(onRelieve = { pedidos += it })
        var estado by mutableStateOf(conDatos())
        banco.poner { RallyCopilotTheme { MapaScreen(estado, EstadoVistaMapa(), acciones, mapaFalso) } }
        abrirCapas()
        banco.pulsar(banco.conEtiqueta(ETIQUETA_RELIEVE))
        assertEquals(listOf(true), pedidos)
        // Con el relieve ya activo, pulsar lo apaga.
        estado = conDatos { copy(relieve = true) }
        banco.reposo()
        banco.pulsar(banco.conEtiqueta(ETIQUETA_RELIEVE))
        assertEquals(listOf(true, false), pedidos)
    }

    @Test
    fun `el boton de orientacion alterna entre rumbo y norte`() {
        val pedidas = mutableListOf<Orientacion>()
        val acciones = AccionesMapa(onOrientacion = { pedidas += it })
        poner(conDatos { copy(orientacion = Orientacion.RUMBO) }, acciones = acciones)
        assertTrue(banco.descripcion(banco.conEtiqueta(ETIQUETA_ORIENTACION))!!.startsWith("Rumbo arriba"))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ORIENTACION))
        assertEquals(listOf(Orientacion.NORTE), pedidas)
    }

    @Test
    fun `con el norte arriba el boton lo dice y pide volver al rumbo`() {
        val pedidas = mutableListOf<Orientacion>()
        val acciones = AccionesMapa(onOrientacion = { pedidas += it })
        poner(conDatos { copy(orientacion = Orientacion.NORTE) }, acciones = acciones)
        assertTrue(banco.descripcion(banco.conEtiqueta(ETIQUETA_ORIENTACION))!!.startsWith("Norte arriba"))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ORIENTACION))
        assertEquals(listOf(Orientacion.RUMBO), pedidas)
    }

    @Test
    fun `sin datos no hay controles de mapa`() {
        poner(MapaUiState(cargando = false))
        assertFalse(banco.hay(ETIQUETA_CAPAS_BOTON))
        assertFalse(banco.hay(ETIQUETA_ORIENTACION))
    }

    @Test
    fun `la pantalla se mantiene encendida solo mientras el mapa esta visible`() {
        lateinit var vista: View
        var visible by mutableStateOf(true)
        banco.poner {
            vista = LocalView.current
            RallyCopilotTheme { if (visible) MapaScreen(conDatos(), EstadoVistaMapa(), AccionesMapa(), mapaFalso) }
        }
        assertTrue(vista.keepScreenOn)
        visible = false
        banco.reposo()
        assertFalse(vista.keepScreenOn)
    }

    @Test
    fun `sin datos muestra la tarjeta de descarga y lleva a mapas offline`() {
        var abierto = 0
        poner(MapaUiState(cargando = false), acciones = AccionesMapa(onAbrirMapasOffline = { abierto++ }))
        assertFalse(banco.hay(ETIQUETA_MAPA_FALSO))
        assertTrue("Descarga los mapas de Mallorca" in banco.textos(banco.conEtiqueta(ETIQUETA_SIN_DATOS)))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_SIN_DATOS_BOTON))
        assertEquals(1, abierto)
    }

    @Test
    fun `con datos instalados pero estilo ilegible avisa de mapas danados y no de descargar`() {
        var abierto = 0
        poner(
            MapaUiState(cargando = false, datos = datos),
            acciones = AccionesMapa(onAbrirMapasOffline = { abierto++ }),
        )
        assertFalse(banco.hay(ETIQUETA_MAPA_FALSO))
        assertFalse(banco.hay(ETIQUETA_SIN_DATOS))
        assertTrue("Los mapas instalados están dañados" in banco.textos(banco.conEtiqueta(ETIQUETA_DANADOS)))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_DANADOS_BOTON))
        assertEquals(1, abierto)
    }

    @Test
    fun `mientras carga no muestra ni mapa ni tarjeta`() {
        poner(MapaUiState(cargando = true))
        assertTrue(banco.hay(ETIQUETA_CARGANDO))
        assertFalse(banco.hay(ETIQUETA_SIN_DATOS))
        assertFalse(banco.hay(ETIQUETA_MAPA_FALSO))
    }

    @Test
    fun `el panel siempre esta y la senal solo con limite`() {
        poner(conDatos { copy(velocidadKmh = 72, via = ViaActual("Carretera", "Ma-1110", 50)) })
        assertTrue(banco.hay(ETIQUETA_VELOCIDAD))
        assertTrue(banco.hay(ETIQUETA_LIMITE))
    }

    @Test
    fun `sin limite conocido en la via la senal es gris con una interrogacion`() {
        poner(conDatos { copy(velocidadKmh = 72, via = ViaActual("Cami", null, null)) })
        assertTrue(banco.hay(ETIQUETA_VELOCIDAD))
        assertFalse("no hay una señal de límite falsa", banco.hay(ETIQUETA_LIMITE))
        val senal = banco.conEtiqueta(ETIQUETA_SENAL_LIMITE_DESCONOCIDO)
        assertEquals(listOf("?"), banco.textos(senal))
        assertEquals(
            "Velocidad: 72 kilómetros por hora. Límite de velocidad desconocido en esta vía",
            banco.descripcion(banco.conEtiqueta(ETIQUETA_VELOCIDAD)),
        )
        assertEquals(40 * 3, senal.size.width)
    }

    @Test
    fun `sin via conocida no hay ninguna senal`() {
        poner(conDatos { copy(velocidadKmh = 72) })
        assertFalse(banco.hay(ETIQUETA_LIMITE))
        assertFalse(banco.hay(ETIQUETA_SENAL_LIMITE_DESCONOCIDO))
    }

    @Test
    fun `el aviso de actualizacion se puede cerrar`() {
        var cerrado = 0
        val estado = conDatos().copy(datos = datos.copy(avisoActualizado = "2026.11.0"))
        poner(estado, acciones = AccionesMapa(onDescartarAviso = { cerrado++ }))
        assertEquals(listOf("Mapas actualizados"), banco.textos(banco.conEtiqueta(ETIQUETA_AVISO)).take(1))
        assertFalse(
            "la versión no se enseña al conducir",
            banco.textos(banco.conEtiqueta(ETIQUETA_AVISO)).any {
                "2026" in
                    it
            },
        )
        banco.pulsar(banco.conEtiqueta(ETIQUETA_AVISO_CERRAR))
        assertEquals(1, cerrado)
    }

    @Test
    fun `el aviso de actualizacion se descarta solo a los 4 segundos y va abajo sobre el panel`() {
        var cerrado = 0
        val estado = conDatos().copy(datos = datos.copy(avisoActualizado = "2026.11.0"))
        poner(estado, acciones = AccionesMapa(onDescartarAviso = { cerrado++ }))
        val aviso = banco.conEtiqueta(ETIQUETA_AVISO).boundsInRoot
        val panel = banco.conEtiqueta(ETIQUETA_PANEL).boundsInRoot
        assertTrue("el aviso queda sobre el panel: $aviso vs $panel", aviso.bottom <= panel.top + 1)
        banco.esperar(3_000)
        assertEquals(0, cerrado)
        banco.esperar(1_500)
        assertEquals(1, cerrado)
    }

    @Test
    fun `sin aviso no hay banner`() {
        poner(conDatos())
        assertFalse(banco.hay(ETIQUETA_AVISO))
    }

    @Test
    fun `con permiso no se muestra la tarjeta de permiso`() {
        poner(conDatos())
        assertFalse(banco.hay(ETIQUETA_PERMISO))
    }

    @Test
    fun `sin pedir el permiso se explica y se puede pedir`() {
        var pedido = 0
        poner(
            conDatos(),
            EstadoVistaMapa(permiso = EstadoPermisoUbicacion.EXPLICAR),
            AccionesMapa(onPedirPermiso = { pedido++ }),
        )
        assertTrue(banco.hay(ETIQUETA_PERMISO))
        assertFalse(banco.hay(ETIQUETA_PERMISO_AJUSTES))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_PERMISO_PEDIR))
        assertEquals(1, pedido)
    }

    @Test
    fun `con el permiso bloqueado se ofrece abrir los ajustes`() {
        var ajustes = 0
        poner(
            conDatos(),
            EstadoVistaMapa(permiso = EstadoPermisoUbicacion.DENEGADO_PERMANENTE),
            AccionesMapa(onAbrirAjustes = { ajustes++ }),
        )
        assertFalse(banco.hay(ETIQUETA_PERMISO_PEDIR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_PERMISO_AJUSTES))
        assertEquals(1, ajustes)
    }

    @Test
    fun `mientras el mapa sigue la posicion no hay boton de recentrar`() {
        poner(conDatos { copy(posicion = fijacion(1_000)) }, EstadoVistaMapa(siguiendo = true))
        assertFalse(banco.hay(ETIQUETA_RECENTRAR))
    }

    @Test
    fun `tras mover el mapa aparece recentrar y funciona`() {
        var recentrado = 0
        poner(
            conDatos { copy(posicion = fijacion(1_000)) },
            EstadoVistaMapa(siguiendo = false),
            AccionesMapa(onRecentrar = { recentrado++ }),
        )
        banco.pulsar(banco.conEtiqueta(ETIQUETA_RECENTRAR))
        assertEquals(1, recentrado)
    }

    @Test
    fun `no hay flecha de volver en la pantalla del mapa`() {
        poner(conDatos())
        assertFalse(banco.hay(ETIQUETA_VOLVER))
        assertFalse(banco.nodos().mapNotNull { banco.descripcion(it) }.any { it.startsWith("Volver") })
    }

    @Test
    fun `los botones redondos van a la derecha sobre la hoja y la pildora a la izquierda`() {
        // A 72 km/h capas se esconde; los de conducción y la brújula siguen.
        poner(conDatos { copy(velocidadKmh = 72) })
        val hoja = banco.conEtiqueta(ETIQUETA_PANEL).boundsInRoot
        val raiz = banco.raiz().boundsInRoot
        val pildora = banco.conEtiqueta(ETIQUETA_VELOCIDAD).boundsInRoot
        listOf(ETIQUETA_ORIENTACION, ETIQUETA_MODO_BOTON, ETIQUETA_SILENCIO_BOTON).forEach {
            val boton = banco.conEtiqueta(it).boundsInRoot
            assertTrue("$it sobre la hoja: $boton vs $hoja", boton.bottom <= hoja.top + 1)
            assertTrue("$it a la derecha: $boton", boton.left > raiz.width / 2)
            assertFalse("$it no pisa la pildora", boton.overlaps(pildora))
        }
        assertTrue("la pildora sobre la hoja: $pildora vs $hoja", pildora.bottom <= hoja.top + 1)
        assertTrue("la pildora a la izquierda: $pildora", pildora.left < raiz.width / 4)
    }

    @Test
    fun `la columna de botones tiene objetivos de 64 dp los de conduccion y 48 dp la brujula separados 12 dp`() {
        poner(conDatos())
        val fijos = listOf(ETIQUETA_MODO_BOTON, ETIQUETA_SILENCIO_BOTON, ETIQUETA_ORIENTACION)
        val cajas = fijos.map { banco.conEtiqueta(it).boundsInRoot }.sortedBy { it.top }
        val minimos = listOf(64, 64, 48)
        cajas.zip(minimos).forEach { (c, dp) ->
            assertTrue("$c < $dp dp", c.width >= dp * 3f - 1 && c.height >= dp * 3f - 1)
        }
        cajas.zipWithNext().forEach { (a, b) -> assertEquals("hueco de 12 dp", 12 * 3f, b.top - a.bottom, 1f) }
    }

    @Test
    fun `el menu de capas se abre sobre el mapa y no dentro de la hoja`() {
        poner(conDatos())
        abrirCapas()
        val panel = banco.conEtiqueta(ETIQUETA_PANEL).boundsInRoot
        val menu = banco.conEtiqueta(ETIQUETA_CAPAS_PANEL).boundsInRoot
        assertTrue("el menu queda encima de la hoja: $menu vs $panel", menu.bottom <= panel.top + 1)
    }

    @Test
    fun `el aviso de actualizacion ocupa el ancho y no deja controles arriba`() {
        val estado = conDatos().copy(datos = datos.copy(avisoActualizado = "2026.11.0"))
        poner(estado)
        val aviso = banco.conEtiqueta(ETIQUETA_AVISO).boundsInRoot
        val raiz = banco.raiz().boundsInRoot
        assertTrue("aviso a todo el ancho: $aviso", aviso.width > raiz.width * 0.9f)
        listOf(ETIQUETA_CAPAS_BOTON, ETIQUETA_ORIENTACION).forEach {
            assertTrue("$it no esta arriba", banco.conEtiqueta(it).boundsInRoot.top > raiz.height / 2)
        }
    }

    @Test
    fun `en vertical el mapa ocupa toda la pantalla y la hoja se ancla abajo con 112 dp como mucho`() {
        poner(conDatos())
        val mapa = banco.conEtiqueta(ETIQUETA_MAPA_FALSO).boundsInRoot
        val hoja = banco.conEtiqueta(ETIQUETA_PANEL).boundsInRoot
        val raiz = banco.raiz().boundsInRoot
        assertEquals("el mapa a pantalla completa", raiz.height, mapa.height, 1f)
        assertEquals("la hoja abajo del todo", raiz.bottom, hoja.bottom, 1f)
        assertEquals("la hoja a todo el ancho", raiz.width, hoja.width, 1f)
        assertTrue("hoja compacta: ${hoja.height}", hoja.height <= (112 + 48) * 3f)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
class MapaScreenHorizontalTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun poner(velocidadKmh: Int? = 0) {
        val datos = EstadoDatosMapa(instalados = DatosInstalados("1", 1, conSatelite = false))
        banco.poner {
            RallyCopilotTheme {
                MapaScreen(
                    estado =
                        MapaUiState(
                            cargando = false,
                            datos = datos,
                            estilos = EstilosMapa("{}", null),
                            velocidadKmh = velocidadKmh,
                        ),
                    vista = EstadoVistaMapa(),
                    acciones = AccionesMapa(),
                    mapa = { _, m -> Box(m.testTag(ETIQUETA_MAPA_FALSO)) },
                )
            }
        }
    }

    @Test
    fun `en horizontal el mapa va a pantalla completa y la hoja abajo a la izquierda de 320 dp`() {
        poner()
        val mapa = banco.conEtiqueta(ETIQUETA_MAPA_FALSO).boundsInRoot
        val hoja = banco.conEtiqueta(ETIQUETA_PANEL).boundsInRoot
        val raiz = banco.raiz().boundsInRoot
        assertEquals("el mapa a pantalla completa", raiz.width, mapa.width, 1f)
        assertEquals("la hoja abajo", raiz.bottom, hoja.bottom, 1f)
        assertEquals("la hoja a la izquierda", raiz.left, hoja.left, 1f)
        assertEquals("la hoja de 320 dp", 320 * 3f, hoja.width, 1f)
        assertFalse(banco.textos(banco.raiz()).any { "OpenStreetMap" in it || "PNOA" in it })
        assertTrue(banco.hay(ETIQUETA_VELOCIDAD))
    }

    @Test
    fun `en horizontal la columna de botones va a la derecha y la pildora sobre la hoja`() {
        poner()
        val raiz = banco.raiz().boundsInRoot
        val hoja = banco.conEtiqueta(ETIQUETA_PANEL).boundsInRoot
        val pildora = banco.conEtiqueta(ETIQUETA_VELOCIDAD).boundsInRoot
        listOf(ETIQUETA_CAPAS_BOTON, ETIQUETA_ORIENTACION).forEach {
            val boton = banco.conEtiqueta(it).boundsInRoot
            assertTrue("$it a la derecha: $boton", boton.left > raiz.width * 0.7f)
            assertTrue("$it dentro de la pantalla: $boton", boton.top >= raiz.top && boton.bottom <= raiz.bottom)
        }
        assertTrue("la pildora sobre la hoja: $pildora vs $hoja", pildora.bottom <= hoja.top + 1)
        assertTrue("la pildora encima de la hoja de 320 dp", pildora.right <= hoja.right + 1)
        assertFalse(banco.hay(ETIQUETA_VOLVER))
    }
}
