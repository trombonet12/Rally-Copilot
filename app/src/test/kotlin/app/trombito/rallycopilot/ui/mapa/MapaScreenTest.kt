package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.testTag
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

    private fun poner(
        estado: MapaUiState,
        vista: EstadoVistaMapa = EstadoVistaMapa(),
        acciones: AccionesMapa = AccionesMapa(),
    ) = banco.poner {
        RallyCopilotTheme {
            MapaScreen(estado, vista, acciones, mapa = { json, modificador ->
                estilosPedidos += json
                Box(modificador.fillMaxSize().testTag(ETIQUETA_MAPA_FALSO))
            })
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

    @Test
    fun `sin satelite instalado el conmutador lo deshabilita`() {
        poner(conDatos(EstilosMapa(vectorial = estilos.vectorial, satelite = null)))
        assertFalse(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_CAPA_SATELITE)))
        assertTrue(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_CAPA_MAPA)))
    }

    @Test
    fun `el conmutador pide la capa elegida`() {
        val pedidas = mutableListOf<CapaMapa>()
        poner(conDatos(), acciones = AccionesMapa(onCapa = { pedidas += it }))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_CAPA_SATELITE))
        assertEquals(listOf(CapaMapa.SATELITE), pedidas)
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
    fun `volver llama a la accion`() {
        var volver = 0
        poner(conDatos(), acciones = AccionesMapa(onVolver = { volver++ }))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        assertEquals(1, volver)
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
}
