package app.trombito.rallycopilot.ui.tramos

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.domain.tramos.ResultadoCrono
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

/** El botón «Ver análisis» de la tarjeta de meta: solo si hay a dónde ir y la pasada está guardada; nunca solo. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TarjetaMetaAnalisisTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val abiertos = mutableListOf<Pair<String, Long>>()

    private fun guardada() =
        ResultadoCrono(148_100L, -2_300L, nuevoMejor = true, mejorAnteriorMs = 150_400L, tramoId = "t1", pasadaId = 7L)

    private fun ponerMeta(
        resultado: ResultadoCrono,
        conDestino: Boolean = true,
    ) {
        val destino: ((String, Long) -> Unit)? = if (conDestino) { t, p -> abiertos += t to p } else null
        banco.poner {
            RallyCopilotTheme {
                CompositionLocalProvider(LocalAbrirAnalisis provides destino) {
                    CronoSuperior(cronoTerminado(resultado), reloj = { AHORA_PRUEBA_MS })
                }
            }
        }
    }

    @Test
    fun `con la pasada guardada ofrece ver el analisis y no navega solo`() {
        ponerMeta(guardada())
        assertTrue(banco.hay(ETIQUETA_CRONO_META_ANALISIS))
        assertTrue("nunca se abre automáticamente", abiertos.isEmpty())
        banco.esperar(DURACION_TARJETA_META_MS - 1_000L)
        assertTrue(abiertos.isEmpty())
    }

    @Test
    fun `pulsar ver analisis abre la pasada recien guardada`() {
        ponerMeta(guardada())
        val boton = banco.conEtiqueta(ETIQUETA_CRONO_META_ANALISIS)
        assertTrue(banco.esPulsable(boton))
        assertEquals(listOf("VER ANÁLISIS"), banco.textos(boton))
        banco.pulsar(boton)
        assertEquals(listOf("t1" to 7L), abiertos)
    }

    @Test
    fun `el boton mide al menos 48 dp para pulsarlo en marcha`() {
        ponerMeta(guardada())
        val alto = banco.conEtiqueta(ETIQUETA_CRONO_META_ANALISIS).boundsInRoot.height
        // xxhdpi: 3 px por dp
        assertTrue("alto $alto px", alto >= 48 * 3f)
    }

    @Test
    fun `la tarjeta sigue siendo una region viva sin el boton dentro`() {
        ponerMeta(guardada())
        val tarjeta = banco.conEtiqueta(ETIQUETA_CRONO_META)
        assertEquals(
            "Meta. Tiempo: 2 minutos 28,1 segundos. Respecto a tu mejor tiempo: menos 2,3 segundos. " +
                "Nuevo mejor tiempo.",
            banco.descripcion(tarjeta),
        )
        assertEquals(null, tarjeta.config.getOrNull(SemanticsProperties.Role))
        assertFalse("el botón no cuelga de la región viva", banco.textos(tarjeta).contains("VER ANÁLISIS"))
    }

    @Test
    fun `sin pasada guardada no hay boton`() {
        ponerMeta(ResultadoCrono(148_100L, -2_300L, nuevoMejor = true, mejorAnteriorMs = 150_400L))
        assertTrue(banco.hay(ETIQUETA_CRONO_META))
        assertFalse(banco.hay(ETIQUETA_CRONO_META_ANALISIS))
    }

    @Test
    fun `sin destino de navegacion no hay boton`() {
        ponerMeta(guardada(), conDestino = false)
        assertTrue(banco.hay(ETIQUETA_CRONO_META))
        assertFalse(banco.hay(ETIQUETA_CRONO_META_ANALISIS))
    }
}
