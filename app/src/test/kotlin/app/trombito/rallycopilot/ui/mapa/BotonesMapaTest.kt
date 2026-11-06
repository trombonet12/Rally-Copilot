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
    fun `en marcha solo van los botones fijos hasta que se toca el mapa`() {
        assertTrue(botonesTactilesVisibles(enMarcha = false, tocado = false))
        assertFalse(botonesTactilesVisibles(enMarcha = true, tocado = false))
        assertTrue(botonesTactilesVisibles(enMarcha = true, tocado = true))
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

    private fun poner(velocidadKmh: Int?) =
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

    private val tactiles = listOf(ETIQUETA_CAPAS_BOTON, ETIQUETA_MICROFONO_BOTON)
    private val fijos = listOf(ETIQUETA_MODO_BOTON, ETIQUETA_SILENCIO_BOTON, ETIQUETA_ORIENTACION)

    @Test
    fun `parado se ven todos los botones`() {
        poner(velocidadKmh = 0)
        (tactiles + fijos).forEach { assertTrue("falta $it", banco.hay(it)) }
        banco.esperar(7_000)
        (tactiles + fijos).forEach { assertTrue("$it se esconde parado", banco.hay(it)) }
    }

    @Test
    fun `en marcha los botones secundarios se esconden a los 6 segundos y reaparecen al tocar el mapa`() {
        poner(velocidadKmh = 60)
        tactiles.forEach { assertTrue("al ponerse en marcha $it sigue unos segundos", banco.hay(it)) }
        banco.esperar(5_000)
        tactiles.forEach { assertTrue("a los 5 s $it sigue", banco.hay(it)) }
        banco.esperar(2_000)
        tactiles.forEach { assertFalse("a los 7 s $it se esconde", banco.hay(it)) }
        fijos.forEach { assertTrue("$it es fijo", banco.hay(it)) }

        val mapa = banco.conEtiqueta(ETIQUETA_MAPA_FALSO).boundsInRoot
        banco.tocarEn(mapa.center.x, mapa.top + 200f)
        tactiles.forEach { assertTrue("tras tocar el mapa $it vuelve", banco.hay(it)) }
        banco.esperar(7_000)
        tactiles.forEach { assertFalse("y se vuelve a esconder", banco.hay(it)) }
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
    fun `los botones no llevan texto y miden 48 dp`() {
        poner(velocidadKmh = 0)
        (tactiles + fijos).forEach {
            val nodo = banco.conEtiqueta(it)
            assertTrue("$it con texto", banco.textos(nodo).isEmpty())
            assertTrue("$it sin descripcion", !banco.descripcion(nodo).isNullOrBlank())
            assertTrue("$it mide ${nodo.size}", nodo.size.width >= 48 * 3 && nodo.size.height >= 48 * 3)
        }
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
