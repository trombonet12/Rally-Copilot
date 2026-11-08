package app.trombito.rallycopilot.ui.tramos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ResultadoCrono
import app.trombito.rallycopilot.domain.tramos.SubtramoResultado
import app.trombito.rallycopilot.domain.tramos.SubtramoVivo
import app.trombito.rallycopilot.ui.comun.BancoCompose
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

/** Línea del subtramo en marcha y tarjeta del recién cerrado, que salen en el mismo sitio que el HUD del crono. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SubtramoHudTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private var crono by mutableStateOf<CronoEnCurso?>(null)
    private val ahora = AHORA_PRUEBA_MS

    private fun poner(inicial: CronoEnCurso?) {
        crono = inicial
        banco.poner { RallyCopilotTheme { CronoSuperior(crono, reloj = { ahora }) } }
    }

    private fun textos(etiqueta: String) = banco.textos(banco.conEtiqueta(etiqueta))

    private fun conSubtramo(
        deltaMs: Long?,
        ultimo: SubtramoResultado? = null,
    ) = cronoSinTramo().copy(subtramo = SubtramoVivo(2_400.0, deltaMs, 52_300L), ultimoSubtramo = ultimo)

    private fun cerrado(
        mejorMs: Long?,
        hace: Long = 1_000L,
    ) = SubtramoResultado(2_400.0, 112_300L, mejorMs, ahora - hace)

    @Test
    fun `la linea del subtramo enseña la distancia, el tiempo y el delta a favor`() {
        poner(conSubtramo(-3_100L))
        assertTrue(banco.hay(ETIQUETA_SUBTRAMO_HUD))
        assertTrue("Subtramo · 2,4 km" in banco.todosLosTextos())
        assertEquals(listOf("00:52,3"), textos(ETIQUETA_SUBTRAMO_TIEMPO))
        assertEquals(listOf("▼ −3,1"), textos(ETIQUETA_SUBTRAMO_DELTA))
        assertEquals(
            "Subtramo de 2,4 km. Tiempo: 52,3 segundos. Respecto a tu mejor tiempo: menos 3,1 segundos",
            banco.descripcion(banco.conEtiqueta(ETIQUETA_SUBTRAMO_HUD)),
        )
    }

    @Test
    fun `un delta en contra sale con flecha hacia arriba y sin delta solo hay tiempo`() {
        poner(conSubtramo(1_200L))
        assertEquals(listOf("▲ +1,2"), textos(ETIQUETA_SUBTRAMO_DELTA))
        crono = conSubtramo(null)
        banco.reposo()
        assertFalse(banco.hay(ETIQUETA_SUBTRAMO_DELTA))
        assertEquals(listOf("00:52,3"), textos(ETIQUETA_SUBTRAMO_TIEMPO))
        assertEquals(
            "Subtramo de 2,4 km. Tiempo: 52,3 segundos",
            banco.descripcion(banco.conEtiqueta(ETIQUETA_SUBTRAMO_HUD)),
        )
    }

    @Test
    fun `el tiempo del subtramo sigue contando con el reloj`() {
        var reloj = ahora
        crono = conSubtramo(null)
        banco.poner { RallyCopilotTheme { CronoSuperior(crono, reloj = { reloj }) } }
        reloj += 5_000L
        banco.esperar(PERIODO_HUD_MS + 100L)
        assertEquals(listOf("00:57,3"), textos(ETIQUETA_SUBTRAMO_TIEMPO))
    }

    @Test
    fun `sin subtramo ni cerrado no se pinta nada`() {
        poner(cronoSinTramo())
        assertFalse(banco.hay(ETIQUETA_SUBTRAMO_HUD))
        assertFalse(banco.hay(ETIQUETA_SUBTRAMO_TARJETA))
    }

    @Test
    fun `la tarjeta enseña tiempo y delta y desaparece a los 8 segundos de cerrarse`() {
        poner(cronoSinTramo().copy(ultimoSubtramo = cerrado(mejorMs = 115_400L)))
        assertTrue(banco.hay(ETIQUETA_SUBTRAMO_TARJETA))
        assertTrue("Subtramo 2,4 km" in banco.todosLosTextos())
        assertEquals(listOf("01:52,3"), textos(ETIQUETA_SUBTRAMO_TARJETA_TIEMPO))
        assertEquals(listOf("▼ −3,1"), textos(ETIQUETA_SUBTRAMO_TARJETA_DELTA))
        val tarjeta = banco.conEtiqueta(ETIQUETA_SUBTRAMO_TARJETA)
        assertEquals(LiveRegionMode.Polite, tarjeta.config.getOrNull(SemanticsProperties.LiveRegion))
        assertEquals(
            "Subtramo de 2,4 km cerrado. Tiempo: 1 minuto 52,3 segundos. " +
                "Respecto a tu mejor tiempo: menos 3,1 segundos.",
            banco.descripcion(tarjeta),
        )
        banco.esperar(DURACION_TARJETA_SUBTRAMO_MS - 1_000L - 500L)
        assertTrue(banco.hay(ETIQUETA_SUBTRAMO_TARJETA))
        banco.esperar(1_000L)
        assertFalse(banco.hay(ETIQUETA_SUBTRAMO_TARJETA))
    }

    @Test
    fun `sin mejor previo la tarjeta dice primera vez y no pinta delta`() {
        poner(cronoSinTramo().copy(ultimoSubtramo = cerrado(mejorMs = null)))
        assertEquals(listOf("Primera vez"), textos(ETIQUETA_SUBTRAMO_PRIMERA_VEZ))
        assertFalse(banco.hay(ETIQUETA_SUBTRAMO_TARJETA_DELTA))
        val descripcion = checkNotNull(banco.descripcion(banco.conEtiqueta(ETIQUETA_SUBTRAMO_TARJETA)))
        assertTrue(descripcion.endsWith("Primera vez en este trozo."))
    }

    @Test
    fun `un subtramo cerrado hace mas de 8 segundos no se enseña`() {
        poner(cronoSinTramo().copy(ultimoSubtramo = cerrado(mejorMs = 115_400L, hace = 9_000L)))
        assertFalse(banco.hay(ETIQUETA_SUBTRAMO_TARJETA))
    }

    @Test
    fun `un subtramo nuevo cerrado vuelve a sacar la tarjeta`() {
        poner(cronoSinTramo().copy(ultimoSubtramo = cerrado(mejorMs = 115_400L)))
        banco.esperar(DURACION_TARJETA_SUBTRAMO_MS)
        assertFalse(banco.hay(ETIQUETA_SUBTRAMO_TARJETA))
        crono = cronoSinTramo().copy(ultimoSubtramo = cerrado(mejorMs = 100_000L, hace = 0L))
        banco.reposo()
        assertTrue(banco.hay(ETIQUETA_SUBTRAMO_TARJETA))
        assertEquals(listOf("▲ +12,3"), textos(ETIQUETA_SUBTRAMO_TARJETA_DELTA))
    }

    @Test
    fun `la tarjeta de meta manda sobre las del subtramo`() {
        val meta = ResultadoCrono(148_100L, -2_300L, nuevoMejor = true, mejorAnteriorMs = 150_400L)
        poner(cronoTerminado(meta).copy(ultimoSubtramo = cerrado(mejorMs = 115_400L)))
        assertTrue(banco.hay(ETIQUETA_CRONO_META))
        assertFalse(banco.hay(ETIQUETA_SUBTRAMO_TARJETA))
    }

    @Test
    fun `el HUD de un tramo conocido y la tarjeta conviven`() {
        poner(cronoConocido().copy(ultimoSubtramo = cerrado(mejorMs = 115_400L)))
        assertTrue(banco.hay(ETIQUETA_CRONO_HUD))
        assertTrue(banco.hay(ETIQUETA_SUBTRAMO_TARJETA))
    }
}
