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

/** La sección «Notas por revisar» del detalle del tramo. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class NotasPorRevisarTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private val notas =
        listOf(
            FilaNotaPorRevisar(1_200.0, "derecha cuatro larga"),
            FilaNotaPorRevisar(3_450.0, null),
        )

    private val base =
        DetalleTramoUiState(
            cargando = false,
            tramo = resumenFalso("t1", "Ma-2141 · Escorca", pasadas = 0, mejorMs = null),
        )

    @Test
    fun `cada nota enseña su kilometro y el texto si se conoce`() {
        banco.poner { RallyCopilotTheme { LazyColumn { seccionNotasPorRevisar(notas) } } }
        val textos = banco.todosLosTextos()
        assertTrue("Notas por revisar" in textos)
        assertEquals(
            listOf("km 1,2", "derecha cuatro larga"),
            banco.textos(banco.conEtiqueta(ETIQUETA_NOTA_REVISAR_FILA_PREFIJO + 0)),
        )
        assertEquals(
            listOf("km 3,5"),
            banco.textos(banco.conEtiqueta(ETIQUETA_NOTA_REVISAR_FILA_PREFIJO + 1)),
        )
        assertEquals(
            "Nota por revisar en el kilómetro 1,2: derecha cuatro larga.",
            banco.descripcion(banco.conEtiqueta(ETIQUETA_NOTA_REVISAR_FILA_PREFIJO + 0)),
        )
        assertEquals(
            "Nota por revisar en el kilómetro 3,5.",
            banco.descripcion(banco.conEtiqueta(ETIQUETA_NOTA_REVISAR_FILA_PREFIJO + 1)),
        )
    }

    @Test
    fun `sin notas por revisar la seccion no se muestra`() {
        banco.poner { RallyCopilotTheme { LazyColumn { seccionNotasPorRevisar(emptyList()) } } }
        assertFalse(banco.hay(ETIQUETA_NOTAS_REVISAR_TITULO))
        assertFalse("Notas por revisar" in banco.todosLosTextos())
    }

    @Test
    fun `el detalle del tramo la incluye cuando hay notas por revisar y no cuando no`() {
        banco.poner {
            RallyCopilotTheme {
                DetalleTramoScreen(base.copy(notasPorRevisar = notas), AccionesDetalleTramo())
            }
        }
        assertTrue(banco.hay(ETIQUETA_NOTAS_REVISAR_TITULO))
        assertTrue(banco.hay(ETIQUETA_NOTA_REVISAR_FILA_PREFIJO + 0))
    }

    @Test
    fun `el detalle sin notas por revisar no tiene la seccion`() {
        banco.poner { RallyCopilotTheme { DetalleTramoScreen(base, AccionesDetalleTramo()) } }
        assertFalse(banco.hay(ETIQUETA_NOTAS_REVISAR_TITULO))
    }
}
