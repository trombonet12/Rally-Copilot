package app.trombito.rallycopilot.ui.tramos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.domain.tramos.TipoCorte
import app.trombito.rallycopilot.ui.navegacion.guiado.ColoresGuiado
import app.trombito.rallycopilot.ui.theme.RallyClaro
import app.trombito.rallycopilot.ui.theme.RallyOscuro
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FormatoCronoTest {
    private val recursos = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun `el cronometro se escribe mm ss punto d`() {
        assertEquals("02:30,4", formatearTiempo(150_400L))
        assertEquals("00:00,0", formatearTiempo(0L))
        assertEquals("00:59,9", formatearTiempo(59_999L))
        assertEquals("1:02:30,0", formatearTiempo(3_750_000L))
        assertEquals("00:00,0", formatearTiempo(-50L))
    }

    @Test
    fun `sin decimas solo minutos y segundos`() {
        assertEquals("02:30", formatearTiempo(150_400L, conDecimas = false))
    }

    @Test
    fun `el delta lleva signo y coma decimal`() {
        assertEquals("−0,3", formatearDelta(-300L))
        assertEquals("+1,2", formatearDelta(1_200L))
        assertEquals("+1:05,3", formatearDelta(65_300L))
        assertEquals("−12,0", formatearDelta(-12_000L))
    }

    @Test
    fun `la flecha del delta baja si ganas tiempo y sube si lo pierdes`() {
        assertEquals("▼", flechaDelta(-300L))
        assertEquals("▲", flechaDelta(1_200L))
        assertEquals("", flechaDelta(20L))
    }

    @Test
    fun `menos de media decima es cero sin signo`() {
        assertEquals("0,0", formatearDelta(0L))
        assertEquals("0,0", formatearDelta(49L))
        assertEquals("0,0", formatearDelta(-49L))
        assertEquals("−0,1", formatearDelta(-50L))
    }

    @Test
    fun `el delta negativo es verde, el positivo rojo y el cero neutro`() {
        assertEquals(RallyOscuro.deltaMejor, colorDelta(-300L))
        assertEquals(RallyOscuro.deltaPeor, colorDelta(1_200L))
        assertEquals(ColoresGuiado.Texto, colorDelta(0L))
        assertEquals(ColoresGuiado.Texto, colorDelta(20L))
        assertEquals(ColoresGuiado.Texto, colorDelta(null))
        assertEquals(RallyClaro.deltaMejor, colorDelta(-300L, RallyClaro))
        assertEquals(RallyClaro.deltaPeor, colorDelta(900L, RallyClaro))
    }

    @Test
    fun `el signo sale de la decima redondeada`() {
        assertEquals(SignoDelta.MEJOR, signoDelta(-60L))
        assertEquals(SignoDelta.PEOR, signoDelta(60L))
        assertEquals(SignoDelta.IGUAL, signoDelta(40L))
    }

    @Test
    fun `el tiempo se dice con minutos y segundos`() {
        assertEquals("2 minutos 30,4 segundos", descripcionTiempo(recursos, 150_400L))
        assertEquals("1 minuto 0,0 segundos", descripcionTiempo(recursos, 60_000L))
        assertEquals("30,4 segundos", descripcionTiempo(recursos, 30_400L))
    }

    @Test
    fun `el delta se dice mas o menos`() {
        assertEquals("más 1,2 segundos", descripcionDelta(recursos, 1_200L))
        assertEquals("menos 0,3 segundos", descripcionDelta(recursos, -300L))
        assertEquals("más 1 minuto 5,3 segundos", descripcionDelta(recursos, 65_300L))
        assertEquals("igual que tu mejor tiempo", descripcionDelta(recursos, 10L))
    }

    @Test
    fun `solo un tramo conocido con mejor pasada pide el HUD completo`() {
        assertEquals(VistaCrono.HUD, vistaCrono(cronoConocido()))
        assertEquals(VistaCrono.MINI, vistaCrono(cronoSinReferencia()))
        assertEquals(VistaCrono.MINI, vistaCrono(cronoSinTramo()))
        assertEquals(VistaCrono.NINGUNA, vistaCrono(null))
    }

    @Test
    fun `la fraccion de progreso se acota a cero y uno`() {
        assertEquals(0.5f, fraccionProgreso(1_995.0, 3_990.0), 0.0001f)
        assertEquals(1f, fraccionProgreso(5_000.0, 3_990.0), 0.0001f)
        assertEquals(0f, fraccionProgreso(-3.0, 3_990.0), 0.0001f)
        assertEquals(0f, fraccionProgreso(10.0, 0.0), 0.0001f)
    }

    @Test
    fun `la barra marca los tres cortes de sector (cuatro sectores) y los microsectores`() {
        val marcas = marcasDeBarra(tramoDePrueba(), listOf(sector(1, -300L), sector(2, 800L), micro(1, -100L)))
        val sectores = marcas.filter { it.tipo == TipoCorte.SECTOR }
        val micros = marcas.filter { it.tipo == TipoCorte.MICROSECTOR }
        assertEquals(3, sectores.size)
        assertEquals(2, micros.size)
        assertEquals(1_000f / 3_990f, sectores[0].fraccion, 0.0001f)
        assertEquals(listOf(SignoDelta.MEJOR, SignoDelta.PEOR, null), sectores.map { it.signo })
        assertEquals(listOf(SignoDelta.MEJOR, null), micros.map { it.signo })
    }
}
