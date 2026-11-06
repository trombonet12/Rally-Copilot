package app.trombito.rallycopilot.ui.ajustes

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ETIQUETA_VOLVER
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
class AjustesYAcercaDeTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    @Test
    fun `datos ofrece mapas offline y lo abre`() {
        var mapas = 0
        banco.ponerAjustes(seccion = SeccionAjustes.DATOS, acciones = AccionesAjustes(onMapasOffline = { mapas++ }))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_AJUSTES_MAPAS))
        assertEquals(1, mapas)
        assertTrue("Mapas offline" in banco.textos(banco.raiz()))
    }

    @Test
    fun `una subpantalla vuelve`() {
        var volver = 0
        banco.poner {
            RallyCopilotTheme {
                AjustesSeccionScreen(
                    SeccionAjustes.TEMA,
                    AjustesConduccion(),
                    AccionesAjustes(onVolver = { volver++ }),
                )
            }
        }
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        assertEquals(1, volver)
    }

    @Test
    fun `acerca de muestra la cabecera con mascota version y licencia`() {
        banco.poner { RallyCopilotTheme { AcercaDeScreen(version = "0.1.42", onVolver = {}) } }
        val textos = banco.textos(banco.raiz())
        assertTrue("Versión 0.1.42" in textos)
        assertTrue("GPL-3.0" in textos)
        assertTrue(textos.any { "software libre bajo la licencia GPL-3.0" in it })
        val mascota = banco.nodos().firstOrNull { banco.descripcion(it)?.startsWith("Trombito") == true }
        assertTrue("falta la mascota", mascota != null)
        assertTrue("96 dp a xxhdpi", mascota!!.size.width == 96 * 3)
    }

    @Test
    fun `las atribuciones son filas plegables con el texto completo al desplegar`() {
        banco.poner { RallyCopilotTheme { AcercaDeScreen(version = "0.1.42", onVolver = {}) } }
        val filas = banco.etiquetas(ETIQUETA_ATRIBUCION_PREFIJO)
        assertEquals(10, filas.size)
        assertFalse("plegadas no enseñan el detalle", banco.todosLosTextos().any { "contributors" in it })
        filas.forEach { banco.pulsar(banco.conEtiqueta(it)) }
        val atribuciones = banco.textos(banco.conEtiqueta(ETIQUETA_ATRIBUCIONES))
        val claves =
            listOf(
                "OpenStreetMap",
                "ODbL",
                "PNOA",
                "CC BY 4.0 scne.es",
                "Dirección General de Tráfico",
                "Protomaps",
                "BSD-3",
                "CC0",
                "Noto Sans",
                "OFL",
                "MapLibre Native",
                "BSD-2",
                "sherpa-onnx",
                "Apache-2.0",
                "Vosk",
                "Alpha Cephei",
                "espeak-ng",
                "GPL-3.0",
                "Sharvard corpus",
                "University of Edinburgh",
                "CC BY 3.0",
            )
        claves.forEach { clave -> assertTrue("Falta $clave en $atribuciones", atribuciones.any { clave in it }) }
        assertEquals(ATRIBUCIONES.size * 2, atribuciones.size)
    }

    @Test
    fun `una atribucion se pliega al tocarla otra vez`() {
        banco.poner { RallyCopilotTheme { AcercaDeScreen(version = "1", onVolver = {}) } }
        val fila = banco.conEtiqueta(ETIQUETA_ATRIBUCION_PREFIJO + 0)
        banco.pulsar(fila)
        assertTrue(banco.todosLosTextos().any { "OpenStreetMap contributors" in it })
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ATRIBUCION_PREFIJO + 0))
        assertFalse(banco.todosLosTextos().any { "OpenStreetMap contributors" in it })
    }
}
