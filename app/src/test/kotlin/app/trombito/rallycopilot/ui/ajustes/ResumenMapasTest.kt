package app.trombito.rallycopilot.ui.ajustes

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El texto de Ajustes > Datos dice lo que hay de verdad: con y sin satélite, con complementos y sin nada. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ResumenMapasTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun instalados(
        satelite: Boolean = false,
        relieve: Boolean = false,
        voz: Boolean = false,
    ) = EstadoDatosMapa(
        instalados = DatosInstalados("1", 10L, conSatelite = satelite, conRelieve = relieve, conVozSharvard = voz),
    )

    private fun detalle(resumen: ResumenMapas?): String {
        banco.poner {
            RallyCopilotTheme {
                AjustesSeccionScreen(SeccionAjustes.DATOS, AjustesConduccion(), AccionesAjustes(), mapas = resumen)
            }
        }
        return banco.todosLosTextos().first { it.contains("Mallorca") || it.contains("mapas descargados") }
    }

    @Test
    fun `el resumen sale del estado de los datos`() {
        assertEquals(ResumenMapas(instalado = false), ResumenMapas.de(EstadoDatosMapa()))
        assertEquals(ResumenMapas(instalado = true, satelite = true), ResumenMapas.de(instalados(satelite = true)))
        assertEquals(
            ResumenMapas(instalado = true, relieve = true, vozSharvard = true),
            ResumenMapas.de(instalados(relieve = true, voz = true)),
        )
    }

    @Test
    fun `mientras se siembra aun no se sabe`() {
        assertNull(ResumenMapas.de(EstadoDatosMapa(preparando = true)))
    }

    @Test
    fun `sin satelite no dice que esta descargado`() {
        assertEquals("Mapa de Mallorca descargado. Sin satélite.", detalle(ResumenMapas(instalado = true)))
    }

    @Test
    fun `con satelite lo dice`() {
        assertEquals(
            "Mapa de Mallorca y satélite descargados.",
            detalle(ResumenMapas(instalado = true, satelite = true)),
        )
    }

    @Test
    fun `con complementos los suma`() {
        val texto = detalle(ResumenMapas(instalado = true, satelite = true, relieve = true, vozSharvard = true))
        assertTrue(texto, texto.startsWith("Mapa de Mallorca y satélite descargados."))
        assertTrue(texto, texto.endsWith("Además: relieve y voz Sharvard."))
    }

    @Test
    fun `sin mapas lo dice`() {
        assertEquals("Aún no hay mapas descargados.", detalle(ResumenMapas(instalado = false)))
    }
}
