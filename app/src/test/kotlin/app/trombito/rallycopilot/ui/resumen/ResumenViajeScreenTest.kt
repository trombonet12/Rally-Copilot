package app.trombito.rallycopilot.ui.resumen

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import app.trombito.rallycopilot.motor.analisis.ResumenViaje
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ETIQUETA_CARGANDO
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

private const val ETIQUETA_MAPA_FALSO = "resumen-mapa-falso"

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class ResumenViajeScreenTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private val estilo = "{\"version\":8}"
    private val llamadasAlMapa = mutableListOf<Pair<String, ResumenViaje>>()

    private val mapaFalso: MapaDelResumen = { estiloJson, resumen, modificador ->
        llamadasAlMapa += estiloJson to resumen
        Box(modificador.testTag(ETIQUETA_MAPA_FALSO))
    }

    private fun poner(
        estado: ResumenViajeUiState,
        alVolver: () -> Unit = {},
    ) = banco.poner { RallyCopilotTheme { ResumenViajeScreen(estado, alVolver, mapaFalso) } }

    private fun lleno(resumen: ResumenViaje = FixtureResumen.resumen()) =
        ResumenViajeUiState(cargando = false, viaje = FixtureResumen.viaje(resumen), estiloMapaJson = estilo)

    private fun textosDe(etiqueta: String) = banco.textos(banco.conEtiqueta(etiqueta))

    @Test
    fun `ensena el mapa con la escala del viaje y su leyenda`() {
        poner(lleno())
        assertTrue(banco.hay(ETIQUETA_MAPA_FALSO))
        assertEquals(estilo, llamadasAlMapa.single().first)
        assertEquals(FixtureResumen.resumen().escala, llamadasAlMapa.single().second.escala)
        assertTrue(banco.hay("analisis-leyenda"))
    }

    @Test
    fun `las tarjetas de datos dicen km, tiempo en marcha, media, total y maxima`() {
        poner(lleno())
        assertTrue(textosDe(ETIQUETA_RESUMEN_KM).containsAll(listOf("31,9", "km")))
        assertTrue(textosDe(ETIQUETA_RESUMEN_EN_MARCHA).containsAll(listOf("35:00", "min")))
        assertTrue(textosDe(ETIQUETA_RESUMEN_MEDIA).containsAll(listOf("55", "km/h")))
        assertTrue(textosDe(ETIQUETA_RESUMEN_TOTAL).contains("40:00"))
        assertTrue(textosDe(ETIQUETA_RESUMEN_MAXIMA).containsAll(listOf("98", "km/h")))
    }

    @Test
    fun `la llegada compara lo real con lo previsto y dice cuanto antes`() {
        poner(lleno())
        assertTrue(textosDe(ETIQUETA_RESUMEN_LLEGADA_REAL).contains("40:00"))
        assertTrue(textosDe(ETIQUETA_RESUMEN_LLEGADA_PREVISTA).contains("45:00"))
        assertEquals(listOf("5 min antes de lo previsto"), textosDe(ETIQUETA_RESUMEN_LLEGADA_FRASE))
    }

    @Test
    fun `llegar mas tarde y llegar a la hora tienen su frase`() {
        poner(lleno(FixtureResumen.resumen(previstoS = 2_100L)))
        assertEquals(listOf("5 min más tarde de lo previsto"), textosDe(ETIQUETA_RESUMEN_LLEGADA_FRASE))
    }

    @Test
    fun `sin tiempo previsto no hay tarjeta de llegada`() {
        poner(lleno(FixtureResumen.resumen(previstoS = null)))
        assertFalse(banco.hay(ETIQUETA_RESUMEN_LLEGADA))
    }

    @Test
    fun `las conclusiones salen como frases`() {
        poner(lleno())
        assertTrue(textosDe(ETIQUETA_RESUMEN_CONCLUSION_PREFIJO + 0).contains("Récord nuevo en Ma-10 Valldemossa"))
        assertTrue(textosDe(ETIQUETA_RESUMEN_CONCLUSION_PREFIJO + 1).contains("Llegaste 5 min antes de lo previsto"))
    }

    @Test
    fun `los tramos llevan su delta y solo el record tiene estrella`() {
        poner(lleno())
        assertTrue(banco.hay(ETIQUETA_RESUMEN_TRAMO_RECORD_PREFIJO + 0))
        assertFalse(banco.hay(ETIQUETA_RESUMEN_TRAMO_RECORD_PREFIJO + 1))
        assertTrue(textosDe(ETIQUETA_RESUMEN_TRAMO_PREFIJO + 0).containsAll(listOf("Ma-10 Valldemossa", "−2,0")))
        assertTrue(textosDe(ETIQUETA_RESUMEN_TRAMO_PREFIJO + 1).containsAll(listOf("Ma-2141 Escorca", "+1,0")))
        assertTrue(textosDe(ETIQUETA_RESUMEN_TRAMO_PREFIJO + 2).contains("Primera vez"))
    }

    @Test
    fun `las vias comparan con tu media con signo y sin historico dicen que no hay`() {
        poner(lleno())
        assertEquals(listOf("+7 %"), textosDe(ETIQUETA_RESUMEN_VIA_DIFERENCIA_PREFIJO + 0))
        assertEquals(listOf("−20 %"), textosDe(ETIQUETA_RESUMEN_VIA_DIFERENCIA_PREFIJO + 2))
        assertFalse(banco.hay(ETIQUETA_RESUMEN_VIA_DIFERENCIA_PREFIJO + 1))
        assertTrue(textosDe(ETIQUETA_RESUMEN_VIA_PREFIJO + 0).contains("Tu media: 58 km/h"))
        assertTrue(textosDe(ETIQUETA_RESUMEN_VIA_PREFIJO + 1).contains("Camí de Sa Calobra"))
        assertTrue(textosDe(ETIQUETA_RESUMEN_VIA_PREFIJO + 1).contains("Sin viajes anteriores"))
    }

    @Test
    fun `sin tramos ni vias no salen esas secciones`() {
        poner(lleno(FixtureResumen.resumen(vias = emptyList(), tramos = emptyList())))
        assertFalse(banco.hay(ETIQUETA_RESUMEN_TRAMOS))
        assertFalse(banco.hay(ETIQUETA_RESUMEN_VIAS))
    }

    @Test
    fun `sin datos de mapa instalados avisa en lugar del mapa`() {
        poner(lleno().copy(estiloMapaJson = null))
        assertTrue(banco.hay(ETIQUETA_RESUMEN_SIN_MAPA))
        assertFalse(banco.hay(ETIQUETA_MAPA_FALSO))
        assertTrue(llamadasAlMapa.isEmpty())
    }

    @Test
    fun `cargando solo enseña la espera`() {
        poner(ResumenViajeUiState())
        assertTrue(banco.hay(ETIQUETA_CARGANDO))
        assertFalse(banco.hay(ETIQUETA_RESUMEN_LISTA))
    }

    @Test
    fun `un viaje que ya no existe enseña el estado vacio`() {
        poner(ResumenViajeUiState(cargando = false, viaje = null))
        assertTrue(banco.hay(ETIQUETA_RESUMEN_VACIO))
        assertFalse(banco.hay(ETIQUETA_RESUMEN_LISTA))
    }

    @Test
    fun `si falla la lectura enseña el estado de error`() {
        poner(ResumenViajeUiState(cargando = false, error = true))
        assertTrue(banco.hay(ETIQUETA_RESUMEN_ERROR))
        assertFalse(banco.hay(ETIQUETA_RESUMEN_VACIO))
    }

    @Test
    fun `la cabecera lleva el destino y volver funciona`() {
        var volvio = false
        poner(lleno()) { volvio = true }
        assertTrue(banco.todosLosTextos().any { it.endsWith("· Sóller") })
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        assertTrue(volvio)
    }

    @Test
    fun `el tiempo de las tarjetas pasa a horas desde la hora`() {
        assertEquals(DuracionTarjeta("12:34", "min"), duracionDeTarjeta(754L))
        assertEquals(DuracionTarjeta("1:05", "h"), duracionDeTarjeta(3_900L))
        assertEquals(DuracionTarjeta("00:00", "min"), duracionDeTarjeta(-5L))
    }
}
