package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_VOZ_OPCION_PREFIJO
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_VOZ_PROBAR_PREFIJO
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

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AjustesConduccionScreenTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun poner(
        ajustes: AjustesConduccion = AjustesConduccion(),
        acciones: AccionesAjustes = AccionesAjustes(),
    ) = banco.poner { RallyCopilotTheme { AjustesScreen(ajustes, acciones) } }

    @Test
    fun `el zoom ofrece cinco niveles con icono y sin texto y marca el elegido`() {
        poner(AjustesConduccion(nivelZoom = 1))
        val etiquetas = banco.etiquetas(ETIQUETA_ZOOM_PREFIJO)
        assertEquals(5, etiquetas.size)
        etiquetas.forEach { assertTrue(banco.textos(banco.conEtiqueta(it)).isEmpty()) }
        assertEquals("Mucho más lejos", banco.descripcion(banco.conEtiqueta(ETIQUETA_ZOOM_PREFIJO + -2)))
        assertEquals("Mucho más cerca", banco.descripcion(banco.conEtiqueta(ETIQUETA_ZOOM_PREFIJO + 2)))
    }

    @Test
    fun `elegir un nivel de zoom lo pide`() {
        val pedidos = mutableListOf<Int>()
        poner(acciones = AccionesAjustes(onNivelZoom = { pedidos += it }))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ZOOM_PREFIJO + -2))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ZOOM_PREFIJO + 2))
        assertEquals(listOf(-2, 2), pedidos)
    }

    @Test
    fun `hay una miniatura por icono de posicion y se puede elegir`() {
        val pedidos = mutableListOf<IconoPosicion>()
        poner(acciones = AccionesAjustes(onIcono = { pedidos += it }))
        assertEquals(IconoPosicion.entries.size, banco.etiquetas(ETIQUETA_ICONO_PREFIJO).size)
        IconoPosicion.entries.forEach { icono ->
            val nodo = banco.conEtiqueta(ETIQUETA_ICONO_PREFIJO + icono.name.lowercase())
            assertTrue(banco.textos(nodo).isEmpty())
            assertFalse(banco.descripcion(nodo).isNullOrBlank())
            assertTrue("La miniatura de $icono es un objetivo táctil de 48 dp", nodo.size.width >= 48 * 3)
            banco.pulsar(nodo)
        }
        assertEquals(IconoPosicion.entries.toList(), pedidos)
    }

    @Test
    fun `ya no hay selector del estilo de maniobras`() {
        poner()
        assertTrue(banco.etiquetas("ajustes-maniobras-").isEmpty())
    }

    @Test
    fun `la orientacion se elige con dos iconos`() {
        val pedidas = mutableListOf<Orientacion>()
        poner(acciones = AccionesAjustes(onOrientacion = { pedidas += it }))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ORIENTACION_NORTE))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ORIENTACION_RUMBO))
        assertEquals(listOf(Orientacion.NORTE, Orientacion.RUMBO), pedidas)
        assertTrue(banco.textos(banco.conEtiqueta(ETIQUETA_ORIENTACION_NORTE)).isEmpty())
    }

    @Test
    fun `la anticipacion ofrece cinco niveles con icono y sin texto y marca el elegido`() {
        poner(AjustesConduccion(anticipacion = NivelAnticipacion.TARDE))
        val etiquetas = banco.etiquetas(ETIQUETA_ANTICIPACION_PREFIJO)
        assertEquals(NivelAnticipacion.entries.size, etiquetas.size)
        etiquetas.forEach {
            val nodo = banco.conEtiqueta(it)
            assertTrue(banco.textos(nodo).isEmpty())
            assertFalse(banco.descripcion(nodo).isNullOrBlank())
            assertTrue("$it >= 48 dp", nodo.size.width >= 48 * 3 && nodo.size.height >= 48 * 3)
        }
        assertEquals("Muy pronto", banco.descripcion(banco.conEtiqueta(ETIQUETA_ANTICIPACION_PREFIJO + "muy_pronto")))
        val elegido = banco.conEtiqueta(ETIQUETA_ANTICIPACION_PREFIJO + "tarde")
        assertEquals("Seleccionado", elegido.config.getOrNull(SemanticsProperties.StateDescription))
        val otro = banco.conEtiqueta(ETIQUETA_ANTICIPACION_PREFIJO + "normal")
        assertNull(otro.config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test
    fun `elegir un nivel de anticipacion lo pide`() {
        val pedidos = mutableListOf<NivelAnticipacion>()
        poner(acciones = AccionesAjustes(onAnticipacion = { pedidos += it }))
        NivelAnticipacion.entries.forEach {
            banco.pulsar(banco.conEtiqueta(ETIQUETA_ANTICIPACION_PREFIJO + it.name.lowercase()))
        }
        assertEquals(NivelAnticipacion.entries.toList(), pedidos)
    }

    @Test
    fun `las notas simplificadas son un interruptor que pide el cambio`() {
        val cambios = mutableListOf<Boolean>()
        poner(acciones = AccionesAjustes(onNotasSimplificadas = { cambios += it }))
        val fila = banco.conEtiqueta(ETIQUETA_NOTAS_SIMPLIFICADAS)
        assertTrue("Notas simplificadas" in banco.textos(fila))
        banco.pulsar(fila.children.first { banco.esPulsable(it) })
        assertEquals(listOf(true), cambios)
    }

    @Test
    fun `el copiloto ofrece cuatro modos con icono y texto y marca el elegido`() {
        poner(AjustesConduccion(modoCopiloto = ModoCopiloto.NOTAS))
        val etiquetas = banco.etiquetas(ETIQUETA_AJUSTES_MODO_PREFIJO)
        assertEquals(ModoCopiloto.entries.size, etiquetas.size)
        val textos = banco.textos(banco.raiz())
        listOf("Silencio", "Solo alertas", "Notas", "Completo").forEach { assertTrue("Falta $it", it in textos) }
        etiquetas.forEach { assertTrue("$it >= 48 dp", banco.conEtiqueta(it).size.height >= 48 * 3) }
        val elegido = banco.conEtiqueta(ETIQUETA_AJUSTES_MODO_PREFIJO + "notas")
        assertEquals(true, elegido.config.getOrNull(SemanticsProperties.Selected))
        val otro = banco.conEtiqueta(ETIQUETA_AJUSTES_MODO_PREFIJO + "completo")
        assertEquals(false, otro.config.getOrNull(SemanticsProperties.Selected))
    }

    @Test
    fun `elegir un modo en ajustes lo pide`() {
        val pedidos = mutableListOf<ModoCopiloto>()
        poner(acciones = AccionesAjustes(onModoCopiloto = { pedidos += it }))
        ModoCopiloto.entries.forEach {
            banco.pulsar(banco.conEtiqueta(ETIQUETA_AJUSTES_MODO_PREFIJO + it.name.lowercase()))
        }
        assertEquals(ModoCopiloto.entries.toList(), pedidos)
    }

    @Test
    fun `la voz ofrece dos opciones con probar y avisa si no hay ninguna elegida`() {
        poner(AjustesConduccion(voz = null))
        assertEquals(VozCopiloto.entries.size, banco.etiquetas(ETIQUETA_VOZ_OPCION_PREFIJO).size)
        assertTrue(banco.hay(ETIQUETA_AJUSTES_VOZ_SIN_ELEGIR))
        val textos = banco.textos(banco.raiz())
        assertTrue("Voz 1" in textos && "Voz 2" in textos)
        VozCopiloto.entries.forEach {
            val probar = banco.conEtiqueta(ETIQUETA_VOZ_PROBAR_PREFIJO + it.name.lowercase())
            assertTrue(banco.textos(probar).isEmpty())
            assertTrue(probar.size.width >= 48 * 3 && probar.size.height >= 48 * 3)
        }
    }

    @Test
    fun `con voz elegida no sale el aviso y se marca la elegida`() {
        poner(AjustesConduccion(voz = VozCopiloto.SHARVARD))
        assertFalse(banco.hay(ETIQUETA_AJUSTES_VOZ_SIN_ELEGIR))
        val elegida = banco.conEtiqueta(ETIQUETA_VOZ_OPCION_PREFIJO + "sharvard")
        assertEquals(true, elegida.config.getOrNull(SemanticsProperties.Selected))
        val otra = banco.conEtiqueta(ETIQUETA_VOZ_OPCION_PREFIJO + "davefx")
        assertEquals(false, otra.config.getOrNull(SemanticsProperties.Selected))
    }

    @Test
    fun `elegir y probar una voz en ajustes llaman a sus acciones`() {
        val elegidas = mutableListOf<VozCopiloto>()
        val probadas = mutableListOf<VozCopiloto>()
        poner(acciones = AccionesAjustes(onVozCopiloto = { elegidas += it }, onProbarVoz = { probadas += it }))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOZ_OPCION_PREFIJO + "sharvard"))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOZ_PROBAR_PREFIJO + "davefx"))
        assertEquals(listOf(VozCopiloto.SHARVARD), elegidas)
        assertEquals(listOf(VozCopiloto.DAVEFX), probadas)
    }

    @Test
    fun `el interruptor de notas simplificadas refleja el ajuste`() {
        poner(AjustesConduccion(notasSimplificadas = true))
        val estados =
            banco.conEtiqueta(ETIQUETA_NOTAS_SIMPLIFICADAS).children.mapNotNull {
                it.config.getOrNull(SemanticsProperties.ToggleableState)
            }
        assertEquals(listOf(ToggleableState.On), estados)
    }
}
