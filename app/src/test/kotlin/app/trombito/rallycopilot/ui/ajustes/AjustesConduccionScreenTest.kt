package app.trombito.rallycopilot.ui.ajustes

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `la orientacion se elige con dos iconos`() {
        val pedidas = mutableListOf<Orientacion>()
        poner(acciones = AccionesAjustes(onOrientacion = { pedidas += it }))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ORIENTACION_NORTE))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ORIENTACION_RUMBO))
        assertEquals(listOf(Orientacion.NORTE, Orientacion.RUMBO), pedidas)
        assertTrue(banco.textos(banco.conEtiqueta(ETIQUETA_ORIENTACION_NORTE)).isEmpty())
    }
}
