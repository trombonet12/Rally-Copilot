package app.trombito.rallycopilot.ui.navegacion

import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.util.TimeZone

/** La vista previa de rutas: hora de llegada, cifras con unidades y barra de proporción de curvas. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class VistaPreviaRutasTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val zonaOriginal = TimeZone.getDefault()

    @Before
    fun fijarZona() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    @After
    fun restaurarZona() = TimeZone.setDefault(zonaOriginal)

    private fun poner(
        vista: VistaPrevia,
        oscuro: Boolean = true,
    ) = banco.poner {
        RallyCopilotTheme(darkTheme = oscuro) {
            PanelVistaPrevia(vista, AccionesNavegacion(), horizontal = false, reloj = { 0L })
        }
    }

    private fun vista(vararg opciones: Pair<PerfilRuta, OpcionRuta>) =
        VistaPrevia(
            Destino("Sóller", SOLLER),
            PerfilRuta.MAS_RAPIDA,
            PerfilRuta.entries.associateWith { OpcionRuta.Cargando } + opciones.toMap(),
        )

    @Test
    fun `la hora de llegada suma el tiempo a la de salida en 24 horas`() {
        val utc = ZoneId.of("UTC")
        assertEquals("00:23", formatearHoraLlegada(0L, 1_380.0, utc))
        assertEquals("01:30", formatearHoraLlegada(0L, 5_400.0, utc))
        assertEquals("14:32", formatearHoraLlegada(14 * 3_600_000L + 2 * 60_000L, 1_800.0, utc))
        assertEquals("00:10", formatearHoraLlegada(23 * 3_600_000L + 50 * 60_000L, 1_200.0, utc))
        assertEquals("08:00", formatearHoraLlegada(8 * 3_600_000L, -5.0, utc))
    }

    @Test
    fun `la duracion lleva las unidades en pequeno y las cifras intactas`() {
        val texto = duracionConUnidades("1 h 15 min")
        assertEquals("1 h 15 min", texto.text)
        val unidades = texto.spanStyles.map { texto.text.substring(it.start, it.end) }
        assertEquals(listOf(" ", "h", " ", " ", "m", "i", "n"), unidades)
        assertTrue(duracionConUnidades("23").spanStyles.isEmpty())
    }

    @Test
    fun `cada ruta enseña tiempo, hora de llegada y la barra de curvas con sus kilometros`() {
        poner(
            vista(
                PerfilRuta.MAS_RAPIDA to OpcionRuta.Lista(rutaDePrueba(18_400.0, 1_380.0), curvasM = 2_000.0),
                PerfilRuta.SINUOSA to
                    OpcionRuta.Lista(rutaDePruebaDistinta(24_000.0, 4_500.0, 0.06), curvasM = 9_800.0),
            ),
        )
        assertEquals(
            listOf("Rápida", "23 min", "Llegada 00:23", "2,0 de 18,4 km"),
            banco.textos(banco.conEtiqueta(ETIQUETA_RUTA_RAPIDA)),
        )
        assertEquals(
            listOf("Curvas", "1 h 15 min", "Llegada 01:15", "9,8 de 24,0 km"),
            banco.textos(banco.conEtiqueta(ETIQUETA_RUTA_SINUOSA)),
        )
        assertEquals(listOf("Llegada 00:23"), banco.textos(banco.conEtiqueta(ETIQUETA_HORA_LLEGADA + "mas_rapida")))
    }

    @Test
    fun `el relleno de la barra es la proporcion de curvas sobre la distancia`() {
        poner(
            vista(
                PerfilRuta.MAS_RAPIDA to OpcionRuta.Lista(rutaDePrueba(20_000.0, 1_380.0), curvasM = 5_000.0),
                PerfilRuta.SINUOSA to
                    OpcionRuta.Lista(rutaDePruebaDistinta(20_000.0, 4_500.0, 0.06), curvasM = 15_000.0),
            ),
        )

        fun fraccion(perfil: String): Float {
            val pista = banco.conEtiqueta(ETIQUETA_BARRA_CURVAS + perfil).size.width
            val relleno = banco.conEtiqueta("$ETIQUETA_BARRA_CURVAS$perfil-relleno").size.width
            return relleno.toFloat() / pista
        }
        assertEquals(0.25f, fraccion("mas_rapida"), 0.02f)
        assertEquals(0.75f, fraccion("sinuosa"), 0.02f)
    }

    @Test
    fun `sin curvas medidas o casi sin curvas no hay barra y se ve la distancia`() {
        poner(
            vista(
                PerfilRuta.MAS_RAPIDA to OpcionRuta.Lista(rutaDePrueba(18_400.0, 1_380.0), curvasM = 40.0),
                PerfilRuta.SINUOSA to OpcionRuta.Lista(rutaDePruebaDistinta(24_000.0, 4_500.0, 0.06)),
            ),
        )
        assertFalse(banco.hay(ETIQUETA_BARRA_CURVAS + "mas_rapida"))
        assertFalse(banco.hay(ETIQUETA_BARRA_CURVAS + "sinuosa"))
        assertTrue("18,4 km" in banco.textos(banco.conEtiqueta(ETIQUETA_RUTA_RAPIDA)))
        assertTrue("24,0 km" in banco.textos(banco.conEtiqueta(ETIQUETA_RUTA_SINUOSA)))
    }

    @Test
    fun `mientras calcula hay esqueleto sin cifras y sin barra`() {
        poner(vista())
        val rapida = banco.conEtiqueta(ETIQUETA_RUTA_RAPIDA)
        assertEquals(listOf("Rápida"), banco.textos(rapida))
        assertEquals("Ruta más rápida: calculando", banco.descripcion(rapida))
        assertFalse(banco.hay(ETIQUETA_BARRA_CURVAS + "mas_rapida"))
    }

    @Test
    fun `en tema claro el panel sigue siendo el mismo y la descripcion incluye la llegada`() {
        poner(
            vista(PerfilRuta.MAS_RAPIDA to OpcionRuta.Lista(rutaDePrueba(18_400.0, 1_380.0), curvasM = 2_000.0)),
            oscuro = false,
        )
        assertTrue(banco.hay(ETIQUETA_VISTA_PREVIA))
        assertEquals(
            "Ruta más rápida: 23 min, 18,4 km, 2,0 km de curvas, Llegada 00:23",
            banco.descripcion(banco.conEtiqueta(ETIQUETA_RUTA_RAPIDA)),
        )
        val panel = banco.conEtiqueta(ETIQUETA_VISTA_PREVIA)
        assertEquals("Rutas disponibles", banco.descripcion(panel))
    }
}
