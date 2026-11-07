package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.comun.fijacion
import app.trombito.rallycopilot.ui.copiloto.AccionesCopiloto
import app.trombito.rallycopilot.ui.copiloto.CopilotoEnMapa
import app.trombito.rallycopilot.ui.copiloto.CopilotoUiState
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_MICROFONO_BOTON
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_MODO_BOTON
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_SILENCIO_BOTON
import app.trombito.rallycopilot.ui.copiloto.modoAlAlternarSilencio
import app.trombito.rallycopilot.ui.navegacion.NavegacionEnMapa
import app.trombito.rallycopilot.ui.navegacion.NavegacionUiState
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

class LogicaBotonesMapaTest {
    @Test
    fun `la velocidad cae en rapida, intermedia o lenta con histeresis entre 10 y 20`() {
        assertEquals(ZonaVelocidad.RAPIDA, zonaDeVelocidad(21))
        assertEquals(ZonaVelocidad.INTERMEDIA, zonaDeVelocidad(20))
        assertEquals(ZonaVelocidad.INTERMEDIA, zonaDeVelocidad(15))
        assertEquals(ZonaVelocidad.INTERMEDIA, zonaDeVelocidad(10))
        assertEquals(ZonaVelocidad.LENTA, zonaDeVelocidad(9))
        assertEquals(ZonaVelocidad.LENTA, zonaDeVelocidad(0))
        assertEquals("sin dato cuenta como parado", ZonaVelocidad.LENTA, zonaDeVelocidad(null))
    }

    @Test
    fun `silenciar alterna entre el silencio y el modo anterior`() {
        assertEquals(ModoCopiloto.SILENCIO, modoAlAlternarSilencio(ModoCopiloto.NOTAS, ModoCopiloto.COMPLETO))
        assertEquals(ModoCopiloto.NOTAS, modoAlAlternarSilencio(ModoCopiloto.SILENCIO, ModoCopiloto.NOTAS))
    }

    @Test
    fun `si no hay modo anterior con voz se vuelve al modo por defecto`() {
        assertEquals(
            ModoCopiloto.POR_DEFECTO,
            modoAlAlternarSilencio(ModoCopiloto.SILENCIO, ModoCopiloto.SILENCIO),
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BotonesMapaTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private var copiloto by mutableStateOf(CopilotoUiState(cargado = true, modo = ModoCopiloto.NOTAS))
    private val datos = EstadoDatosMapa(instalados = DatosInstalados("2026.10.1", 1_000_000, conSatelite = false))

    private var velocidad by mutableStateOf<Int?>(0)

    private fun poner(velocidadKmh: Int?) {
        velocidad = velocidadKmh
        banco.poner {
            RallyCopilotTheme {
                MapaScreen(
                    estado =
                        MapaUiState(
                            cargando = false,
                            datos = datos,
                            estilos = EstilosMapa("{}", null),
                            velocidadKmh = velocidad,
                        ),
                    vista = EstadoVistaMapa(),
                    acciones = AccionesMapa(),
                    mapa = { _, m -> Box(m.fillMaxSize().testTag(ETIQUETA_MAPA_FALSO)) },
                    navegacion = NavegacionEnMapa(NavegacionUiState(control = EstadoNavegacion.Libre(0L, 0.0))),
                    copiloto =
                        CopilotoEnMapa(
                            copiloto,
                            AccionesCopiloto(onModo = { copiloto = copiloto.copy(modo = it) }),
                        ),
                )
            }
        }
    }

    private fun conVelocidad(kmh: Int?) {
        velocidad = kmh
        banco.reposo()
    }

    private val secundarios = listOf(ETIQUETA_CAPAS_BOTON)
    private val deConduccion = listOf(ETIQUETA_MICROFONO_BOTON, ETIQUETA_MODO_BOTON, ETIQUETA_SILENCIO_BOTON)
    private val fijos = deConduccion + ETIQUETA_ORIENTACION

    @Test
    fun `parado se ven todos los botones`() {
        poner(velocidadKmh = 0)
        (secundarios + fijos).forEach { assertTrue("falta $it", banco.hay(it)) }
        banco.esperar(7_000)
        (secundarios + fijos).forEach { assertTrue("$it se esconde parado", banco.hay(it)) }
    }

    @Test
    fun `por encima de 20 km h se esconden capas y notas grandes y los de conduccion siguen`() {
        poner(velocidadKmh = 21)
        secundarios.forEach { assertFalse("$it se esconde deprisa", banco.hay(it)) }
        assertFalse("notas grandes también", banco.hay(ETIQUETA_NOTAS_GRANDES_BOTON))
        fijos.forEach { assertTrue("$it es fijo", banco.hay(it)) }
        // Tocar el mapa no los devuelve: hay que parar.
        val mapa = banco.conEtiqueta(ETIQUETA_MAPA_FALSO).boundsInRoot
        banco.tocarEn(mapa.center.x, mapa.top + 200f)
        secundarios.forEach { assertFalse("$it no vuelve con un toque", banco.hay(it)) }
    }

    @Test
    fun `no parpadean entre 10 y 20 y vuelven tras 3 segundos por debajo de 10`() {
        poner(velocidadKmh = 60)
        secundarios.forEach { assertFalse(banco.hay(it)) }
        conVelocidad(15)
        banco.esperar(5_000)
        secundarios.forEach { assertFalse("entre 10 y 20 siguen escondidos", banco.hay(it)) }
        conVelocidad(8)
        banco.esperar(2_000)
        secundarios.forEach { assertFalse("a los 2 s por debajo de 10 aún no", banco.hay(it)) }
        banco.esperar(1_500)
        secundarios.forEach { assertTrue("a los 3 s vuelven", banco.hay(it)) }
        // Y de nuevo deprisa, se esconden al momento.
        conVelocidad(40)
        secundarios.forEach { assertFalse(banco.hay(it)) }
    }

    @Test
    fun `una frenada corta por debajo de 10 que se vuelve a acelerar no los muestra`() {
        poner(velocidadKmh = 50)
        conVelocidad(8)
        banco.esperar(1_500)
        conVelocidad(30)
        banco.esperar(5_000)
        secundarios.forEach { assertFalse("$it no parpadea", banco.hay(it)) }
    }

    @Test
    fun `el boton de silenciar activa el silencio y devuelve el modo anterior`() {
        poner(velocidadKmh = 0)
        val boton = banco.conEtiqueta(ETIQUETA_SILENCIO_BOTON)
        assertEquals("Silenciar el copiloto", banco.descripcion(boton))
        assertEquals(false, boton.config.getOrNull(SemanticsProperties.Selected))
        banco.pulsar(boton)
        assertEquals(ModoCopiloto.SILENCIO, copiloto.modo)
        banco.reposo()
        val activo = banco.conEtiqueta(ETIQUETA_SILENCIO_BOTON)
        assertEquals(true, activo.config.getOrNull(SemanticsProperties.Selected))
        assertEquals("En silencio", banco.descripcionEstado(ETIQUETA_SILENCIO_BOTON))
        banco.pulsar(activo)
        assertEquals(ModoCopiloto.NOTAS, copiloto.modo)
    }

    @Test
    fun `los botones no llevan texto y miden 48 dp y 64 dp los de conduccion`() {
        poner(velocidadKmh = 0)
        (secundarios + fijos).forEach {
            val nodo = banco.conEtiqueta(it)
            val minimo = (if (it in deConduccion) 64 else 48) * 3
            assertTrue("$it con texto", banco.textos(nodo).isEmpty())
            assertTrue("$it sin descripcion", !banco.descripcion(nodo).isNullOrBlank())
            assertTrue("$it mide ${nodo.size}", nodo.size.width >= minimo && nodo.size.height >= minimo)
        }
    }

    @Test
    fun `entre botones hay al menos 12 dp`() {
        poner(velocidadKmh = 0)
        val cajas = (secundarios + fijos).map { banco.conEtiqueta(it).boundsInRoot }.sortedBy { it.top }
        cajas.zipWithNext().forEach { (a, b) ->
            assertTrue("hueco ${b.top - a.bottom}", b.top - a.bottom >= 12 * 3f - 1f)
        }
    }

    @Test
    fun `con el mapa movido y deprisa recentrar se esconde y al parar vuelve`() {
        velocidad = 60
        banco.poner {
            RallyCopilotTheme {
                MapaScreen(
                    estado =
                        MapaUiState(
                            cargando = false,
                            datos = datos,
                            estilos = EstilosMapa("{}", null),
                            posicion = fijacion(1_000),
                            velocidadKmh = velocidad,
                        ),
                    vista = EstadoVistaMapa(siguiendo = false),
                    acciones = AccionesMapa(),
                    mapa = { _, m -> Box(m.fillMaxSize().testTag(ETIQUETA_MAPA_FALSO)) },
                )
            }
        }
        assertFalse(banco.hay(ETIQUETA_RECENTRAR))
        conVelocidad(0)
        banco.esperar(3_500)
        assertTrue(banco.hay(ETIQUETA_RECENTRAR))
    }

    @Test
    fun `con el mapa movido la brujula pasa a ser mi ubicacion`() {
        banco.poner {
            RallyCopilotTheme {
                MapaScreen(
                    estado =
                        MapaUiState(
                            cargando = false,
                            datos = datos,
                            estilos = EstilosMapa("{}", null),
                            posicion = fijacion(1_000),
                        ),
                    vista = EstadoVistaMapa(siguiendo = false),
                    acciones = AccionesMapa(),
                    mapa = { _, m -> Box(m.fillMaxSize().testTag(ETIQUETA_MAPA_FALSO)) },
                )
            }
        }
        assertTrue(banco.hay(ETIQUETA_RECENTRAR))
        assertFalse(banco.hay(ETIQUETA_ORIENTACION))
    }
}
