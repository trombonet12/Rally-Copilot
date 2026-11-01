package app.trombito.rallycopilot.ui.copiloto

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.mapa.AccionesMapa
import app.trombito.rallycopilot.ui.mapa.ETIQUETA_CAPAS_BOTON
import app.trombito.rallycopilot.ui.mapa.ETIQUETA_CAPAS_PANEL
import app.trombito.rallycopilot.ui.mapa.ETIQUETA_ORIENTACION
import app.trombito.rallycopilot.ui.mapa.ETIQUETA_VELOCIDAD
import app.trombito.rallycopilot.ui.mapa.EstadoVistaMapa
import app.trombito.rallycopilot.ui.mapa.MapaScreen
import app.trombito.rallycopilot.ui.mapa.MapaUiState
import app.trombito.rallycopilot.ui.navegacion.AccionesNavegacion
import app.trombito.rallycopilot.ui.navegacion.ETIQUETA_TERMINAR
import app.trombito.rallycopilot.ui.navegacion.NavegacionEnMapa
import app.trombito.rallycopilot.ui.navegacion.NavegacionUiState
import app.trombito.rallycopilot.ui.navegacion.SOLLER
import app.trombito.rallycopilot.ui.navegacion.rutaDePrueba
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

private const val ETIQUETA_MAPA_FALSO = "mapa-falso"
private const val ETIQUETA_PANEL = "panel"

/** Prueba el selector de modo y el diálogo de voz del copiloto montados en [MapaScreen]. */
abstract class CopilotoEnMapaBase {
    @get:Rule val limpieza = LimpiezaCompose()

    protected val banco = BancoCompose()
    protected val modos = mutableListOf<ModoCopiloto>()
    protected val elegidas = mutableListOf<VozCopiloto>()
    protected val probadas = mutableListOf<VozCopiloto>()
    protected var pospuestas = 0

    private val datos = EstadoDatosMapa(instalados = DatosInstalados("2026.10.1", 1_000_000, conSatelite = false))
    protected var copiloto by mutableStateOf(CopilotoUiState(cargado = true, voz = VozCopiloto.DAVEFX))

    private val acciones =
        AccionesCopiloto(
            onModo = { modos += it },
            onElegirVoz = { elegidas += it },
            onProbarVoz = { probadas += it },
            onPosponerVoz = { pospuestas++ },
        )

    protected fun poner(guiando: Boolean = false) {
        val navegacion =
            if (guiando) {
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
            } else {
                NavegacionUiState()
            }
        banco.poner {
            RallyCopilotTheme {
                MapaScreen(
                    estado =
                        MapaUiState(
                            cargando = false,
                            datos = datos,
                            estilos = EstilosMapa("{}", null),
                            velocidadKmh = 72,
                        ),
                    vista = EstadoVistaMapa(),
                    acciones = AccionesMapa(),
                    mapa = { _, m -> Box(m.testTag(ETIQUETA_MAPA_FALSO)) },
                    navegacion = NavegacionEnMapa(navegacion, AccionesNavegacion(onTerminar = {})),
                    copiloto = CopilotoEnMapa(copiloto, acciones),
                )
            }
        }
    }

    protected fun botonesDelPanel(): List<String> =
        listOf(ETIQUETA_CAPAS_BOTON, ETIQUETA_MODO_BOTON, ETIQUETA_ORIENTACION, ETIQUETA_TERMINAR)

    /** Los botones están dentro del panel negro y ninguno tapa la velocidad. */
    protected fun comprobarQueCabenSinTaparLaVelocidad() {
        val panel = banco.conEtiqueta(ETIQUETA_PANEL).boundsInRoot
        val velocidad = banco.conEtiqueta(ETIQUETA_VELOCIDAD).boundsInRoot
        botonesDelPanel().forEach { etiqueta ->
            val boton = banco.conEtiqueta(etiqueta).boundsInRoot
            val dentro =
                boton.left >= panel.left && boton.right <= panel.right &&
                    boton.top >= panel.top && boton.bottom <= panel.bottom
            assertTrue("$etiqueta dentro del panel: $boton vs $panel", dentro)
            assertFalse("$etiqueta tapa la velocidad: $boton vs $velocidad", boton.overlaps(velocidad))
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CopilotoEnMapaVerticalTest : CopilotoEnMapaBase() {
    @Test
    fun `el panel muestra un boton con el modo actual, sin texto y con descripcion para TalkBack`() {
        poner()
        val boton = banco.conEtiqueta(ETIQUETA_MODO_BOTON)
        assertTrue(banco.textos(boton).isEmpty())
        assertEquals("Modo del copiloto: Completo. Pulsa para cambiarlo", banco.descripcion(boton))
        assertTrue("Objetivo táctil de 48 dp", boton.size.width >= 48 * 3 && boton.size.height >= 48 * 3)
    }

    @Test
    fun `la descripcion del boton sigue al modo`() {
        copiloto = copiloto.copy(modo = ModoCopiloto.SILENCIO)
        poner()
        assertEquals(
            "Modo del copiloto: Silencio. Pulsa para cambiarlo",
            banco.descripcion(banco.conEtiqueta(ETIQUETA_MODO_BOTON)),
        )
        copiloto = copiloto.copy(modo = ModoCopiloto.NOTAS)
        banco.reposo()
        assertEquals(
            "Modo del copiloto: Notas. Pulsa para cambiarlo",
            banco.descripcion(banco.conEtiqueta(ETIQUETA_MODO_BOTON)),
        )
    }

    @Test
    fun `tocar el boton abre un menu con los cuatro modos como iconos`() {
        poner()
        assertFalse(banco.hay(ETIQUETA_MODO_PANEL))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_MODO_BOTON))
        assertTrue(banco.hay(ETIQUETA_MODO_PANEL))
        val opciones = banco.etiquetas(ETIQUETA_MODO_PREFIJO)
        assertEquals(ModoCopiloto.entries.size, opciones.size)
        val nombres = mutableListOf<String?>()
        opciones.forEach {
            val nodo = banco.conEtiqueta(it)
            assertTrue("$it sin texto", banco.textos(nodo).isEmpty())
            nombres += banco.descripcion(nodo)
        }
        assertEquals(listOf("Silencio", "Solo alertas", "Notas", "Completo"), nombres)
    }

    @Test
    fun `el menu marca el modo actual`() {
        copiloto = copiloto.copy(modo = ModoCopiloto.NOTAS)
        poner()
        banco.pulsar(banco.conEtiqueta(ETIQUETA_MODO_BOTON))
        val elegido = banco.conEtiqueta(ETIQUETA_MODO_PREFIJO + "notas")
        assertEquals("Seleccionado", elegido.config.getOrNull(SemanticsProperties.StateDescription))
        val otro = banco.conEtiqueta(ETIQUETA_MODO_PREFIJO + "silencio")
        assertNull(otro.config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test
    fun `elegir un modo lo pide y cierra el menu`() {
        poner()
        banco.pulsar(banco.conEtiqueta(ETIQUETA_MODO_BOTON))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_MODO_PREFIJO + "solo_alertas"))
        assertEquals(listOf(ModoCopiloto.SOLO_ALERTAS), modos)
        assertFalse(banco.hay(ETIQUETA_MODO_PANEL))
    }

    @Test
    fun `volver a tocar el boton cierra el menu sin cambiar nada`() {
        poner()
        banco.pulsar(banco.conEtiqueta(ETIQUETA_MODO_BOTON))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_MODO_BOTON))
        assertFalse(banco.hay(ETIQUETA_MODO_PANEL))
        assertTrue(modos.isEmpty())
    }

    @Test
    fun `el menu de modo y el de capas no estan abiertos a la vez`() {
        poner()
        banco.pulsar(banco.conEtiqueta(ETIQUETA_CAPAS_BOTON))
        assertTrue(banco.hay(ETIQUETA_CAPAS_PANEL))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_MODO_BOTON))
        assertTrue(banco.hay(ETIQUETA_MODO_PANEL))
        assertFalse(banco.hay(ETIQUETA_CAPAS_PANEL))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_CAPAS_BOTON))
        assertTrue(banco.hay(ETIQUETA_CAPAS_PANEL))
        assertFalse(banco.hay(ETIQUETA_MODO_PANEL))
    }

    @Test
    fun `en vertical los cuatro botones caben en el panel sin tapar la velocidad`() {
        poner(guiando = true)
        comprobarQueCabenSinTaparLaVelocidad()
    }

    @Test
    fun `con la voz elegida no sale el dialogo`() {
        poner()
        assertFalse(banco.hay(ETIQUETA_VOZ_DIALOGO))
    }

    @Test
    fun `mientras no se han leido los ajustes no sale el dialogo`() {
        copiloto = CopilotoUiState(cargado = false, voz = null)
        poner()
        assertFalse(banco.hay(ETIQUETA_VOZ_DIALOGO))
    }

    @Test
    fun `con la voz sin elegir sale el dialogo con las dos voces y sin nombres tecnicos`() {
        copiloto = copiloto.copy(voz = null)
        poner()
        assertTrue(banco.hay(ETIQUETA_VOZ_DIALOGO))
        val textos = banco.todosLosTextos()
        assertTrue("Elige la voz del copiloto" in textos)
        assertTrue("Voz 1" in textos)
        assertTrue("Voz 2" in textos)
        assertFalse(textos.any { "davefx" in it.lowercase() || "sharvard" in it.lowercase() })
        assertEquals(VozCopiloto.entries.size, banco.etiquetas(ETIQUETA_VOZ_OPCION_PREFIJO).size)
    }

    @Test
    fun `elegir guarda la voz marcada y por defecto es la primera`() {
        copiloto = copiloto.copy(voz = null)
        poner()
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOZ_ELEGIR))
        assertEquals(listOf(VozCopiloto.DAVEFX), elegidas)
    }

    @Test
    fun `se puede marcar la segunda voz y elegirla`() {
        copiloto = copiloto.copy(voz = null)
        poner()
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOZ_OPCION_PREFIJO + "sharvard"))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOZ_ELEGIR))
        assertEquals(listOf(VozCopiloto.SHARVARD), elegidas)
    }

    @Test
    fun `probar suena la voz sin elegirla`() {
        copiloto = copiloto.copy(voz = null)
        poner()
        val probar = banco.conEtiqueta(ETIQUETA_VOZ_PROBAR_PREFIJO + "sharvard")
        assertEquals("Probar Voz 2", banco.descripcion(probar))
        assertTrue(banco.textos(probar).isEmpty())
        banco.pulsar(probar)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOZ_PROBAR_PREFIJO + "davefx"))
        assertEquals(listOf(VozCopiloto.SHARVARD, VozCopiloto.DAVEFX), probadas)
        assertTrue(elegidas.isEmpty())
    }

    @Suppress("DEPRECATION") // Dialog.onBackPressed es la forma de simular «atrás» en un diálogo de Robolectric
    @Test
    fun `atras pospone el dialogo y no elige nada`() {
        copiloto = copiloto.copy(voz = null)
        poner()
        ShadowDialog.getLatestDialog().onBackPressed()
        banco.reposo()
        assertEquals(1, pospuestas)
        assertTrue(elegidas.isEmpty())
    }

    @Test
    fun `una vez pospuesto el dialogo desaparece`() {
        copiloto = copiloto.copy(voz = null)
        poner()
        assertTrue(banco.hay(ETIQUETA_VOZ_DIALOGO))
        copiloto = copiloto.copy(vozPospuesta = true)
        banco.reposo()
        assertFalse(banco.hay(ETIQUETA_VOZ_DIALOGO))
    }

    @Test
    fun `el selector sigue ahi mientras el dialogo esta abierto`() {
        copiloto = copiloto.copy(voz = null)
        poner()
        assertTrue(banco.hay(ETIQUETA_MODO_BOTON))
        assertEquals(1, ShadowDialog.getShownDialogs().count { it.isShowing })
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
class CopilotoEnMapaHorizontalTest : CopilotoEnMapaBase() {
    @Test
    fun `en horizontal los cuatro botones caben en el panel lateral sin tapar la velocidad`() {
        poner(guiando = true)
        comprobarQueCabenSinTaparLaVelocidad()
    }

    @Test
    fun `en horizontal el selector abre su menu`() {
        poner()
        banco.pulsar(banco.conEtiqueta(ETIQUETA_MODO_BOTON))
        assertTrue(banco.hay(ETIQUETA_MODO_PANEL))
        assertEquals(ModoCopiloto.entries.size, banco.etiquetas(ETIQUETA_MODO_PREFIJO).size)
    }
}
