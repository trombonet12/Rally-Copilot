package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.lazy.LazyColumn
import app.trombito.rallycopilot.domain.tramos.DatosVia
import app.trombito.rallycopilot.motor.vias.SentidoVia
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
    fun `en un tramo de via los subtramos van en kilometros de la carretera`() {
        val via = DatosVia("MA15", 20.1, 32.4, SentidoVia.CRECIENTE, "Manacor", null)
        val tramo = resumenFalso("v1", "Ma-15", longitudM = 12_300.0, via = via)
        val filas = listOf(FilaSubtramo(0, 1_762_000_000_000L, 0.0, 6_150.0, 112_300L, null).enKmDeCarretera(tramo))
        banco.poner { RallyCopilotTheme { LazyColumn { seccionSubtramos(filas) } } }
        // La mitad del tramo (6,15 km) cae a mitad del rango 20,1 → 32,4.
        assertTrue("km 20,1 → 26,3" in banco.todosLosTextos())
        assertTrue(
            banco
                .descripcion(banco.conEtiqueta(ETIQUETA_SUBTRAMO_FILA_PREFIJO + 0))!!
                .startsWith("Subtramo del kilómetro 20,1 al 26,3,"),
        )
    }

    @Test
    fun `con kilometros decrecientes el subtramo cuenta hacia atras desde la salida`() {
        val via = DatosVia("MA15", 32.4, 20.1, SentidoVia.DECRECIENTE, "Palma", null)
        val tramo = resumenFalso("v2", "Ma-15", longitudM = 12_300.0, via = via)
        val fila = FilaSubtramo(0, 1L, 3_075.0, 12_300.0, 1_000L, null).enKmDeCarretera(tramo)
        assertEquals(29.325, fila.desdeKm!!, 1e-9)
        assertEquals(20.1, fila.hastaKm!!, 1e-9)
    }

    @Test
    fun `un viaje, o un tramo de via sin hitos, deja los kilometros desde la salida`() {
        val viaje = resumenFalso("j1", "Palma → Manacor", longitudM = 12_300.0)
        val sinHitos = resumenFalso("v3", "Ma-2", via = DatosVia("MA2", null, null, SentidoVia.CRECIENTE, null, null))
        val base = FilaSubtramo(0, 1L, 1_200.0, 3_600.0, 1_000L, null)
        assertEquals(base, base.enKmDeCarretera(viaje))
        assertEquals(base, base.enKmDeCarretera(sinHitos))
        assertEquals(base, base.enKmDeCarretera(null))
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
