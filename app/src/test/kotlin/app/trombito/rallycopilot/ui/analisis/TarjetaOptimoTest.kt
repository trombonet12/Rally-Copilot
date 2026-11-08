package app.trombito.rallycopilot.ui.analisis

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

/** La tarjeta «Óptimo teórico» y cómo el ViewModel arma su estado desde las pasadas del tramo. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TarjetaOptimoTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private val lenta = FixtureAnalisis.detallada(1, kmh = 50.0, inicioMs = 1_762_000_000_000L)
    private val rapida = FixtureAnalisis.detallada(2, kmh = 70.0, inicioMs = 1_761_000_000_000L, esMejor = true)

    private fun poner(optimo: OptimoUi) {
        banco.poner { RallyCopilotTheme { TarjetaOptimo(optimo) } }
    }

    private fun textos(etiqueta: String) = banco.textos(banco.conEtiqueta(etiqueta))

    private fun optimoConGanancia() =
        OptimoUi(
            totalMs = 150_000L,
            gananciaMs = 800L,
            sectores =
                listOf(
                    SectorOptimoUi(1, 40_000L, 1_761_000_000_000L),
                    SectorOptimoUi(2, 110_000L, 1_762_000_000_000L),
                ),
        )

    @Test
    fun `la tarjeta enseña el total, la ganancia en verde y la pasada de cada sector`() {
        poner(optimoConGanancia())
        assertTrue(banco.hay(ETIQUETA_OPTIMO))
        assertEquals(listOf("02:30,0"), textos(ETIQUETA_OPTIMO_TOTAL))
        assertEquals(listOf("−0,8 s"), textos(ETIQUETA_OPTIMO_GANANCIA))
        assertTrue(banco.hay(ETIQUETA_OPTIMO_SECTOR_PREFIJO + 1))
        assertTrue(banco.hay(ETIQUETA_OPTIMO_SECTOR_PREFIJO + 2))
        val fila = banco.textos(banco.conEtiqueta(ETIQUETA_OPTIMO_SECTOR_PREFIJO + 1))
        assertTrue("S1 y su pasada: $fila", fila.contains("S1") && fila.any { it.startsWith("Pasada del ") })
    }

    @Test
    fun `si la mejor pasada ya es el optimo lo dice sin ganancia`() {
        poner(optimoConGanancia().copy(gananciaMs = 0L))
        assertEquals(listOf("Tu mejor pasada ya es el óptimo"), textos(ETIQUETA_OPTIMO_GANANCIA))
    }

    @Test
    fun `sin pasada completa no hay ganancia que enseñar`() {
        poner(optimoConGanancia().copy(gananciaMs = null))
        assertFalse(banco.hay(ETIQUETA_OPTIMO_GANANCIA))
        assertTrue(banco.hay(ETIQUETA_OPTIMO_TOTAL))
    }

    @Test
    fun `con una sola pasada no hay optimo`() {
        assertNull(optimoUi(FixtureAnalisis.tramo, listOf(lenta)))
    }

    @Test
    fun `con dos pasadas el optimo sale de la mas rapida en todos los sectores y no gana nada`() {
        val optimo = checkNotNull(optimoUi(FixtureAnalisis.tramo, listOf(lenta, rapida)))
        assertEquals(rapida.totalMs.toDouble(), optimo.totalMs.toDouble(), 2.0)
        assertEquals(0L, optimo.gananciaMs)
        assertTrue(optimo.sectores.isNotEmpty())
        assertTrue(optimo.sectores.all { it.pasadaInicioMs == rapida.inicioMs })
        assertNotNull(optimo.sectores.firstOrNull())
    }
}
