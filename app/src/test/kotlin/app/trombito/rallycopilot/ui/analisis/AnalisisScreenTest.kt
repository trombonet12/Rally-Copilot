package app.trombito.rallycopilot.ui.analisis

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.motor.analisis.AnalisisPasada
import app.trombito.rallycopilot.motor.analisis.PosicionRepeticion
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ETIQUETA_VOLVER
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

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AnalisisScreenTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private val lenta = FixtureAnalisis.detallada(1, kmh = 50.0, inicioMs = 1_762_000_000_000L)
    private val rapida = FixtureAnalisis.detallada(2, kmh = 70.0, inicioMs = 1_761_000_000_000L, esMejor = true)
    private val media = FixtureAnalisis.detallada(3, kmh = 60.0, inicioMs = 1_760_000_000_000L)

    private val pedidas = mutableListOf<String>()
    private val comparaciones = mutableListOf<Long?>()
    private val cursores = mutableListOf<Double>()
    private val posiciones = mutableListOf<Long>()
    private val velocidades = mutableListOf<VelocidadRepeticion>()
    private var alternar = 0

    /** El estado que armaría el ViewModel para la pasada lenta comparada con [contra]. */
    private fun estado(
        contra: app.trombito.rallycopilot.domain.tramos.PasadaDetallada? = rapida,
        repeticion: EstadoRepeticion = EstadoRepeticion(),
        posicion: PosicionRepeticion? = null,
        cursorM: Double? = null,
        estilo: String? = "{}",
    ): AnalisisUiState {
        val opciones =
            listOf(rapida, media).map { OpcionComparada(it.id, it.inicioMs, it.totalMs, it.esMejor) }
        return AnalisisUiState(
            cargando = false,
            tramoNombre = "Ma-2141 · Escorca",
            inicioMs = lenta.inicioMs,
            analisis = FixtureAnalisis.analizar(lenta, contra),
            opciones = opciones,
            comparadaId = contra?.id,
            velocidadComparada = contra?.let { FixtureAnalisis.analizar(it).velocidad }.orEmpty(),
            estiloMapaJson = estilo,
            repeticion = repeticion,
            posicion = posicion,
            cursorM = cursorM,
        )
    }

    private val acciones =
        AccionesAnalisis(
            onVolver = { pedidas += "volver" },
            onComparar = { comparaciones += it },
            onAlternarRepeticion = { alternar++ },
            onVelocidad = { velocidades += it },
            onPosicion = { posiciones += it },
            onCursor = { cursores += it },
        )

    private val mapasPedidos = mutableListOf<Triple<String, AnalisisPasada, PuntoGeo?>>()

    private val mapaFalso: MapaDelAnalisis = { estilo, analisis, marcador, _: Modifier ->
        mapasPedidos += Triple(estilo, analisis, marcador)
    }

    private fun poner(estado: AnalisisUiState) {
        banco.poner { RallyCopilotTheme { AnalisisScreen(estado, acciones, mapaFalso) } }
    }

    private fun textos(etiqueta: String) = banco.textos(banco.conEtiqueta(etiqueta))

    @Test
    fun `la cabecera trae el tramo, la fecha, el tiempo total y el delta contra la comparada`() {
        val e = estado()
        poner(e)
        assertEquals(listOf("MA-2141 · ESCORCA"), textos(ETIQUETA_ANALISIS_TITULO))
        assertTrue(Regex("""\d+ \w{3} \d{4} · \d{2}:\d{2}""").matches(textos(ETIQUETA_ANALISIS_FECHA).single()))
        assertEquals(listOf(formatearTiempoPrueba(lenta.totalMs)), textos(ETIQUETA_ANALISIS_TOTAL))
        val delta = textos(ETIQUETA_ANALISIS_DELTA).single()
        assertTrue("va más lento que la mejor: $delta", delta.startsWith("+"))
        assertEquals(
            listOf("contra ${formatearTiempoPrueba(rapida.totalMs)}"),
            textos(ETIQUETA_ANALISIS_COMPARADA),
        )
    }

    @Test
    fun `sin comparada no hay delta y lo dice`() {
        poner(estado(contra = null))
        assertFalse(banco.hay(ETIQUETA_ANALISIS_DELTA))
        assertEquals(listOf("Sin comparar"), textos(ETIQUETA_ANALISIS_COMPARADA))
    }

    @Test
    fun `volver llama al callback`() {
        poner(estado())
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        assertEquals(listOf("volver"), pedidas)
    }

    @Test
    fun `el selector ofrece ninguna y las demas pasadas con la elegida marcada`() {
        poner(estado())
        val ninguna = banco.conEtiqueta(ETIQUETA_COMPARAR_NINGUNA)
        val mejor = banco.conEtiqueta("${ETIQUETA_COMPARAR_PREFIJO}2")
        val otra = banco.conEtiqueta("${ETIQUETA_COMPARAR_PREFIJO}3")
        assertEquals(false, ninguna.config.getOrNull(SemanticsProperties.Selected))
        assertEquals(true, mejor.config.getOrNull(SemanticsProperties.Selected))
        assertEquals(false, otra.config.getOrNull(SemanticsProperties.Selected))
        assertTrue(banco.textos(mejor).first().startsWith(formatearTiempoPrueba(rapida.totalMs)))
        assertTrue("la mejor lleva estrella", banco.nodos().any { banco.descripcion(it) == "Mejor" })
        // No se ofrece la propia pasada analizada
        assertFalse(banco.hay("${ETIQUETA_COMPARAR_PREFIJO}1"))
    }

    @Test
    fun `elegir una pasada o ninguna avisa con su numero`() {
        poner(estado())
        banco.pulsar(banco.conEtiqueta("${ETIQUETA_COMPARAR_PREFIJO}3"))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_COMPARAR_NINGUNA))
        banco.pulsar(banco.conEtiqueta("${ETIQUETA_COMPARAR_PREFIJO}2"))
        assertEquals(listOf<Long?>(3L, null, 2L), comparaciones)
    }

    @Test
    fun `sin otras pasadas no hay selector y se explica`() {
        poner(estado(contra = null).copy(opciones = emptyList()))
        assertFalse(banco.hay(ETIQUETA_COMPARAR_NINGUNA))
        assertTrue(textos(ETIQUETA_ANALISIS_COMPARADA).single().startsWith("Es la única pasada"))
    }

    @Test
    fun `la tabla tiene los cuatro sectores, el total y los microsectores`() {
        poner(estado())
        (1..4).forEach { assertTrue("sector $it", banco.hay("$ETIQUETA_FILA_SECTOR$it")) }
        assertFalse(banco.hay("${ETIQUETA_FILA_SECTOR}5"))
        assertTrue(banco.hay(ETIQUETA_FILA_TOTAL))
        // Un microsector por kilómetro: 4,4 km dan 5
        assertEquals(5, banco.etiquetas(ETIQUETA_FILA_MICRO).size)
    }

    @Test
    fun `cada fila lleva tiempo, delta con su signo, velocidad media y maxima`() {
        val e = estado()
        poner(e)
        val primero = e.analisis!!.sectores.first()
        val textos = textos("${ETIQUETA_FILA_SECTOR}1")
        assertTrue("S1" in textos)
        assertTrue(formatearTiempoPrueba(primero.tiempoMs) in textos)
        // Más lento que la mejor: cada sector pierde tiempo
        assertTrue(textos.any { it.startsWith("+") })
        assertTrue(textos.contains(formatearKmhCelda(primero.velMediaKmh)))
        assertTrue(textos.contains(formatearKmhCelda(primero.velMaxKmh)))
        // La media de 50 km/h constantes sale cerca de 50
        assertEquals(50.0, primero.velMediaKmh, 1.0)
        val descripcion = banco.descripcion(banco.conEtiqueta("${ETIQUETA_FILA_SECTOR}1"))!!
        assertTrue(descripcion, descripcion.startsWith("S1: tiempo "))
        assertTrue(descripcion.contains("velocidad media"))
    }

    @Test
    fun `contra una pasada mas lenta los deltas salen negativos con el signo menos`() {
        val e = estado().copy(analisis = FixtureAnalisis.analizar(rapida, lenta))
        poner(e)
        val delta = textos("${ETIQUETA_FILA_SECTOR}2")
        assertTrue(delta.any { it.startsWith("−") })
        assertTrue(textos(ETIQUETA_FILA_TOTAL).any { it.startsWith("−") })
    }

    @Test
    fun `sin comparada la columna de delta queda en rayas`() {
        poner(estado(contra = null))
        assertTrue("—" in textos("${ETIQUETA_FILA_SECTOR}1"))
        assertTrue(banco.descripcion(banco.conEtiqueta("${ETIQUETA_FILA_SECTOR}1"))!!.contains("sin comparada"))
        assertTrue("—" in textos(ETIQUETA_FILA_TOTAL))
    }

    @Test
    fun `la fila del total suma el tiempo de la pasada`() {
        poner(estado())
        assertTrue(formatearTiempoPrueba(lenta.totalMs) in textos(ETIQUETA_FILA_TOTAL))
        // 50 km/h de media
        assertTrue("50" in textos(ETIQUETA_FILA_TOTAL))
    }

    @Test
    fun `los dos graficos estan y el de delta falta sin comparada`() {
        poner(estado())
        assertTrue(banco.hay(ETIQUETA_GRAFICO_VELOCIDAD))
        assertTrue(banco.hay(ETIQUETA_GRAFICO_DELTA))
        assertTrue(banco.descripcion(banco.conEtiqueta(ETIQUETA_GRAFICO_VELOCIDAD))!!.contains("velocidad"))
    }

    @Test
    fun `sin comparada solo queda el grafico de velocidad`() {
        poner(estado(contra = null))
        assertTrue(banco.hay(ETIQUETA_GRAFICO_VELOCIDAD))
        assertFalse(banco.hay(ETIQUETA_GRAFICO_DELTA))
    }

    @Test
    fun `sin cursor la lectura ensena la ayuda y con cursor los valores de ese punto`() {
        poner(estado())
        assertEquals(
            listOf("Toca o arrastra sobre un gráfico para ver el valor en ese punto."),
            textos(ETIQUETA_LECTURA_CURSOR),
        )
    }

    @Test
    fun `con el cursor puesto la lectura da kilometro, velocidades y delta`() {
        poner(estado(cursorM = 2_000.0, repeticion = EstadoRepeticion(tMs = 144_000L)))
        val lectura = textos(ETIQUETA_LECTURA_CURSOR)
        assertEquals("2,0 km", lectura.first())
        assertTrue("50 km/h" in lectura)
        assertTrue("70 km/h" in lectura)
        assertTrue(lectura.last().endsWith(" s"))
        assertTrue(lectura.last().startsWith("+"))
        val estadoGrafico =
            banco
                .conEtiqueta(
                    ETIQUETA_GRAFICO_VELOCIDAD,
                ).config
                .getOrNull(SemanticsProperties.StateDescription)
        assertNotNull(estadoGrafico)
        assertTrue(estadoGrafico!!, estadoGrafico.contains("2,0 km"))
    }

    @Test
    fun `tocar el grafico avisa con una distancia dentro del tramo`() {
        poner(estado())
        val grafico = banco.conEtiqueta(ETIQUETA_GRAFICO_VELOCIDAD)
        val centro = grafico.boundsInRoot.center
        banco.tocarEn(centro.x, centro.y)
        assertEquals(1, cursores.size)
        val total = FixtureAnalisis.tramo.metaM
        // El centro del lienzo cae algo antes de la mitad del área de dibujo (el eje izquierdo la desplaza)
        assertTrue("distancia ${cursores.first()} de $total", cursores.first() in total * 0.3..total * 0.6)
    }

    @Test
    fun `tocar el grafico de delta tambien mueve el cursor`() {
        poner(estado())
        val centro = banco.conEtiqueta(ETIQUETA_GRAFICO_DELTA).boundsInRoot.center
        banco.tocarEn(centro.x, centro.y)
        assertEquals(1, cursores.size)
    }

    @Test
    fun `el mando de la repeticion avisa de play y de cada velocidad`() {
        poner(estado())
        banco.pulsar(banco.conEtiqueta(ETIQUETA_REPETICION_PLAY))
        assertEquals(1, alternar)
        VelocidadRepeticion.entries.forEach {
            banco.pulsar(
                banco.conEtiqueta(ETIQUETA_REPETICION_VELOCIDAD + it.factor),
            )
        }
        assertEquals(VelocidadRepeticion.entries.toList(), velocidades)
    }

    @Test
    fun `la velocidad elegida va marcada y el boton cambia a pausa`() {
        poner(
            estado(
                repeticion = EstadoRepeticion(tMs = 5_000L, reproduciendo = true, velocidad = VelocidadRepeticion.X4),
            ),
        )
        assertEquals(
            true,
            banco.conEtiqueta(ETIQUETA_REPETICION_VELOCIDAD + 4).config.getOrNull(SemanticsProperties.Selected),
        )
        assertEquals(
            false,
            banco.conEtiqueta(ETIQUETA_REPETICION_VELOCIDAD + 1).config.getOrNull(SemanticsProperties.Selected),
        )
        assertEquals(
            "Pausar la repetición",
            banco.descripcion(
                banco.nodos().first {
                    banco.descripcion(it) ==
                        "Pausar la repetición"
                },
            ),
        )
    }

    @Test
    fun `parada la repeticion el boton dice reproducir y el tiempo empieza en cero`() {
        val e = estado()
        poner(e)
        assertTrue(banco.nodos().any { banco.descripcion(it) == "Reproducir la repetición" })
        assertEquals(
            listOf("00:00.0 / ${formatearTiempoPrueba(lenta.totalMs)}"),
            textos(ETIQUETA_REPETICION_TIEMPO),
        )
    }

    @Test
    fun `el tiempo de la repeticion sigue a su posicion`() {
        poner(estado(repeticion = EstadoRepeticion(tMs = 75_300L)))
        assertTrue(textos(ETIQUETA_REPETICION_TIEMPO).single().startsWith("01:15.3 / "))
    }

    @Test
    fun `el mapa recibe el estilo, el analisis y el marcador de la repeticion`() {
        val marcador = PuntoGeo(39.81, 2.80)
        val posicion = PosicionRepeticion(30_000L, marcador, 1_000.0, 14.0)
        val e = estado(posicion = posicion, repeticion = EstadoRepeticion(tMs = 30_000L))
        poner(e)
        val (estilo, analisis, punto) = mapasPedidos.last()
        assertEquals("{}", estilo)
        assertEquals(e.analisis, analisis)
        assertEquals(marcador, punto)
        assertTrue(banco.hay(ETIQUETA_LEYENDA_VELOCIDAD))
    }

    @Test
    fun `sin la repeticion en marcha no hay marcador`() {
        poner(estado())
        assertNull(mapasPedidos.last().third)
    }

    @Test
    fun `la leyenda describe el rango de velocidades`() {
        poner(estado())
        val descripcion = banco.descripcion(banco.conEtiqueta(ETIQUETA_LEYENDA_VELOCIDAD))!!
        assertTrue(descripcion, descripcion.startsWith("Leyenda del color: de "))
        assertTrue(descripcion.contains("km/h"))
    }

    @Test
    fun `sin estilo de mapa avisa y no pinta el mapa pero mantiene el mando`() {
        poner(estado(estilo = null))
        assertTrue(banco.hay(ETIQUETA_ANALISIS_SIN_MAPA))
        assertTrue(mapasPedidos.isEmpty())
        assertTrue(banco.hay(ETIQUETA_REPETICION_PLAY))
    }

    @Test
    fun `mientras carga solo hay el indicador`() {
        poner(AnalisisUiState())
        assertTrue(banco.hay(ETIQUETA_ANALISIS_CARGANDO))
        assertFalse(banco.hay(ETIQUETA_TABLA))
    }

    @Test
    fun `una pasada que no existe lo dice`() {
        poner(AnalisisUiState(cargando = false, problema = ProblemaAnalisis.NO_ENCONTRADA))
        assertTrue(banco.hay(ETIQUETA_ANALISIS_NO_ENCONTRADA))
        assertTrue(banco.todosLosTextos().contains("PASADA NO ENCONTRADA"))
    }

    @Test
    fun `una pasada ilegible lo dice`() {
        poner(AnalisisUiState(cargando = false, problema = ProblemaAnalisis.ILEGIBLE))
        assertTrue(banco.hay(ETIQUETA_ANALISIS_ILEGIBLE))
    }

    @Test
    fun `los controles tactiles miden al menos 48 dp`() {
        poner(estado())
        // xxhdpi: 3 px por dp
        val minimo = 48 * 3f
        val play = banco.conEtiqueta(ETIQUETA_REPETICION_PLAY).boundsInRoot
        assertTrue(play.width >= minimo && play.height >= minimo)
        val volver = banco.conEtiqueta(ETIQUETA_VOLVER).boundsInRoot
        assertTrue(volver.width >= minimo && volver.height >= minimo)
        listOf(ETIQUETA_COMPARAR_NINGUNA, "${ETIQUETA_COMPARAR_PREFIJO}2").forEach {
            val alto = banco.conEtiqueta(it).touchBoundsInRoot.height
            assertTrue("$it mide $alto px", alto >= minimo)
        }
    }

    private fun formatearTiempoPrueba(ms: Long): String {
        val d = ms / 100
        return String.format(java.util.Locale.ROOT, "%02d:%02d.%d", d / 600, d / 10 % 60, d % 10)
    }
}
