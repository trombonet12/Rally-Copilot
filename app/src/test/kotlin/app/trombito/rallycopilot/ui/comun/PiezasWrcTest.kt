package app.trombito.rallycopilot.ui.comun

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val DENSIDAD = 3

/** Piezas comunes del sistema visual H29b: cabecera, tarjeta de dato, fila, selector segmentado y estado vacío. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PiezasWrcTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun dp(valor: Int) = valor * DENSIDAD

    private fun poner(
        oscuro: Boolean = true,
        contenido: @androidx.compose.runtime.Composable () -> Unit,
    ) = banco.poner { RallyCopilotTheme(darkTheme = oscuro) { contenido() } }

    @Test
    fun `tarjeta de dato muestra etiqueta, cifra y unidad y se lee como un solo nodo`() {
        listOf(true, false).forEach { oscuro ->
            poner(oscuro) {
                TarjetaDato(
                    cifra = "12,4",
                    etiqueta = "Distancia",
                    modifier = Modifier.testTag("dato"),
                    icono = Iconos.Bandera,
                    unidad = "km",
                    biselada = true,
                )
            }
            val nodo = banco.conEtiqueta("dato")
            assertTrue(banco.textos(nodo).containsAll(listOf("12,4", "Distancia", "km")))
            assertTrue(nodo.size.height >= dp(48))
        }
    }

    @Test
    fun `tarjeta de dato sin icono ni unidad compone`() {
        poner { TarjetaDato(cifra = "3", etiqueta = "Tramos", modifier = Modifier.testTag("dato")) }
        assertEquals(listOf("Tramos", "3"), banco.textos(banco.conEtiqueta("dato")))
    }

    @Test
    fun `tarjeta de dato con frase muestra la frase en vez de la cifra`() {
        poner {
            TarjetaDatoFrase(
                frase = "Ritmo estable en curvas lentas",
                etiqueta = "Estilo",
                modifier = Modifier.testTag("frase"),
                icono = Iconos.Curvas,
                variante = VarianteTarjeta.MAPA,
            )
        }
        val textos = banco.textos(banco.conEtiqueta("frase"))
        assertTrue(textos.containsAll(listOf("Ritmo estable en curvas lentas", "Estilo")))
    }

    @Test
    fun `fila mide al menos 56 dp, muestra sus partes y se pulsa`() {
        var pulsaciones = 0
        poner {
            FilaWrc(
                titulo = "Notas",
                modifier = Modifier.testTag("fila"),
                detalle = "Qué significan",
                icono = Iconos.Notas,
                valor = "Activas",
                navega = true,
                onClick = { pulsaciones++ },
            )
        }
        val nodo = banco.conEtiqueta("fila")
        assertTrue(nodo.size.height >= dp(56))
        assertTrue(banco.textos(nodo).containsAll(listOf("Notas", "Qué significan", "Activas")))
        assertTrue(banco.esPulsable(nodo))
        banco.pulsar(nodo)
        assertEquals(1, pulsaciones)
    }

    @Test
    fun `fila informativa no es pulsable y una deshabilitada no responde`() {
        poner {
            FilaWrc(titulo = "Versión", modifier = Modifier.testTag("info"), valor = "1.0")
            FilaWrc(titulo = "Apagada", modifier = Modifier.testTag("apagada"), habilitada = false, onClick = {})
        }
        assertFalse(banco.esPulsable(banco.conEtiqueta("info")))
        assertFalse(banco.estaHabilitado(banco.conEtiqueta("apagada")))
        assertTrue(banco.conEtiqueta("info").size.height >= dp(56))
    }

    @Test
    fun `selector segmentado marca la opcion elegida y avisa al elegir otra`() {
        var elegida = "a"
        poner {
            SelectorSegmentadoWrc(
                opciones =
                    listOf(
                        OpcionSegmento("a", "Mapa", Iconos.Rayo),
                        OpcionSegmento("b", "Satélite"),
                    ),
                seleccionada = elegida,
                onSeleccion = { elegida = it },
                modifier = Modifier.testTag("selector"),
            )
        }
        val primero = banco.conEtiqueta("${ETIQUETA_SEGMENTO}0")
        val segundo = banco.conEtiqueta("${ETIQUETA_SEGMENTO}1")
        assertEquals(true, primero.config.getOrNull(SemanticsProperties.Selected))
        assertEquals(false, segundo.config.getOrNull(SemanticsProperties.Selected))
        assertTrue(primero.size.height >= dp(48) && segundo.size.height >= dp(48))
        assertTrue(banco.textos(banco.conEtiqueta("selector")).containsAll(listOf("Mapa", "Satélite")))
        banco.pulsar(segundo)
        assertEquals("b", elegida)
    }

    @Test
    fun `estado vacio con ilustracion la pinta en lugar del icono y es decorativa`() {
        poner {
            EstadoVacioWrc(
                icono = Iconos.Bandera,
                titulo = "Sin tramos",
                texto = "Graba el primero",
                etiqueta = "vacio",
                ilustracion = { Box(Modifier.size(80.dp).testTag("dibujo")) },
            )
        }
        assertTrue(banco.hay(ETIQUETA_ILUSTRACION_VACIO))
        assertTrue(banco.hay("dibujo"))
        assertTrue(banco.textos(banco.conEtiqueta("vacio")).containsAll(listOf("Sin tramos", "Graba el primero")))
    }

    @Test
    fun `estado vacio sin ilustracion no deja hueco y las variantes componen con accion`() {
        var pulsaciones = 0
        poner { EstadoVacioWrc(Iconos.Bandera, "Sin tramos", "Nada", "vacio") }
        assertFalse(banco.hay(ETIQUETA_ILUSTRACION_VACIO))
        VarianteVacio.entries.forEach { variante ->
            poner(oscuro = variante != VarianteVacio.ERROR) {
                EstadoVacioWrc(
                    icono = Iconos.Bandera,
                    titulo = "Titulo",
                    texto = "Texto",
                    etiqueta = "v-$variante",
                    etiquetaAccion = "Reintentar",
                    onAccion = { pulsaciones++ },
                    variante = variante,
                )
            }
            banco.pulsar(banco.conEtiqueta(ETIQUETA_ACCION_VACIO))
        }
        assertEquals(VarianteVacio.entries.size, pulsaciones)
    }

    @Test
    fun `pantalla wrc pone el fondo, la cabecera con subtitulo y franja, y el contenido`() {
        var atras = 0
        poner {
            PantallaWrc(titulo = "Mis tramos", onVolver = { atras++ }, subtitulo = "3 tramos") {
                Text("Contenido", Modifier.fillMaxWidth().testTag("cuerpo"))
            }
        }
        assertTrue(banco.hay(ETIQUETA_FONDO_WRC))
        assertTrue(banco.hay(ETIQUETA_FRANJA_CABECERA))
        val textos = banco.todosLosTextos()
        assertTrue(textos.toString(), textos.containsAll(listOf("MIS TRAMOS", "3 tramos", "Contenido")))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        assertEquals(1, atras)
    }

    @Test
    fun `barra superior sin subtitulo ni franja solo pinta el titulo`() {
        poner { BarraSuperior(titulo = "Ajustes", onVolver = {}, conFranja = false) }
        assertFalse(banco.hay(ETIQUETA_FRANJA_CABECERA))
        assertFalse(banco.hay(ETIQUETA_SUBTITULO_CABECERA))
        assertTrue(banco.todosLosTextos().contains("AJUSTES"))
    }

    @Test
    fun `transiciones sin movimiento no animan y con movimiento si`() {
        assertEquals(EnterTransition.None, TransicionesWrc.entrada(true))
        assertEquals(ExitTransition.None, TransicionesWrc.salida(true))
        assertEquals(EnterTransition.None, TransicionesWrc.entradaAtras(true))
        assertEquals(ExitTransition.None, TransicionesWrc.salidaAtras(true))
        assertFalse(TransicionesWrc.entrada(false) == EnterTransition.None)
        assertFalse(TransicionesWrc.salida(false) == ExitTransition.None)
    }
}
