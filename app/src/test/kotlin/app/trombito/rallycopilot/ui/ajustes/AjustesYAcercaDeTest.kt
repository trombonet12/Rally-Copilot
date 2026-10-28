package app.trombito.rallycopilot.ui.ajustes

import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ETIQUETA_VOLVER
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
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
    fun `ajustes ofrece mapas offline y acerca de`() {
        var mapas = 0
        var acerca = 0
        banco.poner {
            RallyCopilotTheme { AjustesScreen(onMapasOffline = { mapas++ }, onAcercaDe = { acerca++ }, onVolver = {}) }
        }
        banco.pulsar(banco.conEtiqueta(ETIQUETA_AJUSTES_MAPAS))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_AJUSTES_ACERCA_DE))
        assertEquals(1, mapas)
        assertEquals(1, acerca)
        val textos = banco.textos(banco.raiz())
        assertTrue("Mapas offline" in textos)
        assertTrue("Acerca de" in textos)
    }

    @Test
    fun `ajustes vuelve`() {
        var volver = 0
        banco.poner { RallyCopilotTheme { AjustesScreen({}, {}, { volver++ }) } }
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        assertEquals(1, volver)
    }

    @Test
    fun `acerca de muestra version licencia y todas las atribuciones`() {
        banco.poner { RallyCopilotTheme { AcercaDeScreen(version = "0.1.42", onVolver = {}) } }
        val textos = banco.textos(banco.raiz())
        assertTrue("Versión 0.1.42" in textos)
        assertTrue(textos.any { "GPL-3.0" in it })
        val atribuciones = banco.textos(banco.conEtiqueta(ETIQUETA_ATRIBUCIONES))
        val claves =
            listOf(
                "OpenStreetMap",
                "ODbL",
                "PNOA",
                "CC BY 4.0 scne.es",
                "Protomaps",
                "BSD-3",
                "CC0",
                "Noto Sans",
                "OFL",
                "MapLibre Native",
                "BSD-2",
            )
        claves.forEach { clave -> assertTrue("Falta $clave en $atribuciones", atribuciones.any { clave in it }) }
        assertEquals(5, atribuciones.size)
    }
}
