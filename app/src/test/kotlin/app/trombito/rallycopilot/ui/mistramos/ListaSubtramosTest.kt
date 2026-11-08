package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.lazy.LazyColumn
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.comun.resumenFalso
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La sección «Subtramos» del detalle del tramo. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ListaSubtramosTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private val subtramos =
        listOf(
            FilaSubtramo(0, 1_762_000_000_000L, 1_200.0, 3_600.0, 112_300L, -3_100L),
            FilaSubtramo(1, 1_761_000_000_000L, 0.0, 1_500.0, 70_000L, null),
            FilaSubtramo(2, 1_760_000_000_000L, 500.0, 900.0, 25_000L, 1_200L),
        )

    @Test
    fun `cada subtramo enseña fecha, rango en kilometros, tiempo y delta`() {
        banco.poner { RallyCopilotTheme { LazyColumn { seccionSubtramos(subtramos) } } }
        val textos = banco.todosLosTextos()
        assertTrue("Subtramos" in textos)
        assertTrue("km 1,2 → 3,6" in textos)
        assertTrue("km 0,0 → 1,5" in textos)
        assertTrue("01:52,3" in textos)
        assertTrue("−3,1" in textos)
        assertTrue("+1,2" in textos)
        assertTrue("Primera vez" in textos)
        assertEquals(
            "Subtramo del kilómetro 1,2 al 3,6, ${formatearFechaHora(1_762_000_000_000L)}. " +
                "Tiempo: 1 minuto 52,3 segundos. Respecto al mejor de entonces: menos 3,1 segundos.",
            banco.descripcion(banco.conEtiqueta(ETIQUETA_SUBTRAMO_FILA_PREFIJO + 0)),
        )
        assertTrue(
            banco
                .descripcion(banco.conEtiqueta(ETIQUETA_SUBTRAMO_FILA_PREFIJO + 1))!!
                .endsWith("Primera vez en este trozo."),
        )
    }

    @Test
    fun `sin subtramos la seccion no se muestra`() {
        banco.poner { RallyCopilotTheme { LazyColumn { seccionSubtramos(emptyList()) } } }
        assertFalse("Subtramos" in banco.todosLosTextos())
        assertFalse(banco.hay(ETIQUETA_SUBTRAMOS_TITULO))
    }

    @Test
    fun `el detalle del tramo la incluye cuando hay subtramos y no cuando no`() {
        val tramo = resumenFalso("t1", "Ma-2141 · Escorca", pasadas = 0, mejorMs = null)
        val base = DetalleTramoUiState(cargando = false, tramo = tramo)
        banco.poner {
            RallyCopilotTheme { DetalleTramoScreen(base.copy(subtramos = subtramos), AccionesDetalleTramo()) }
        }
        assertTrue(banco.hay(ETIQUETA_SUBTRAMOS_TITULO))
        assertTrue(banco.hay(ETIQUETA_SUBTRAMO_FILA_PREFIJO + 0))
    }

    @Test
    fun `el detalle sin subtramos no tiene la seccion`() {
        val tramo = resumenFalso("t1", "Ma-2141 · Escorca", pasadas = 0, mejorMs = null)
        banco.poner {
            RallyCopilotTheme { DetalleTramoScreen(DetalleTramoUiState(false, tramo), AccionesDetalleTramo()) }
        }
        assertFalse(banco.hay(ETIQUETA_SUBTRAMOS_TITULO))
    }
}
