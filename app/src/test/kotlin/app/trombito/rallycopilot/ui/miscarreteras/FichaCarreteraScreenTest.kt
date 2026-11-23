package app.trombito.rallycopilot.ui.miscarreteras

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.testTag
import app.trombito.rallycopilot.domain.tramos.DatosVia
import app.trombito.rallycopilot.motor.analisis.CurvasPorGrado
import app.trombito.rallycopilot.motor.analisis.FichaCarretera
import app.trombito.rallycopilot.motor.analisis.Marcha
import app.trombito.rallycopilot.motor.analisis.SentidoCarretera
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.SentidoVia
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ETIQUETA_CARGANDO
import app.trombito.rallycopilot.ui.comun.ETIQUETA_VOLVER
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.comun.resumenFalso
import app.trombito.rallycopilot.ui.mapa.ModoCalor
import app.trombito.rallycopilot.ui.mistramos.agruparCarreteras
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ETIQUETA_MAPA_FALSO = "ficha-mapa-falso"

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class FichaCarreteraScreenTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private val ficha =
        FichaCarretera(
            id = "MA10",
            ref = "Ma-10",
            nombre = "Carretera de Valldemossa",
            viajes = 6,
            metros = 86_000.0,
            curvas = CurvasPorGrado(listOf(100, 200, 150, 90, 40, 20), escuadras = 3, horquillas = 5),
            ultimaVezMs = 1_700_000_000_000L,
            creciente = SentidoCarretera(4, Marcha(40_000.0, 2_400_000L)),
            decreciente = SentidoCarretera(3, Marcha(40_000.0, 2_700_000L)),
        )

    @Suppress("LongParameterList") // cada prueba fija solo lo que le importa; el resto va por defecto
    private fun tramo(
        id: String,
        desde: Double?,
        hasta: Double?,
        sentido: SentidoVia,
        destino: String?,
        pasadas: Int = 3,
        mejorMs: Long? = 192_400L,
        mediaMs: Long? = 210_100L,
        nombrePropio: String? = null,
    ) = resumenFalso(
        id,
        pasadas = pasadas,
        mejorMs = mejorMs,
        longitudM = 5_000.0,
        mediaMs = mediaMs,
        nombrePropio = nombrePropio,
        via = DatosVia("MA10", desde, hasta, sentido, destino, null),
    )

    private val tramos =
        listOf(
            tramo("a", 62.0, 71.0, SentidoVia.CRECIENTE, "Sóller"),
            tramo("b", 71.0, 76.0, SentidoVia.CRECIENTE, "Sóller", pasadas = 1, mejorMs = 99_000L, mediaMs = 99_000L),
            tramo("c", 62.0, 108.0, SentidoVia.DECRECIENTE, "Andratx", mejorMs = null, mediaMs = null),
        )

    private val lleno =
        FichaCarreteraUiState(
            cargando = false,
            ficha = ficha,
            grupos = agruparCarreteras(tramos),
            regla = reglaDe(tramos),
            trazos = listOf(TrazoCarretera(listOf(PuntoGeo(39.6, 2.9), PuntoGeo(39.61, 2.92)), pasadas = 6)),
        )

    private val mapaFalso: MapaDelCalor = { _, _, _, modificador ->
        Box(modificador.fillMaxSize().testTag(ETIQUETA_MAPA_FALSO))
    }

    private fun poner(
        estado: FichaCarreteraUiState,
        acciones: AccionesFichaCarretera = AccionesFichaCarretera(),
    ) = banco.poner { RallyCopilotTheme { FichaCarreteraScreen(estado, acciones, mapa = mapaFalso) } }

    @Test
    fun `la cabecera dice kilometros, viajes, ritmo y curvas por km de la carretera`() {
        poner(lleno)
        val textos = banco.textos(banco.conEtiqueta(ETIQUETA_FICHA_CABECERA))
        assertTrue("Carretera de Valldemossa" in textos)
        assertTrue("86,0 km en 6 viajes · 56 km/h · 7,1 curvas/km" in textos)
    }

    @Test
    fun `dos columnas por sentido con el destino, el ritmo y las veces`() {
        poner(lleno)
        val creciente = banco.textos(banco.conEtiqueta("${ETIQUETA_FICHA_SENTIDO_PREFIJO}CRECIENTE"))
        assertTrue("Hacia Sóller" in creciente)
        assertTrue("60 km/h" in creciente)
        assertTrue("4 viajes" in creciente)
        val decreciente = banco.textos(banco.conEtiqueta("${ETIQUETA_FICHA_SENTIDO_PREFIJO}DECRECIENTE"))
        assertTrue("Hacia Andratx" in decreciente)
        assertTrue("53 km/h" in decreciente)
        assertEquals(
            "Hacia Sóller: 60 km/h, 4 viajes",
            banco.descripcion(banco.conEtiqueta("${ETIQUETA_FICHA_SENTIDO_PREFIJO}CRECIENTE")),
        )
    }

    @Test
    fun `sin destino el sentido se nombra por los kilometros y sin tiempos lo dice`() {
        val sinTiempos =
            ficha.copy(creciente = SentidoCarretera(), decreciente = SentidoCarretera(2, Marcha(1_000.0, 60_000L)))
        poner(lleno.copy(ficha = sinTiempos, grupos = emptyList()))
        val creciente = banco.textos(banco.conEtiqueta("${ETIQUETA_FICHA_SENTIDO_PREFIJO}CRECIENTE"))
        assertTrue("Hacia el km más alto" in creciente)
        assertTrue("Sin tiempos en este sentido" in creciente)
        val decreciente = banco.textos(banco.conEtiqueta("${ETIQUETA_FICHA_SENTIDO_PREFIJO}DECRECIENTE"))
        assertTrue("Hacia el km más bajo" in decreciente)
    }

    @Test
    fun `si ningun viaje se pudo orientar ensena una sola linea con el ritmo`() {
        val sinSentido =
            ficha.copy(
                creciente = SentidoCarretera(),
                decreciente = SentidoCarretera(),
                sinSentido = SentidoCarretera(2, Marcha(1_000.0, 60_000L)),
            )
        poner(lleno.copy(ficha = sinSentido, grupos = emptyList()))
        assertFalse(banco.hay("${ETIQUETA_FICHA_SENTIDO_PREFIJO}CRECIENTE"))
        val unica = banco.textos(banco.conEtiqueta("${ETIQUETA_FICHA_SENTIDO_PREFIJO}DESCONOCIDO"))
        assertTrue("Sentido sin determinar" in unica)
        assertTrue("60 km/h" in unica)
    }

    @Test
    fun `las curvas por grado de la carretera dicen que 1 es la mas cerrada`() {
        poner(lleno)
        assertEquals(
            "Grado 2: 200 curvas",
            banco.descripcion(banco.conEtiqueta("${ETIQUETA_FICHA_CURVAS_FILA_PREFIJO}g2")),
        )
        assertEquals(8, banco.etiquetas(ETIQUETA_FICHA_CURVAS_FILA_PREFIJO).size)
        assertTrue("1 = la más cerrada · 6 = la más suave" in banco.todosLosTextos())
    }

    @Test
    fun `la regla tiene una franja por tramo con kilometros y no mete los que no los tienen`() {
        val sinKm = tramos + tramo("sin-km", null, null, SentidoVia.DESCONOCIDO, null)
        poner(lleno.copy(grupos = agruparCarreteras(sinKm), regla = reglaDe(sinKm)))
        assertTrue(banco.hay(ETIQUETA_FICHA_REGLA))
        assertEquals(3, banco.etiquetas(ETIQUETA_FICHA_FRANJA_PREFIJO).size)
        assertEquals(
            "km 62,0 → 71,0, Hacia Sóller. Toca para ver el tramo.",
            banco.descripcion(banco.conEtiqueta("${ETIQUETA_FICHA_FRANJA_PREFIJO}a")),
        )
        // Las marcas de la regla: de 62 a 108 km en cuatro tramos iguales.
        assertTrue(banco.todosLosTextos().containsAll(listOf("62", "74", "85", "97", "108")))
    }

    @Test
    fun `sin kilometros en ningun tramo la regla lo explica`() {
        val sinKm = listOf(tramo("x", null, null, SentidoVia.DESCONOCIDO, null))
        poner(lleno.copy(grupos = agruparCarreteras(sinKm), regla = null))
        assertTrue(banco.hay(ETIQUETA_FICHA_REGLA_VACIA))
        assertFalse(banco.hay(ETIQUETA_FICHA_REGLA))
    }

    @Test
    fun `tocar una franja avisa de la que se toca y sin tramo elegido no hay detalle`() {
        val franjas = mutableListOf<String>()
        val acciones = AccionesFichaCarretera(onFranja = { franjas += it })
        poner(lleno, acciones)
        assertFalse(banco.hay(ETIQUETA_FICHA_DETALLE))
        banco.pulsar(banco.conEtiqueta("${ETIQUETA_FICHA_FRANJA_PREFIJO}b"))
        assertEquals(listOf("b"), franjas)
    }

    @Test
    fun `el detalle del tramo elegido da rango, sentido, medidas y mejor tiempo`() {
        val abiertos = mutableListOf<String>()
        poner(lleno.copy(seleccionado = "a"), AccionesFichaCarretera(onAbrirTramo = { abiertos += it }))
        val detalle = banco.textos(banco.conEtiqueta(ETIQUETA_FICHA_DETALLE))
        assertTrue("km 62,0 → 71,0" in detalle)
        assertTrue("Hacia Sóller" in detalle)
        assertTrue("5,0 km · 3 pasadas · Media 03:30,1" in detalle)
        assertTrue("Mejor 03:12,4" in detalle)
        assertEquals(
            "km 62,0 → 71,0, Hacia Sóller. Toca para ver el tramo.",
            banco.descripcion(banco.conEtiqueta("${ETIQUETA_FICHA_FRANJA_PREFIJO}a")),
        )
        banco.pulsar(banco.conEtiqueta(ETIQUETA_FICHA_ABRIR_TRAMO))
        assertEquals(listOf("a"), abiertos)
    }

    @Test
    fun `la lista de tramos da kilometros, mejor tiempo y media y abre el tramo`() {
        val abiertos = mutableListOf<String>()
        poner(lleno, AccionesFichaCarretera(onAbrirTramo = { abiertos += it }))
        val a = banco.conEtiqueta("${ETIQUETA_FICHA_TRAMO_PREFIJO}a")
        val textos = banco.textos(a)
        assertTrue("km 62,0 → 71,0" in textos)
        assertTrue("5,0 km · 3 pasadas · Media 03:30,1" in textos)
        assertTrue("Mejor 03:12,4" in textos)
        // Una sola pasada no tiene media que enseñar.
        assertTrue("5,0 km · 1 pasada" in banco.textos(banco.conEtiqueta("${ETIQUETA_FICHA_TRAMO_PREFIJO}b")))
        assertTrue("Sin tiempo" in banco.textos(banco.conEtiqueta("${ETIQUETA_FICHA_TRAMO_PREFIJO}c")))
        banco.pulsar(a)
        assertEquals(listOf("a"), abiertos)
        assertTrue(banco.etiquetas(ETIQUETA_FICHA_GRUPO_PREFIJO).size == 2)
    }

    @Test
    fun `sin tramos guardados la lista lo explica`() {
        poner(lleno.copy(grupos = emptyList(), regla = null))
        assertTrue(banco.hay(ETIQUETA_FICHA_TRAMOS_VACIO))
    }

    @Test
    fun `con estilo de mapa usa el mapa de MapLibre y sin el, el dibujo sin base`() {
        poner(lleno.copy(estiloMapaJson = "{}"))
        assertTrue(banco.hay(ETIQUETA_MAPA_FALSO))
    }

    @Test
    fun `sin estilo ensena el dibujo sin base y el conmutador de modo avisa`() {
        val modos = mutableListOf<ModoCalor>()
        poner(lleno, AccionesFichaCarretera(onModo = { modos += it }))
        assertTrue(banco.hay(ETIQUETA_CARRETERAS_MAPA))
        banco.pulsar(banco.pulsableConTexto("Velocidad", dentroDe = banco.conEtiqueta(ETIQUETA_CARRETERAS_MODO)))
        assertEquals(listOf(ModoCalor.VELOCIDAD), modos)
    }

    @Test
    fun `cargando ensena el indicador`() {
        poner(FichaCarreteraUiState())
        assertTrue(banco.hay(ETIQUETA_CARGANDO))
    }

    @Test
    fun `un fallo ensena el estado de error`() {
        poner(FichaCarreteraUiState(cargando = false, error = true))
        assertTrue(banco.hay(ETIQUETA_FICHA_ERROR))
    }

    @Test
    fun `una carretera sin viajes ensena que ya no esta`() {
        poner(FichaCarreteraUiState(cargando = false))
        assertTrue(banco.hay(ETIQUETA_FICHA_NO_ENCONTRADA))
        assertFalse(banco.hay(ETIQUETA_FICHA_LISTA))
    }

    @Test
    fun `volver llama a la accion`() {
        var volvio = 0
        poner(lleno, AccionesFichaCarretera(onVolver = { volvio++ }))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        assertEquals(1, volvio)
    }

    @Test
    fun `todos los botones tienen nombre para TalkBack`() {
        poner(lleno.copy(seleccionado = "a"))
        assertTrue(banco.pulsablesSinNombre().isEmpty())
    }
}
