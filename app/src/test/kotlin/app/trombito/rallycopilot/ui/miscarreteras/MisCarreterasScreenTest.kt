package app.trombito.rallycopilot.ui.miscarreteras

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.trombito.rallycopilot.motor.analisis.CurvasPorGrado
import app.trombito.rallycopilot.motor.analisis.FichaCarretera
import app.trombito.rallycopilot.motor.analisis.Marcha
import app.trombito.rallycopilot.motor.analisis.OrdenCarreteras
import app.trombito.rallycopilot.motor.analisis.SentidoCarretera
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.RutaFichaCarretera
import app.trombito.rallycopilot.ui.RutaMisCarreteras
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ETIQUETA_CARGANDO
import app.trombito.rallycopilot.ui.comun.ETIQUETA_SEGMENTO
import app.trombito.rallycopilot.ui.comun.ETIQUETA_VOLVER
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.mapa.ModoCalor
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ETIQUETA_MAPA_FALSO = "mapa-falso"

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class MisCarreterasScreenTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private val trazos =
        listOf(
            TrazoCarretera(listOf(PuntoGeo(39.6, 2.9), PuntoGeo(39.61, 2.92)), pasadas = 1),
            TrazoCarretera(listOf(PuntoGeo(39.61, 2.92), PuntoGeo(39.63, 2.95)), pasadas = 12, velMediaKmh = 62.0),
        )
    private val ma10 =
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
    private val camino =
        FichaCarretera("~CAMIDESACALOBRA", null, "Camí de Sa Calobra", 1, 850.0, CurvasPorGrado.VACIO, null)
    private val lleno =
        MisCarreterasUiState(
            cargando = false,
            recorridos = 4,
            curvas = CurvasPorGrado(listOf(1, 2, 3, 4, 5, 6), escuadras = 2, horquillas = 7),
            trazos = trazos,
            fichas = listOf(ma10, camino),
        )

    private val mapaFalso: MapaDelCalor = { _, _, _, modificador ->
        Box(modificador.fillMaxSize().testTag(ETIQUETA_MAPA_FALSO))
    }

    private fun poner(
        estado: MisCarreterasUiState,
        acciones: AccionesMisCarreteras = AccionesMisCarreteras(),
    ) = banco.poner { RallyCopilotTheme { MisCarreterasScreen(estado, acciones, mapa = mapaFalso) } }

    private fun filaCurvas(clave: String) =
        banco.descripcion(banco.conEtiqueta("$ETIQUETA_CARRETERAS_CURVAS_FILA_PREFIJO$clave"))

    private fun tarjeta(id: String) = banco.textos(banco.conEtiqueta("$ETIQUETA_CARRETERAS_TARJETA_PREFIJO$id"))

    @Test
    fun `sin estilo de mapa dibuja el mapa de calor sin base con su leyenda y el conmutador`() {
        poner(lleno)
        assertTrue(banco.hay(ETIQUETA_CARRETERAS_MAPA))
        assertFalse(banco.hay(ETIQUETA_CARRETERAS_MAPA_BASE))
        assertFalse(banco.hay(ETIQUETA_CARRETERAS_MAPA_AVISO))
        assertTrue(banco.hay(ETIQUETA_CARRETERAS_LEYENDA))
        assertTrue(banco.hay(ETIQUETA_CARRETERAS_MODO))
    }

    @Test
    fun `con estilo de mapa usa el mapa de MapLibre en lugar del dibujo sin base`() {
        poner(lleno.copy(estiloMapaJson = "{}"))
        assertTrue(banco.hay(ETIQUETA_CARRETERAS_MAPA_BASE))
        assertTrue(banco.hay(ETIQUETA_MAPA_FALSO))
        assertFalse(banco.hay(ETIQUETA_CARRETERAS_MAPA))
    }

    @Test
    fun `en modo velocidad la leyenda es la de velocidad`() {
        poner(lleno.copy(modoCalor = ModoCalor.VELOCIDAD))
        assertTrue(banco.hay(ETIQUETA_CARRETERAS_LEYENDA_VELOCIDAD))
        assertFalse(banco.hay(ETIQUETA_CARRETERAS_LEYENDA))
    }

    @Test
    fun `el conmutador del mapa avisa del modo elegido`() {
        val modos = mutableListOf<ModoCalor>()
        poner(lleno, AccionesMisCarreteras(onModo = { modos += it }))
        val modo = banco.conEtiqueta(ETIQUETA_CARRETERAS_MODO)
        banco.pulsar(banco.pulsableConTexto("Velocidad", dentroDe = modo))
        assertEquals(listOf(ModoCalor.VELOCIDAD), modos)
        assertTrue(banco.textos(modo).containsAll(listOf("Uso", "Velocidad")))
    }

    @Test
    fun `la tarjeta de la carretera resume kilometros, viajes, ritmo y curvas por km`() {
        poner(lleno)
        val textos = tarjeta("MA10")
        assertTrue("Carretera de Valldemossa" in textos)
        // 86 km en 6 viajes; 80 km en marcha en 5 100 s = 56 km/h; 608 curvas en 86 km = 7 curvas/km.
        assertTrue("86,0 km en 6 viajes · 56 km/h · 7,1 curvas/km" in textos)
        assertTrue(textos.any { it.startsWith("Última vez:") })
    }

    @Test
    fun `una carretera sin ritmo ni curvas solo dice kilometros y viajes`() {
        poner(lleno)
        val textos = tarjeta("~CAMIDESACALOBRA")
        assertTrue("Camí de Sa Calobra" in textos)
        assertTrue("850 m en 1 viaje" in textos)
        assertFalse(textos.any { it.startsWith("Última vez") })
    }

    @Test
    fun `tocar la tarjeta abre la ficha con el id de la carretera`() {
        val abiertas = mutableListOf<String>()
        poner(lleno, AccionesMisCarreteras(onFicha = { abiertas += it }))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_CARRETERAS_TARJETA_PREFIJO + "MA10"))
        assertEquals(listOf("MA10"), abiertas)
    }

    @Test
    fun `el orden se elige con el selector y la ayuda dice como esta ordenado`() {
        val ordenes = mutableListOf<OrdenCarreteras>()
        poner(lleno, AccionesMisCarreteras(onOrden = { ordenes += it }))
        val ayuda = banco.textos(banco.conEtiqueta(ETIQUETA_CARRETERAS_ORDEN_AYUDA))
        assertTrue("Ordenadas por kilómetros conducidos" in ayuda)
        banco.pulsar(banco.pulsableConTexto("Curvas", dentroDe = banco.conEtiqueta(ETIQUETA_CARRETERAS_ORDEN)))
        assertEquals(listOf(OrdenCarreteras.CURVAS), ordenes)
    }

    @Test
    fun `la ayuda sigue al orden del estado`() {
        poner(lleno.copy(orden = OrdenCarreteras.RECIENTE))
        val ayuda = banco.textos(banco.conEtiqueta(ETIQUETA_CARRETERAS_ORDEN_AYUDA))
        assertTrue("Ordenadas por la última vez que pasaste" in ayuda)
    }

    @Test
    fun `no repite la distancia total ni los recorridos del perfil`() {
        poner(lleno)
        val textos = banco.todosLosTextos()
        assertFalse("Distancia total" in textos)
        assertFalse("Recorridos" in textos)
    }

    @Test
    fun `las barras de curvas describen cada categoria y dicen que 1 es la mas cerrada`() {
        poner(lleno)
        assertEquals("Grado 3: 3 curvas", filaCurvas("g3"))
        assertEquals("Escuadra: 2 curvas", filaCurvas("escuadra"))
        assertEquals("Horquilla: 7 curvas", filaCurvas("horquilla"))
        assertEquals(8, banco.etiquetas(ETIQUETA_CARRETERAS_CURVAS_FILA_PREFIJO).size)
        assertTrue("1 = la más cerrada · 6 = la más suave" in banco.todosLosTextos())
    }

    @Test
    fun `sin vias el mapa avisa en lugar de dibujar y no hay conmutador`() {
        poner(lleno.copy(trazos = emptyList(), fichas = emptyList(), hayVias = false))
        assertTrue(banco.hay(ETIQUETA_CARRETERAS_MAPA_AVISO))
        assertFalse(banco.hay(ETIQUETA_CARRETERAS_LEYENDA))
        assertFalse(banco.hay(ETIQUETA_CARRETERAS_MODO))
        val aviso = banco.textos(banco.conEtiqueta(ETIQUETA_CARRETERAS_MAPA)).first()
        assertTrue("Faltan los datos de carreteras de Mallorca" in aviso)
    }

    @Test
    fun `sin fichas no ensena la lista ni el selector de orden`() {
        poner(lleno.copy(fichas = emptyList()))
        assertFalse(banco.hay(ETIQUETA_CARRETERAS_ORDEN))
        assertTrue(banco.hay(ETIQUETA_CARRETERAS_CURVAS))
    }

    @Test
    fun `cargando ensena el indicador`() {
        poner(MisCarreterasUiState())
        assertTrue(banco.hay(ETIQUETA_CARGANDO))
        assertFalse(banco.hay(ETIQUETA_CARRETERAS_LISTA))
    }

    @Test
    fun `sin recorridos ensena el estado vacio`() {
        poner(MisCarreterasUiState(cargando = false))
        assertTrue(banco.hay(ETIQUETA_CARRETERAS_VACIO))
        assertFalse(banco.hay(ETIQUETA_CARRETERAS_LISTA))
    }

    @Test
    fun `un fallo ensena el estado de error`() {
        poner(MisCarreterasUiState(cargando = false, error = true))
        assertTrue(banco.hay(ETIQUETA_CARRETERAS_ERROR))
    }

    @Test
    fun `volver llama a la accion`() {
        var volvio = 0
        poner(lleno, AccionesMisCarreteras(onVolver = { volvio++ }))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        assertEquals(1, volvio)
    }

    @Test
    fun `todos los botones tienen nombre para TalkBack y los segmentos existen`() {
        poner(lleno)
        assertTrue(banco.pulsablesSinNombre().isEmpty())
        assertTrue(banco.hay("${ETIQUETA_SEGMENTO}0"))
    }

    @Test
    fun `los niveles de la escala parten en 1, 2, 5 y 10`() {
        assertEquals(NivelUso.UNA, NivelUso.de(1))
        assertEquals(NivelUso.POCAS, NivelUso.de(4))
        assertEquals(NivelUso.VARIAS, NivelUso.de(5))
        assertEquals(NivelUso.VARIAS, NivelUso.de(9))
        assertEquals(NivelUso.MUCHAS, NivelUso.de(10))
        assertEquals(NivelUso.MUCHAS, NivelUso.de(250))
    }

    @Test
    fun `en el dibujo sin base cada modo da su estilo y sin velocidad sale gris`() {
        val rapida = TrazoCarretera(emptyList(), pasadas = 1, velMediaKmh = 110.0)
        val lenta = TrazoCarretera(emptyList(), pasadas = 1, velMediaKmh = 20.0)
        val sinDato = TrazoCarretera(emptyList(), pasadas = 1)
        assertEquals(NivelUso.UNA.color, estiloDeTrazo(rapida, ModoCalor.USO).color)
        assertTrue(estiloDeTrazo(rapida, ModoCalor.VELOCIDAD).color != estiloDeTrazo(lenta, ModoCalor.VELOCIDAD).color)
        assertEquals(0, estiloDeTrazo(sinDato, ModoCalor.VELOCIDAD).orden)
        assertTrue(estiloDeTrazo(rapida, ModoCalor.VELOCIDAD).orden > estiloDeTrazo(lenta, ModoCalor.VELOCIDAD).orden)
    }

    @Composable
    private fun Navegacion() {
        val nav = rememberNavController()
        NavHost(nav, startDestination = RutaMisCarreteras) {
            composable<RutaMisCarreteras> {
                MisCarreterasScreen(
                    lleno,
                    AccionesMisCarreteras(onFicha = { nav.navigate(RutaFichaCarretera(it)) }),
                    mapa = mapaFalso,
                )
            }
            composable<RutaFichaCarretera> {
                FichaCarreteraScreen(
                    FichaCarreteraUiState(cargando = false, ficha = ma10),
                    AccionesFichaCarretera(onVolver = { nav.popBackStack() }),
                    mapa = mapaFalso,
                )
            }
        }
    }

    @Test
    fun `la tarjeta de una carretera abre su ficha y volver regresa a la lista`() {
        banco.poner { RallyCopilotTheme { Navegacion() } }
        banco.pulsar(banco.conEtiqueta(ETIQUETA_CARRETERAS_TARJETA_PREFIJO + "MA10"))
        banco.esperar(1_000)
        assertTrue(banco.hay(ETIQUETA_FICHA_LISTA))
        assertFalse(banco.hay(ETIQUETA_CARRETERAS_LISTA))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        banco.esperar(1_000)
        assertTrue(banco.hay(ETIQUETA_CARRETERAS_LISTA))
    }
}
