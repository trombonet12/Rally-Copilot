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
import app.trombito.rallycopilot.ui.comun.ETIQUETA_VOLVER
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.comun.fijacion
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
    fun `sin limite de la via no hay senal`() {
        poner(conDatos { copy(velocidadKmh = 72, via = ViaActual("Cami", null, null)) })
        assertTrue(banco.hay(ETIQUETA_VELOCIDAD))
        assertFalse(banco.hay(ETIQUETA_LIMITE))
    }

    @Test
    fun `el aviso de actualizacion se puede cerrar`() {
        var cerrado = 0
        val estado = conDatos().copy(datos = datos.copy(avisoActualizado = "2026.11.0"))
        poner(estado, acciones = AccionesMapa(onDescartarAviso = { cerrado++ }))
        assertTrue("Mapas actualizados a la versión 2026.11.0" in banco.textos(banco.conEtiqueta(ETIQUETA_AVISO)))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_AVISO_CERRAR))
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
    fun `capas y orientacion van en el panel inferior a los lados de la velocidad`() {
        poner(conDatos { copy(velocidadKmh = 72) })
        val panel = banco.conEtiqueta(ETIQUETA_PANEL).boundsInRoot
        val velocidad = banco.conEtiqueta(ETIQUETA_VELOCIDAD).boundsInRoot
        val capas = banco.conEtiqueta(ETIQUETA_CAPAS_BOTON).boundsInRoot
        val orientacion = banco.conEtiqueta(ETIQUETA_ORIENTACION).boundsInRoot
        assertTrue("capas dentro del panel: $capas vs $panel", capas.top >= panel.top && capas.bottom <= panel.bottom)
        assertTrue("capas a la izquierda de la velocidad", capas.right <= velocidad.left)
        assertTrue("orientacion dentro del panel", orientacion.top >= panel.top && orientacion.bottom <= panel.bottom)
        assertTrue("orientacion a la derecha de la velocidad", orientacion.left >= velocidad.right)
    }

    @Test
    fun `el menu de capas se abre sobre el mapa y no dentro del panel`() {
        poner(conDatos())
        abrirCapas()
        val panel = banco.conEtiqueta(ETIQUETA_PANEL).boundsInRoot
        val menu = banco.conEtiqueta(ETIQUETA_CAPAS_PANEL).boundsInRoot
        assertTrue("el menu queda encima del panel: $menu vs $panel", menu.bottom <= panel.top + 1)
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
    fun `en vertical el panel va debajo del mapa`() {
        poner(conDatos())
        val mapa = banco.conEtiqueta(ETIQUETA_MAPA_FALSO).boundsInRoot
        val panel = banco.conEtiqueta(ETIQUETA_PANEL).boundsInRoot
        assertTrue("El panel debe quedar bajo el mapa: $panel vs $mapa", panel.top >= mapa.bottom - 1)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
class MapaScreenHorizontalTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    @Test
    fun `en horizontal el panel va a la derecha del mapa`() {
        val datos = EstadoDatosMapa(instalados = DatosInstalados("1", 1, conSatelite = false))
        banco.poner {
            RallyCopilotTheme {
                MapaScreen(
                    estado =
                        MapaUiState(
                            cargando = false,
                            datos = datos,
                            estilos = EstilosMapa("{}", null),
                            velocidadKmh = 50,
                        ),
                    vista = EstadoVistaMapa(),
                    acciones = AccionesMapa(),
                    mapa = { _, m -> Box(m.testTag(ETIQUETA_MAPA_FALSO)) },
                )
            }
        }
        val mapa = banco.conEtiqueta(ETIQUETA_MAPA_FALSO).boundsInRoot
        val panel = banco.conEtiqueta(ETIQUETA_PANEL).boundsInRoot
        assertTrue("El panel debe quedar a la derecha del mapa: $panel vs $mapa", panel.left >= mapa.right - 1)
        assertFalse(banco.textos(banco.raiz()).any { "OpenStreetMap" in it || "PNOA" in it })
        assertTrue(banco.hay(ETIQUETA_VELOCIDAD))
    }

    @Test
    fun `en horizontal los botones van dentro del panel lateral y no hay flecha de volver`() {
        val datos = EstadoDatosMapa(instalados = DatosInstalados("1", 1, conSatelite = false))
        banco.poner {
            RallyCopilotTheme {
                MapaScreen(
                    estado = MapaUiState(cargando = false, datos = datos, estilos = EstilosMapa("{}", null)),
                    vista = EstadoVistaMapa(),
                    acciones = AccionesMapa(),
                    mapa = { _, m -> Box(m.testTag(ETIQUETA_MAPA_FALSO)) },
                )
            }
        }
        val panel = banco.conEtiqueta(ETIQUETA_PANEL).boundsInRoot
        listOf(ETIQUETA_CAPAS_BOTON, ETIQUETA_ORIENTACION).forEach {
            val boton = banco.conEtiqueta(it).boundsInRoot
            assertTrue("$it dentro del panel: $boton vs $panel", boton.left >= panel.left && boton.right <= panel.right)
            assertTrue("$it dentro del panel en vertical", boton.top >= panel.top && boton.bottom <= panel.bottom)
        }
        assertFalse(banco.hay(ETIQUETA_VOLVER))
    }
}
