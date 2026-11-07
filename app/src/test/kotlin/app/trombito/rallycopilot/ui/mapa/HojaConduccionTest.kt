package app.trombito.rallycopilot.ui.mapa

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.motor.ruta.EstadoRuta
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.navegacion.ETIQUETA_BUSCAR_GUIANDO
import app.trombito.rallycopilot.ui.navegacion.ETIQUETA_SALIR_MAPA
import app.trombito.rallycopilot.ui.navegacion.ETIQUETA_TERMINAR
import app.trombito.rallycopilot.ui.navegacion.SOLLER
import app.trombito.rallycopilot.ui.navegacion.guiado.ETIQUETA_PIE_DISTANCIA
import app.trombito.rallycopilot.ui.navegacion.guiado.ETIQUETA_PIE_GUIADO
import app.trombito.rallycopilot.ui.navegacion.guiado.ETIQUETA_PIE_HORA
import app.trombito.rallycopilot.ui.navegacion.guiado.ETIQUETA_PIE_TIEMPO
import app.trombito.rallycopilot.ui.navegacion.rutaDePrueba
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import app.trombito.rallycopilot.ui.tramos.AHORA_PRUEBA_MS
import app.trombito.rallycopilot.ui.tramos.ETIQUETA_CRONO_MINI
import app.trombito.rallycopilot.ui.tramos.cronoConocido
import app.trombito.rallycopilot.ui.tramos.cronoListo
import app.trombito.rallycopilot.ui.tramos.cronoSinTramo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ETIQUETA_HOJA_PRUEBA = "panel"

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HojaConduccionTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private var salidas = 0
    private var terminadas = 0
    private var busquedasLibres = 0
    private var busquedasGuiando = 0

    private val acciones =
        AccionesHoja(
            onSalir = { salidas++ },
            onTerminar = { terminadas++ },
            onBuscarDestino = { busquedasLibres++ },
            onBuscarGuiando = { busquedasGuiando++ },
        )

    private val via = ViaActual("Carretera de Valldemossa", "Ma-1110", 50)
    private val libre = EstadoNavegacion.Libre(0L, 3_240.0)

    private fun estado(resto: MapaUiState.() -> MapaUiState = { this }) =
        MapaUiState(cargando = false, via = via).resto()

    private fun progreso() =
        EstadoRuta(
            progresoM = 1_000.0,
            distanciaRestanteM = 9_600.0,
            tiempoRestanteS = 17 * 60.0 + 10.0,
            llegadaMs = 1_700_000_000_000L,
            maniobraActual = null,
            distanciaAManiobraM = 0.0,
            maniobraSiguiente = null,
            distanciaALaRutaM = 0.0,
            llegado = false,
        )

    private fun guiando(conProgreso: Boolean = true) =
        EstadoNavegacion.Guiando(
            Destino("Sóller", SOLLER),
            PerfilRuta.MAS_RAPIDA,
            rutaDePrueba(),
            if (conProgreso) progreso() else null,
            false,
        )

    private fun poner(
        control: EstadoNavegacion,
        estado: MapaUiState = estado(),
        crono: CronoEnCurso? = null,
        acc: AccionesHoja = acciones,
    ) = banco.poner {
        RallyCopilotTheme { HojaConduccion(DatosHoja(estado, control, crono), acc, reloj = { AHORA_PRUEBA_MS }) }
    }

    private fun textos(etiqueta: String) = banco.textos(banco.conEtiqueta(etiqueta))

    @Test
    fun `libre parado enseña LISTO, la placa de la via y los kilometros recorridos`() {
        poner(libre, crono = cronoListo())
        assertEquals(listOf("LISTO"), textos(ETIQUETA_CRONO_MINI))
        assertTrue(banco.hay(ETIQUETA_VIA))
        assertEquals(listOf("Ma-1110"), banco.textos(banco.conEtiqueta(ETIQUETA_VIA)))
        assertEquals(listOf("3,2 km"), textos(ETIQUETA_KM_RECORRIDOS))
        assertEquals("3,2 km recorridos", banco.descripcion(banco.conEtiqueta(ETIQUETA_KM_RECORRIDOS)))
    }

    @Test
    fun `libre en marcha lleva el cronometro grande ambar y la hoja es compacta`() {
        poner(libre, crono = cronoSinTramo(95_000L))
        assertEquals(listOf("01:35"), textos(ETIQUETA_CRONO_MINI))
        val hoja = banco.conEtiqueta(ETIQUETA_HOJA_PRUEBA).boundsInRoot
        assertTrue("hoja compacta de 96-112 dp: ${hoja.height / 3}", hoja.height <= 112 * 3f && hoja.height >= 96 * 3f)
    }

    @Test
    fun `con tramo conocido el cronometro grande va arriba y la hoja no lo repite`() {
        poner(libre, crono = cronoConocido())
        assertFalse(banco.hay(ETIQUETA_CRONO_MINI))
        assertTrue(banco.hay(ETIQUETA_VIA))
    }

    @Test
    fun `la placa de la via solo sale si coincide con el camino de la posicion`() {
        poner(libre, estado { copy(viaCoincide = false) })
        assertFalse(banco.hay(ETIQUETA_VIA))
    }

    @Test
    fun `sin ref la via sale con su nombre y sin via no sale nada`() {
        poner(libre, estado { copy(via = ViaActual("Carrer Major", null, null)) })
        assertEquals(listOf("Carrer Major"), banco.textos(banco.conEtiqueta(ETIQUETA_VIA)))
        poner(libre, estado { copy(via = null) })
        assertFalse(banco.hay(ETIQUETA_VIA))
    }

    @Test
    fun `con un nombre de via largo los kilometros se ven enteros dentro de la hoja`() {
        val largo = ViaActual("Carrer de Joan Mas i Matas de Son Juny i Es Campet de Baix", null, null)
        poner(libre, estado { copy(via = largo) })
        val km = banco.conEtiqueta(ETIQUETA_KM_RECORRIDOS).boundsInRoot
        val via = banco.conEtiqueta(ETIQUETA_VIA).boundsInRoot
        val buscar = banco.conEtiqueta(ETIQUETA_BUSCAR_DESTINO).boundsInRoot
        assertTrue("los km no se meten bajo la lupa", km.right <= buscar.left)
        assertTrue("la vía cede ancho a los km", via.right <= km.left)
    }

    @Test
    fun `la X de libre cierra el mapa y la lupa lleva al campo de destino`() {
        poner(libre)
        assertFalse(banco.hay(ETIQUETA_TERMINAR))
        val salir = banco.conEtiqueta(ETIQUETA_SALIR_MAPA)
        assertTrue(salir.size.width >= 48 * 3 && salir.size.height >= 48 * 3)
        assertEquals("Cerrar el mapa", banco.descripcion(salir))
        banco.pulsar(salir)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_BUSCAR_DESTINO))
        assertEquals(1, salidas)
        assertEquals(1, busquedasLibres)
        assertEquals(0, terminadas)
    }

    @Test
    fun `sin campo de destino la lupa no se pinta pero el hueco se conserva`() {
        poner(libre, acc = AccionesHoja({}, {}, null, {}))
        assertFalse(banco.hay(ETIQUETA_BUSCAR_DESTINO))
        assertTrue(banco.hay(ETIQUETA_SALIR_MAPA))
    }

    @Test
    fun `guiando enseña el tiempo restante, los km y la hora, la X termina y la lupa busca`() {
        poner(guiando())
        assertEquals(listOf("17 min"), textos(ETIQUETA_PIE_TIEMPO))
        assertEquals(listOf("9,6 km"), textos(ETIQUETA_PIE_DISTANCIA))
        assertTrue(Regex("""\d\d:\d\d""").matches(textos(ETIQUETA_PIE_HORA).single()))
        assertTrue(banco.hay(ETIQUETA_PIE_GUIADO))
        assertFalse(banco.hay(ETIQUETA_SALIR_MAPA))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_TERMINAR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_BUSCAR_GUIANDO))
        assertEquals(1, terminadas)
        assertEquals(1, busquedasGuiando)
        assertEquals(0, salidas)
    }

    @Test
    fun `guiando sin posicion aun no hay pie pero si la X`() {
        poner(guiando(conProgreso = false))
        assertFalse(banco.hay(ETIQUETA_PIE_GUIADO))
        assertTrue(banco.hay(ETIQUETA_TERMINAR))
    }

    @Test
    fun `guiando el cronometro pequeno va junto al tiempo`() {
        poner(guiando(), crono = cronoSinTramo(95_000L))
        assertEquals(listOf("01:35"), textos(ETIQUETA_CRONO_MINI))
        val pie = banco.conEtiqueta(ETIQUETA_PIE_GUIADO).boundsInRoot
        val crono = banco.conEtiqueta(ETIQUETA_CRONO_MINI).boundsInRoot
        assertTrue("el crono junto al pie: $crono vs $pie", crono.left >= pie.right - 1)
    }

    @Test
    fun `sin señal GPS el cronometro lleva el icono de pausa`() {
        var sinSenal by mutableStateOf(false)
        banco.poner {
            RallyCopilotTheme {
                HojaConduccion(
                    DatosHoja(estado { copy(sinSenalGps = sinSenal) }, libre, cronoSinTramo(95_000L)),
                    acciones,
                )
            }
        }

        fun pausas() = banco.nodos().mapNotNull { banco.descripcion(it) }.count { it.startsWith("Cronómetro en pausa") }
        assertEquals(0, pausas())
        sinSenal = true
        banco.reposo()
        assertEquals(1, pausas())
    }

    @Test
    fun `la hoja de libre y la de guiado caben en 112 dp`() {
        poner(libre, crono = cronoListo())
        val alturaLibre = banco.conEtiqueta(ETIQUETA_HOJA_PRUEBA).boundsInRoot.height
        poner(guiando(), crono = cronoSinTramo())
        val alturaGuiado = banco.conEtiqueta(ETIQUETA_HOJA_PRUEBA).boundsInRoot.height
        assertTrue(
            "libre $alturaLibre y guiado $alturaGuiado entran en 112 dp",
            maxOf(alturaLibre, alturaGuiado) <= 112 * 3f,
        )
    }

    @Test
    fun `refsDePlaca separa los ref de OSM sin vacios ni repetidos`() {
        assertEquals(listOf("Ma-10", "Ma-2141"), refsDePlaca(" Ma-10; ;Ma-2141;Ma-10 "))
        assertEquals(emptyList<String>(), refsDePlaca(null))
        assertEquals(2, refsDePlaca("A;B;C").size)
    }
}
