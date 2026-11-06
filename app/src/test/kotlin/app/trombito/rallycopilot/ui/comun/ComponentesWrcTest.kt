package app.trombito.rallycopilot.ui.comun

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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

private const val DENSIDAD = 3

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ComponentesWrcTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun dp(valor: Int) = valor * DENSIDAD

    private fun poner(
        oscuro: Boolean = true,
        contenido: @androidx.compose.runtime.Composable () -> Unit,
    ) = banco.poner { RallyCopilotTheme(darkTheme = oscuro) { contenido() } }

    @Test
    fun `tarjeta opaca y de mapa muestran su contenido en los dos temas`() {
        listOf(true, false).forEach { oscuro ->
            poner(oscuro) {
                TarjetaWrc(Modifier.testTag("opaca")) { Text("Hola") }
            }
            assertTrue(banco.textos(banco.conEtiqueta("opaca")).contains("Hola"))
        }
    }

    @Test
    fun `tarjeta de mapa con acento, activa y biselada compone y mide`() {
        poner(oscuro = false) {
            TarjetaWrc(
                Modifier.testTag("t").fillMaxWidth(),
                variante = VarianteTarjeta.MAPA,
                activa = true,
                acentoRojo = true,
                biselada = true,
            ) { Text("Tramo") }
        }
        val nodo = banco.conEtiqueta("t")
        assertTrue(nodo.size.height >= dp(32))
        assertTrue(banco.textos(nodo).contains("Tramo"))
    }

    @Test
    fun `tarjeta pulsable llama a la accion`() {
        var pulsaciones = 0
        poner { TarjetaWrc(Modifier.testTag("t"), onClick = { pulsaciones++ }) { Text("Pulsa") } }
        val nodo = banco.conEtiqueta("t")
        assertTrue(banco.esPulsable(nodo))
        banco.pulsar(nodo)
        assertEquals(1, pulsaciones)
    }

    @Test
    fun `tarjeta sin accion no es pulsable`() {
        poner { TarjetaWrc(Modifier.testTag("t")) { Text("Fija") } }
        assertFalse(banco.esPulsable(banco.conEtiqueta("t")))
    }

    @Test
    fun `forma biselada genera un contorno`() {
        val contorno = FormaBiselada().createOutline(Size(300f, 200f), LayoutDirection.Ltr, Density(3f))
        assertTrue(contorno is Outline.Generic)
        EsquinaBiselada.entries.forEach {
            assertNotNull(FormaBiselada(esquina = it).createOutline(Size(10f, 10f), LayoutDirection.Ltr, Density(3f)))
        }
    }

    @Test
    fun `boton redondo mide 48 dp, 56 si es principal, y se describe`() {
        poner {
            BotonRedondoMapa("Capas", {}, Modifier.testTag("normal")) { Text("x") }
            BotonRedondoMapa("Empezar", {}, Modifier.testTag("principal"), principal = true) { Text("x") }
        }
        val normal = banco.conEtiqueta("normal")
        val principal = banco.conEtiqueta("principal")
        assertTrue(normal.size.width >= dp(48) && normal.size.height >= dp(48))
        assertTrue(principal.size.width >= dp(56) && principal.size.height >= dp(56))
        assertEquals("Capas", banco.descripcion(normal))
        assertTrue(normal.children.any { banco.esPulsable(it) })
    }

    @Test
    fun `boton redondo activo informa de su seleccion y la etiqueta no suma al nombre`() {
        poner {
            BotonRedondoMapa(
                descripcion = "Rumbo",
                onClick = {},
                modifier = Modifier.testTag("b"),
                activo = true,
                etiqueta = "Norte",
                descripcionEstado = "Activado",
            ) { Text("x") }
        }
        val nodo = banco.conEtiqueta("b")
        assertEquals("Rumbo", banco.descripcion(nodo))
        assertEquals(true, nodo.config.getOrNull(SemanticsProperties.Selected))
        assertEquals("Activado", banco.descripcionEstado("b"))
    }

    @Test
    fun `boton redondo deshabilitado no responde`() {
        poner { BotonRedondoMapa("Capas", {}, Modifier.testTag("b"), habilitado = false) { Text("x") } }
        assertFalse(banco.esPulsable(banco.conEtiqueta("b")) && banco.estaHabilitado(banco.conEtiqueta("b")))
    }

    @Test
    fun `pildora de velocidad describe velocidad y limite sin exceso`() {
        poner { PildoraVelocidad(velocidadKmh = 45, limiteKmh = 50) }
        val nodo = banco.conEtiqueta(ETIQUETA_PILDORA_VELOCIDAD)
        assertTrue(nodo.size.height >= dp(48))
        val descripcion = banco.descripcion(nodo).orEmpty()
        assertTrue(descripcion, descripcion.contains("45") && descripcion.contains("50"))
        assertNull(banco.descripcionEstado(ETIQUETA_PILDORA_VELOCIDAD))
        assertTrue(banco.hay(ETIQUETA_SENAL_LIMITE))
    }

    @Test
    fun `pildora de velocidad avisa del exceso en semantica y con la animacion en marcha`() {
        poner { PildoraVelocidad(velocidadKmh = 72, limiteKmh = 50) }
        banco.esperar(700)
        assertEquals("Por encima del límite", banco.descripcionEstado(ETIQUETA_PILDORA_VELOCIDAD))
        assertTrue(banco.descripcion(banco.conEtiqueta(ETIQUETA_PILDORA_VELOCIDAD)).orEmpty().contains("72"))
    }

    @Test
    fun `pildora de velocidad sin dato ni limite`() {
        poner { PildoraVelocidad(velocidadKmh = null, limiteKmh = null) }
        assertFalse(banco.hay(ETIQUETA_SENAL_LIMITE))
        assertNull(banco.descripcionEstado(ETIQUETA_PILDORA_VELOCIDAD))
    }

    @Test
    fun `senal de limite mide 40 dp`() {
        poner { SenalLimiteWrc(50, Modifier.testTag("s")) }
        val nodo = banco.conEtiqueta("s")
        assertEquals(dp(40), nodo.size.width)
        assertTrue(banco.textos(nodo).contains("50"))
    }

    @Test
    fun `hoja inferior pinta asa y contenido`() {
        poner { HojaInferiorWrc(Modifier.testTag("h")) { Text("Ajustes rapidos") } }
        assertTrue(banco.hay(ETIQUETA_ASA_HOJA))
        val asa = banco.conEtiqueta(ETIQUETA_ASA_HOJA)
        assertEquals(dp(32), asa.size.width)
        assertEquals(dp(4), asa.size.height)
        assertTrue(banco.textos(banco.conEtiqueta("h")).contains("Ajustes rapidos"))
    }

    @Test
    fun `estado vacio muestra titulo, frase y accion de 48 dp que se pulsa`() {
        var pulsaciones = 0
        poner {
            EstadoVacioWrc(
                icono = Iconos.Bandera,
                titulo = "Sin tramos",
                texto = "Graba el primero",
                etiqueta = "vacio",
                etiquetaAccion = "Grabar",
                onAccion = { pulsaciones++ },
            )
        }
        val textos = banco.textos(banco.conEtiqueta("vacio"))
        assertTrue(textos.containsAll(listOf("Sin tramos", "Graba el primero", "Grabar")))
        val accion = banco.conEtiqueta(ETIQUETA_ACCION_VACIO)
        assertTrue(accion.size.height >= dp(48))
        banco.pulsar(accion)
        assertEquals(1, pulsaciones)
    }

    @Test
    fun `estado vacio sin accion no pinta boton`() {
        poner { EstadoVacioWrc(Iconos.Bandera, "Sin tramos", "Nada", "vacio") }
        assertFalse(banco.hay(ETIQUETA_ACCION_VACIO))
    }

    @Test
    fun `cargando muestra el texto y es un solo nodo accesible`() {
        poner { CargandoWrc("Cargando mapas", Modifier) }
        assertTrue(banco.textos(banco.conEtiqueta(ETIQUETA_CARGANDO)).contains("Cargando mapas"))
    }

    @Test
    fun `lista de esqueletos pinta las filas pedidas y se describe`() {
        poner { ListaEsqueletoWrc(filas = 3) }
        val nodo = banco.conEtiqueta(ETIQUETA_ESQUELETO)
        assertEquals("Cargando la lista", banco.descripcion(nodo))
        assertTrue(nodo.size.height >= dp(72 * 3))
    }

    @Test
    fun `chip informativo no es pulsable y chip con accion mide 48 dp`() {
        var pulsaciones = 0
        poner {
            ChipIconoWrc(Iconos.Rayo, "Rapido", Modifier.testTag("info"))
            ChipIconoWrc(Iconos.Curvas, "Curvas", Modifier.testTag("accion"), seleccionado = true) { pulsaciones++ }
        }
        assertFalse(banco.esPulsable(banco.conEtiqueta("info")))
        val accion = banco.conEtiqueta("accion")
        assertTrue(accion.size.height >= dp(48))
        banco.pulsar(accion)
        assertEquals(1, pulsaciones)
        assertEquals(true, accion.config.getOrNull(SemanticsProperties.Selected))
    }

    @Test
    fun `fondo y degradado superior del mapa se componen con la altura pedida`() {
        poner(oscuro = false) {
            FondoWrc { Box(Modifier.testTag("sobre-fondo")) { Text("Inicio") } }
        }
        assertTrue(banco.hay(ETIQUETA_FONDO_WRC))
        assertTrue(banco.textos(banco.conEtiqueta(ETIQUETA_FONDO_WRC)).contains("Inicio"))
        poner { DegradadoSuperiorMapa() }
        assertEquals(dp(96), banco.conEtiqueta(ETIQUETA_DEGRADADO_MAPA).size.height)
    }
}
