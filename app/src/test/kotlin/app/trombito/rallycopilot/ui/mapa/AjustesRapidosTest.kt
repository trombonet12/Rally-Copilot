package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.ajustes.ETIQUETA_AJUSTES_VOLUMEN
import app.trombito.rallycopilot.ui.ajustes.ETIQUETA_AJUSTES_VOLUMEN_VALOR
import app.trombito.rallycopilot.ui.ajustes.ETIQUETA_ANTICIPACION_PREFIJO
import app.trombito.rallycopilot.ui.ajustes.ETIQUETA_NOTAS_SIMPLIFICADAS
import app.trombito.rallycopilot.ui.ajustes.ETIQUETA_PREVISTA_ANTICIPACION
import app.trombito.rallycopilot.ui.ajustes.ETIQUETA_PREVISTA_ESCUCHAR
import app.trombito.rallycopilot.ui.ajustes.ETIQUETA_PREVISTA_NOTA
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.copiloto.AccionesCopiloto
import app.trombito.rallycopilot.ui.copiloto.CopilotoEnMapa
import app.trombito.rallycopilot.ui.copiloto.CopilotoUiState
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_AVISAR_INCIDENCIAS
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_AVISAR_RADARES
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_AVISAR_VELOCIDAD
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_HABLA_PREFIJO
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_MODO_BOTON
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_MODO_DETALLE
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_MODO_PREFIJO
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

/** La hoja de ajustes rápidos del panel de conducción: abre, cambia en caliente y cabe en vertical y apaisado. */
abstract class AjustesRapidosBase {
    @get:Rule val limpieza = LimpiezaCompose()

    protected val banco = BancoCompose()
    protected var copiloto by mutableStateOf(CopilotoUiState(cargado = true, voz = VozCopiloto.DAVEFX))
    protected val escuchas = mutableListOf<Unit>()

    private val datos = EstadoDatosMapa(instalados = DatosInstalados("2026.10.1", 1_000_000, conSatelite = false))

    private val acciones =
        AccionesCopiloto(
            onModo = { copiloto = copiloto.copy(modo = it) },
            onAnticipacion = { copiloto = copiloto.copy(anticipacion = it) },
            onNotasSimplificadas = { copiloto = copiloto.copy(notasSimplificadas = it) },
            onAvisarCruces = { copiloto = copiloto.copy(avisarCruces = it) },
            onAvisarSalidasAutopista = { copiloto = copiloto.copy(avisarSalidasAutopista = it) },
            onAvisarRotondas = { copiloto = copiloto.copy(avisarRotondas = it) },
            onSalidaManual = { copiloto = copiloto.copy(salidaManual = it) },
            onAvisarRadares = { copiloto = copiloto.copy(avisarRadares = it) },
            onAvisarIncidencias = { copiloto = copiloto.copy(avisarIncidencias = it) },
            onAvisarVelocidad = { copiloto = copiloto.copy(avisarVelocidad = it) },
            onVolumenVoz = { copiloto = copiloto.copy(volumenVoz = it) },
            onEscucharEjemplo = { escuchas += Unit },
        )

    protected fun poner() {
        banco.poner {
            RallyCopilotTheme {
                MapaScreen(
                    estado =
                        MapaUiState(
                            cargando = false,
                            datos = datos,
                            estilos = EstilosMapa("{}", null),
                            velocidadKmh = 0,
                        ),
                    vista = EstadoVistaMapa(),
                    acciones = AccionesMapa(),
                    mapa = { _, m -> Box(m.testTag(ETIQUETA_MAPA_FALSO)) },
                    copiloto = CopilotoEnMapa(copiloto, acciones),
                )
            }
        }
    }

    protected fun abrir() {
        poner()
        assertFalse(banco.hay(ETIQUETA_RAPIDOS_PANEL))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_MODO_BOTON))
        assertTrue(banco.hay(ETIQUETA_RAPIDOS_PANEL))
    }

    protected fun textoDe(etiqueta: String) = banco.textos(banco.conEtiqueta(etiqueta)).joinToString(" ")

    /** La hoja suelta (en la pantalla real Robolectric no mide los menús): cabe y sus botones miden 48 dp. */
    protected fun comprobarQueLaHojaEntraEnPantalla(horizontal: Boolean = false) {
        // La hoja es una ventana aparte: la pantalla se mide con el tamaño del qualifier (411 x 891 dp a xxhdpi).
        val pantalla = if (horizontal) Rect(0f, 0f, 891 * 3f, 411 * 3f) else Rect(0f, 0f, 411 * 3f, 891 * 3f)
        banco.poner {
            RallyCopilotTheme {
                PanelAjustesRapidos(
                    copiloto,
                    AccionesCopiloto(),
                    onCerrar = {},
                    horizontal = horizontal,
                )
            }
        }
        val hoja = banco.conEtiqueta(ETIQUETA_RAPIDOS_PANEL).boundsInRoot
        assertTrue(
            "La hoja $hoja sale de la pantalla $pantalla",
            hoja.left >= pantalla.left && hoja.right <= pantalla.right,
        )
        assertTrue(
            "La hoja $hoja sale de la pantalla $pantalla",
            hoja.top >= pantalla.top && hoja.bottom <= pantalla.bottom,
        )
        assertTrue("Como mucho el 60 % del alto: ${hoja.height}", hoja.height <= pantalla.height * 0.6f + 1f)
        assertEquals("A todo el ancho", pantalla.width, hoja.width, 1f)
        assertEquals(NivelAnticipacion.entries.size, banco.etiquetas(ETIQUETA_ANTICIPACION_PREFIJO).size)
        listOf(
            ETIQUETA_AVISAR_CRUCES,
            ETIQUETA_AVISAR_SALIDAS_AUTOPISTA,
            ETIQUETA_AVISAR_ROTONDAS,
            ETIQUETA_SALIDA_MANUAL,
        ).forEach {
            val nodo = banco.conEtiqueta(it)
            assertTrue("$it mide ${nodo.size}", nodo.size.width >= LADO_MINIMO_PX && nodo.size.height >= LADO_MINIMO_PX)
            assertTrue("$it sale de la hoja", nodo.boundsInRoot.right <= hoja.right)
        }
        banco.etiquetas(ETIQUETA_ANTICIPACION_PREFIJO).forEach {
            val nodo = banco.conEtiqueta(it)
            assertTrue("$it mide ${nodo.size}", nodo.size.width >= LADO_MINIMO_PX && nodo.size.height >= LADO_MINIMO_PX)
            assertTrue("$it sale de la hoja", nodo.boundsInRoot.right <= hoja.right)
        }
    }

    private companion object {
        /** 48 dp a xxhdpi (densidad 3). */
        const val LADO_MINIMO_PX = 48 * 3
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AjustesRapidosVerticalTest : AjustesRapidosBase() {
    @Test
    fun `el boton del copiloto abre la hoja y volver a tocarlo la cierra`() {
        abrir()
        banco.pulsar(banco.conEtiqueta(ETIQUETA_MODO_BOTON))
        assertFalse(banco.hay(ETIQUETA_RAPIDOS_PANEL))
    }

    @Test
    fun `la hoja trae los cuatro modos, anticipacion de cinco niveles, simplificadas, volumen y la vista previa`() {
        abrir()
        assertEquals(ModoCopiloto.entries.size, banco.etiquetas(ETIQUETA_MODO_PREFIJO).size)
        assertEquals(NivelAnticipacion.entries.size, banco.etiquetas(ETIQUETA_ANTICIPACION_PREFIJO).size)
        assertTrue(banco.hay(ETIQUETA_NOTAS_SIMPLIFICADAS))
        assertTrue(banco.hay(ETIQUETA_AJUSTES_VOLUMEN))
        assertTrue(banco.hay(ETIQUETA_PREVISTA_NOTA))
        assertEquals("Te la canta 5 s antes (a 90 km/h, a 150 m)", textoDe(ETIQUETA_PREVISTA_ANTICIPACION))
    }

    @Test
    fun `elegir un modo en la hoja lo aplica y cambia el nombre que se lee`() {
        abrir()
        banco.pulsar(banco.conEtiqueta(ETIQUETA_MODO_PREFIJO + "silencio"))
        assertEquals(ModoCopiloto.SILENCIO, copiloto.modo)
        assertEquals("El copiloto no habla", textoDe(ETIQUETA_MODO_DETALLE))
        assertTrue("la hoja sigue abierta", banco.hay(ETIQUETA_RAPIDOS_PANEL))
    }

    @Test
    fun `tocar la anticipacion cambia la vista previa y lo pide en caliente`() {
        abrir()
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ANTICIPACION_PREFIJO + "muy_pronto"))
        assertEquals(NivelAnticipacion.MUY_PRONTO, copiloto.anticipacion)
        assertEquals("Te la canta 8 s antes (a 90 km/h, a 230 m)", textoDe(ETIQUETA_PREVISTA_ANTICIPACION))
        assertTrue("la hoja sigue abierta", banco.hay(ETIQUETA_RAPIDOS_PANEL))
    }

    @Test
    fun `las simplificadas cambian la tarjeta de ejemplo`() {
        abrir()
        assertTrue("DERECHA 4" in textoDe(ETIQUETA_PREVISTA_NOTA))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_NOTAS_SIMPLIFICADAS).children.first { banco.esPulsable(it) })
        assertTrue(copiloto.notasSimplificadas)
        assertTrue("MEDIA" in textoDe(ETIQUETA_PREVISTA_NOTA))
    }

    private fun marcado(etiqueta: String) = banco.conEtiqueta(etiqueta).config.getOrNull(SemanticsProperties.Selected)

    @Test
    fun `la hoja trae tres interruptores de indicaciones, todos activados al empezar`() {
        abrir()
        listOf(ETIQUETA_AVISAR_CRUCES, ETIQUETA_AVISAR_SALIDAS_AUTOPISTA, ETIQUETA_AVISAR_ROTONDAS).forEach {
            assertTrue("falta $it", banco.hay(it))
            assertEquals("$it activado", true, marcado(it))
            assertTrue("$it pulsable", banco.esPulsable(banco.conEtiqueta(it)))
        }
    }

    @Test
    fun `cada interruptor de indicaciones cambia solo el suyo, en vivo, y se puede volver a activar`() {
        abrir()
        banco.pulsar(banco.conEtiqueta(ETIQUETA_AVISAR_CRUCES))
        assertFalse(copiloto.avisarCruces)
        assertTrue(copiloto.avisarSalidasAutopista && copiloto.avisarRotondas)
        assertEquals(false, marcado(ETIQUETA_AVISAR_CRUCES))

        banco.pulsar(banco.conEtiqueta(ETIQUETA_AVISAR_SALIDAS_AUTOPISTA))
        assertFalse(copiloto.avisarSalidasAutopista)
        assertTrue(copiloto.avisarRotondas)

        banco.pulsar(banco.conEtiqueta(ETIQUETA_AVISAR_ROTONDAS))
        assertFalse(copiloto.avisarRotondas)
        assertEquals(false, marcado(ETIQUETA_AVISAR_ROTONDAS))

        banco.pulsar(banco.conEtiqueta(ETIQUETA_AVISAR_CRUCES))
        assertTrue(copiloto.avisarCruces)
        assertEquals(true, marcado(ETIQUETA_AVISAR_CRUCES))
        assertTrue("la hoja sigue abierta", banco.hay(ETIQUETA_RAPIDOS_PANEL))
    }

    @Test
    fun `la hoja trae los avisos de voz y la velocidad de habla, y cada interruptor cambia solo el suyo`() {
        abrir()
        listOf(ETIQUETA_AVISAR_RADARES, ETIQUETA_AVISAR_INCIDENCIAS, ETIQUETA_AVISAR_VELOCIDAD).forEach {
            assertTrue("falta $it", banco.hay(it))
        }
        assertEquals(2, banco.etiquetas(ETIQUETA_HABLA_PREFIJO).size)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_AVISAR_RADARES))
        assertFalse(copiloto.avisarRadares)
        assertTrue(copiloto.avisarIncidencias && copiloto.avisarVelocidad)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_AVISAR_VELOCIDAD))
        assertFalse(copiloto.avisarVelocidad)
        assertTrue(copiloto.avisarIncidencias)
        assertTrue("la hoja sigue abierta", banco.hay(ETIQUETA_RAPIDOS_PANEL))
    }

    @Test
    fun `el interruptor de salida del cronometro alterna entre automatica y manual en vivo`() {
        abrir()
        val nodo = banco.conEtiqueta(ETIQUETA_SALIDA_MANUAL)
        assertTrue(banco.esPulsable(nodo))
        assertEquals(false, marcado(ETIQUETA_SALIDA_MANUAL))
        assertEquals("Automática", banco.descripcionEstado(ETIQUETA_SALIDA_MANUAL))
        assertEquals("Salida del cronómetro", banco.descripcion(nodo))

        banco.pulsar(nodo)
        assertTrue(copiloto.salidaManual)
        assertEquals(true, marcado(ETIQUETA_SALIDA_MANUAL))
        assertEquals("Manual", banco.descripcionEstado(ETIQUETA_SALIDA_MANUAL))
        assertTrue("la hoja sigue abierta", banco.hay(ETIQUETA_RAPIDOS_PANEL))

        banco.pulsar(banco.conEtiqueta(ETIQUETA_SALIDA_MANUAL))
        assertFalse(copiloto.salidaManual)
        assertEquals("Automática", banco.descripcionEstado(ETIQUETA_SALIDA_MANUAL))
    }

    @Test
    fun `el volumen se cambia con el deslizador y la cifra sigue al valor`() {
        abrir()
        assertEquals("100 %", textoDe(ETIQUETA_AJUSTES_VOLUMEN_VALOR))
        val progreso =
            checkNotNull(banco.conEtiqueta(ETIQUETA_AJUSTES_VOLUMEN).config.getOrNull(SemanticsActions.SetProgress))
        progreso.action?.invoke(80f)
        banco.reposo()
        assertEquals(80, copiloto.volumenVoz)
        assertEquals("80 %", textoDe(ETIQUETA_AJUSTES_VOLUMEN_VALOR))
    }

    @Test
    fun `escuchar ejemplo desde la hoja lo pide`() {
        abrir()
        banco.pulsar(banco.conEtiqueta(ETIQUETA_PREVISTA_ESCUCHAR))
        assertEquals(1, escuchas.size)
    }

    @Test
    fun `en vertical la hoja entra en pantalla con objetivos de 48 dp`() {
        comprobarQueLaHojaEntraEnPantalla()
    }

    @Test
    fun `la hoja de ajustes rapidos y las de capas y modo no estan abiertas a la vez`() {
        abrir()
        banco.pulsar(banco.conEtiqueta(ETIQUETA_CAPAS_BOTON))
        assertTrue(banco.hay(ETIQUETA_CAPAS_PANEL))
        assertFalse(banco.hay(ETIQUETA_RAPIDOS_PANEL))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
class AjustesRapidosHorizontalTest : AjustesRapidosBase() {
    @Test
    fun `en horizontal la hoja entra en pantalla con objetivos de 48 dp`() {
        comprobarQueLaHojaEntraEnPantalla(horizontal = true)
    }
}
