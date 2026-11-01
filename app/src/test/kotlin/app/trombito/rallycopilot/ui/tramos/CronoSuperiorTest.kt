package app.trombito.rallycopilot.ui.tramos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ResultadoCrono
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** HUD de tramo conocido, cronómetro pequeño y tarjeta de meta, en vertical. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CronoSuperiorTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private var crono by mutableStateOf<CronoEnCurso?>(null)
    private var compacto by mutableStateOf(false)
    private var ahora = AHORA_PRUEBA_MS

    private fun poner(inicial: CronoEnCurso?) {
        crono = inicial
        banco.poner {
            RallyCopilotTheme {
                CronoSuperior(crono, compacto = compacto, reloj = { ahora })
                CronoMiniEnPanel(crono, reloj = { ahora })
            }
        }
    }

    private fun textos(etiqueta: String) = banco.textos(banco.conEtiqueta(etiqueta))

    @Test
    fun `sin cronometro no se pinta nada`() {
        poner(null)
        assertFalse(banco.hay(ETIQUETA_CRONO_HUD))
        assertFalse(banco.hay(ETIQUETA_CRONO_MINI))
        assertFalse(banco.hay(ETIQUETA_CRONO_META))
    }

    @Test
    fun `tramo conocido con mejor pasada pinta el HUD y no el cronometro pequeno`() {
        poner(cronoConocido())
        assertTrue(banco.hay(ETIQUETA_CRONO_HUD))
        assertFalse(banco.hay(ETIQUETA_CRONO_MINI))
        assertEquals(listOf("02:30.4"), textos(ETIQUETA_CRONO_TIEMPO))
        assertEquals(listOf("+1,2"), textos(ETIQUETA_CRONO_DELTA))
    }

    @Test
    fun `tramo sin mejor pasada solo tiene el cronometro pequeno`() {
        poner(cronoSinReferencia())
        assertFalse(banco.hay(ETIQUETA_CRONO_HUD))
        assertTrue(banco.hay(ETIQUETA_CRONO_MINI))
    }

    @Test
    fun `sin tramo solo hay el cronometro pequeno sin decimas`() {
        poner(cronoSinTramo(95_000L))
        assertFalse(banco.hay(ETIQUETA_CRONO_HUD))
        assertEquals(listOf("01:35"), textos(ETIQUETA_CRONO_MINI))
        val descripcion = checkNotNull(banco.descripcion(banco.conEtiqueta(ETIQUETA_CRONO_MINI)))
        assertEquals("Cronómetro de la conducción: 1 minuto 35,0 segundos", descripcion)
    }

    @Test
    fun `el cronometro sigue contando entre fijaciones`() {
        poner(cronoConocido(transcurridoMs = 150_400L))
        ahora += 2_000L
        banco.esperar(2_000L)
        assertEquals(listOf("02:32.4"), textos(ETIQUETA_CRONO_TIEMPO))
    }

    @Test
    fun `una fijacion nueva recoloca el cronometro`() {
        poner(cronoConocido(transcurridoMs = 150_400L))
        crono = cronoConocido(transcurridoMs = 160_000L).copy(actualizadoEnMs = AHORA_PRUEBA_MS)
        banco.esperar(100L)
        assertEquals(listOf("02:40.0"), textos(ETIQUETA_CRONO_TIEMPO))
    }

    @Test
    fun `el delta a favor lleva menos y el nodo dice menos segundos`() {
        poner(cronoConocido(deltaMs = -2_300L))
        assertEquals(listOf("−2,3"), textos(ETIQUETA_CRONO_DELTA))
        val hud = checkNotNull(banco.descripcion(banco.nodos().first { hasDescripcionDeHud(it) }))
        assertEquals(
            "Cronómetro del tramo: 2 minutos 30,4 segundos. Respecto a tu mejor tiempo: menos 2,3 segundos",
            hud,
        )
    }

    @Test
    fun `sin delta todavia enseña una raya`() {
        poner(cronoConocido(deltaMs = null))
        assertEquals(listOf("—"), textos(ETIQUETA_CRONO_DELTA))
    }

    @Test
    fun `la descripcion del HUD se lee con minutos y segundos para TalkBack`() {
        poner(cronoConocido())
        val descripcion =
            banco.nodos().firstNotNullOf { n ->
                banco.descripcion(n)?.takeIf { it.startsWith("Cronómetro del tramo") }
            }
        assertEquals(
            "Cronómetro del tramo: 2 minutos 30,4 segundos. Respecto a tu mejor tiempo: más 1,2 segundos",
            descripcion,
        )
    }

    @Test
    fun `la barra de progreso informa de lo recorrido`() {
        poner(cronoConocido(progresoM = 1_995.0))
        val barra = banco.conEtiqueta(ETIQUETA_CRONO_BARRA)
        assertEquals("Progreso del tramo: 50 por ciento", banco.descripcion(barra))
        val rango = checkNotNull(barra.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo))
        assertEquals(0.5f, rango.current, 0.001f)
    }

    @Test
    fun `solo se ven los parciales de sector ya pasados, con su diferencia`() {
        poner(cronoConocido(parciales = listOf(sector(1, -300L), micro(1, 50L), sector(2, 1_200L))))
        assertEquals(listOf("S1", "−0,3"), banco.textos(banco.conEtiqueta(ETIQUETA_CRONO_PARCIAL_PREFIJO + "S1")))
        assertEquals(listOf("S2", "+1,2"), banco.textos(banco.conEtiqueta(ETIQUETA_CRONO_PARCIAL_PREFIJO + "S2")))
        assertFalse(banco.hay(ETIQUETA_CRONO_PARCIAL_PREFIJO + "S3"))
        assertFalse("los microsectores no tienen chip fijo", banco.hay(ETIQUETA_CRONO_PARCIAL_PREFIJO + "M1"))
        assertEquals(
            "Sector 1: menos 0,3 segundos",
            banco.descripcion(banco.conEtiqueta(ETIQUETA_CRONO_PARCIAL_PREFIJO + "S1")),
        )
    }

    @Test
    fun `sin diferencia el parcial enseña lo que costo el sector`() {
        poner(cronoConocido(parciales = listOf(sector(1, null, duracionMs = 41_000L))))
        assertEquals(listOf("S1", "00:41"), banco.textos(banco.conEtiqueta(ETIQUETA_CRONO_PARCIAL_PREFIJO + "S1")))
    }

    @Test
    fun `el sector recien cortado destaca tres segundos y luego vuelve a su sitio`() {
        poner(cronoConocido(parciales = listOf(sector(1, -300L))))
        assertFalse("el que ya estaba al montar no destaca", banco.hay(ETIQUETA_CRONO_DESTACADO))
        crono = cronoConocido(parciales = listOf(sector(1, -300L), sector(2, 1_200L)))
        banco.esperar(100L)
        val destacado = banco.conEtiqueta(ETIQUETA_CRONO_DESTACADO)
        assertTrue("S2" in banco.textos(destacado))
        val chip = banco.conEtiqueta(ETIQUETA_CRONO_PARCIAL_PREFIJO + "S2")
        assertEquals(LiveRegionMode.Polite, chip.config.getOrNull(SemanticsProperties.LiveRegion))
        banco.esperar(DURACION_PARCIAL_DESTACADO_MS - 500L)
        assertTrue(banco.hay(ETIQUETA_CRONO_DESTACADO))
        banco.esperar(1_000L)
        assertFalse(banco.hay(ETIQUETA_CRONO_DESTACADO))
        assertTrue("el parcial sigue en la fila", banco.hay(ETIQUETA_CRONO_PARCIAL_PREFIJO + "S2"))
    }

    @Test
    fun `la meta enseña el resultado con nuevo mejor y desaparece a los pocos segundos`() {
        poner(cronoTerminado(ResultadoCrono(148_100L, -2_300L, nuevoMejor = true, mejorAnteriorMs = 150_400L)))
        assertTrue(banco.hay(ETIQUETA_CRONO_META))
        assertFalse("la tarjeta sustituye al HUD", banco.hay(ETIQUETA_CRONO_HUD))
        assertFalse(banco.hay(ETIQUETA_CRONO_MINI))
        assertEquals(listOf("02:28.1"), textos(ETIQUETA_CRONO_META_TOTAL))
        assertEquals(listOf("−2,3"), textos(ETIQUETA_CRONO_META_DELTA))
        assertEquals(listOf("NUEVO MEJOR"), textos(ETIQUETA_CRONO_NUEVO_MEJOR))
        assertEquals(listOf("Mejor anterior 02:30.4"), textos(ETIQUETA_CRONO_META_MEJOR_ANTERIOR))
        val meta = banco.conEtiqueta(ETIQUETA_CRONO_META)
        assertEquals(LiveRegionMode.Polite, meta.config.getOrNull(SemanticsProperties.LiveRegion))
        assertEquals(
            "Meta. Tiempo: 2 minutos 28,1 segundos. Respecto a tu mejor tiempo: menos 2,3 segundos. " +
                "Nuevo mejor tiempo.",
            banco.descripcion(meta),
        )
        banco.esperar(DURACION_TARJETA_META_MS + 1_000L)
        assertFalse(banco.hay(ETIQUETA_CRONO_META))
        assertFalse(banco.hay(ETIQUETA_CRONO_HUD))
    }

    @Test
    fun `una pasada peor no dice nuevo mejor y el delta va en rojo`() {
        poner(cronoTerminado(ResultadoCrono(152_000L, 1_600L, nuevoMejor = false, mejorAnteriorMs = 150_400L)))
        assertFalse(banco.hay(ETIQUETA_CRONO_NUEVO_MEJOR))
        assertEquals(listOf("+1,6"), textos(ETIQUETA_CRONO_META_DELTA))
        assertEquals(
            "Meta. Tiempo: 2 minutos 32,0 segundos. Respecto a tu mejor tiempo: más 1,6 segundos.",
            banco.descripcion(banco.conEtiqueta(ETIQUETA_CRONO_META)),
        )
    }

    @Test
    fun `la primera pasada no tiene delta ni mejor anterior`() {
        poner(cronoTerminado(ResultadoCrono(150_000L, null, nuevoMejor = true, mejorAnteriorMs = null)))
        assertTrue(banco.hay(ETIQUETA_CRONO_META))
        assertFalse(banco.hay(ETIQUETA_CRONO_META_DELTA))
        assertFalse(banco.hay(ETIQUETA_CRONO_META_MEJOR_ANTERIOR))
        assertNotNull(banco.conEtiqueta(ETIQUETA_CRONO_NUEVO_MEJOR))
    }

    @Test
    fun `el cronometro de la meta no sigue contando`() {
        poner(cronoTerminado(ResultadoCrono(148_100L, -2_300L, nuevoMejor = true, mejorAnteriorMs = 150_400L)))
        ahora += 3_000L
        banco.esperar(3_000L)
        assertEquals(listOf("02:28.1"), textos(ETIQUETA_CRONO_META_TOTAL))
    }

    @Test
    fun `en apaisado compacto el HUD es mas bajo que en vertical`() {
        val parciales = listOf(sector(1, -300L), sector(2, 1_200L))
        poner(cronoConocido(parciales = parciales))
        val normal = banco.conEtiqueta(ETIQUETA_CRONO_HUD).boundsInRoot.height
        compacto = true
        banco.esperar(100L)
        val bajo = banco.conEtiqueta(ETIQUETA_CRONO_HUD).boundsInRoot.height
        assertTrue("compacto $bajo < normal $normal", bajo < normal)
    }

    private fun hasDescripcionDeHud(nodo: SemanticsNode) =
        banco.descripcion(nodo)?.startsWith("Cronómetro del tramo") == true
}
