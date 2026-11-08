package app.trombito.rallycopilot.ui.navegacion.notas

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.domain.escucha.ResultadoNotaMal
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
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

/** «Esta nota estaba mal»: pulsación larga sobre la nota actual del carrete. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CarreteNotaMalTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private val estado =
        NotasUiState(
            visibles = listOf(visible(deNota(nota(3)), activa = true), visible(deNota(nota(5, posicionM = 400.0)))),
        )

    private var anotadas = 0
    private var resultado = ResultadoNotaMal.ANOTADA

    private fun poner(conAccion: Boolean = true) {
        val accion =
            AccionNotaMal {
                anotadas++
                resultado
            }
        banco.poner {
            RallyCopilotTheme {
                CompositionLocalProvider(LocalAccionNotaMal provides accion.takeIf { conAccion }) {
                    CarreteNotas(estado)
                }
            }
        }
    }

    @Test
    fun `la pulsacion larga anota la nota y confirma brevemente`() {
        poner()

        banco.mantener(banco.conEtiqueta(ETIQUETA_NOTA_PREFIJO + 0))

        assertEquals(1, anotadas)
        assertTrue(banco.hay(ETIQUETA_NOTA_MAL_CONFIRMACION))
        assertEquals(listOf("Nota anotada"), banco.textos(banco.conEtiqueta(ETIQUETA_NOTA_MAL_CONFIRMACION)))
        assertEquals("Nota anotada", banco.descripcion(banco.conEtiqueta(ETIQUETA_NOTA_PREFIJO + 0)))

        banco.esperarEnReal(2_000L)

        assertFalse(banco.hay(ETIQUETA_NOTA_MAL_CONFIRMACION))
        assertTrue(banco.hay(ETIQUETA_NOTA_DISTANCIA_PREFIJO + 0))
    }

    @Test
    fun `sin nota reciente la confirmacion dice que no hay nota`() {
        resultado = ResultadoNotaMal.SIN_NOTA
        poner()

        banco.mantener(banco.conEtiqueta(ETIQUETA_NOTA_PREFIJO + 0))

        assertEquals(listOf("No hay nota"), banco.textos(banco.conEtiqueta(ETIQUETA_NOTA_MAL_CONFIRMACION)))
    }

    @Test
    fun `un toque corto no anota nada`() {
        poner()

        banco.mantener(banco.conEtiqueta(ETIQUETA_NOTA_PREFIJO + 0), ms = 50L)

        assertEquals(0, anotadas)
        assertFalse(banco.hay(ETIQUETA_NOTA_MAL_CONFIRMACION))
    }

    @Test
    fun `la accion personalizada de accesibilidad hace lo mismo`() {
        poner()
        val acciones = banco.conEtiqueta(ETIQUETA_NOTA_PREFIJO + 0).config.getOrNull(SemanticsActions.CustomActions)

        assertNotNull(acciones)
        assertEquals(listOf("Marcar esta nota como mal cantada"), acciones!!.map { it.label })

        assertTrue(acciones.single().action())
        banco.reposo()

        assertEquals(1, anotadas)
        assertTrue(banco.hay(ETIQUETA_NOTA_MAL_CONFIRMACION))
    }

    @Test
    fun `sin accion disponible la pulsacion larga no hace nada ni hay accion de accesibilidad`() {
        poner(conAccion = false)

        banco.mantener(banco.conEtiqueta(ETIQUETA_NOTA_PREFIJO + 0))

        assertEquals(0, anotadas)
        assertFalse(banco.hay(ETIQUETA_NOTA_MAL_CONFIRMACION))
        assertNull(banco.conEtiqueta(ETIQUETA_NOTA_PREFIJO + 0).config.getOrNull(SemanticsActions.CustomActions))
    }
}
