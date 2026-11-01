package app.trombito.rallycopilot.ui.alertas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.motor.alertas.TipoIncidencia
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

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BannerAlertaTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private var aviso by mutableStateOf<AvisoAlerta?>(null)
    private var frescura by mutableStateOf<FrescuraIncidencias?>(null)

    private fun poner(
        inicial: AvisoAlerta?,
        compacto: Boolean = false,
        antiguedad: FrescuraIncidencias? = null,
    ) {
        aviso = inicial
        frescura = antiguedad
        banco.poner { RallyCopilotTheme { BannerAlerta(aviso, frescura, compacto = compacto) } }
    }

    private fun descripcion() = checkNotNull(banco.descripcion(banco.conEtiqueta(ETIQUETA_ALERTA_BANNER)))

    @Test
    fun `sin aviso no se pinta nada`() {
        poner(null)
        assertFalse(banco.hay(ETIQUETA_ALERTA_BANNER))
    }

    @Test
    fun `radar fijo con icono de camara, limite y distancia`() {
        poner(AvisoAlerta.Radar(ClaseRadar.FIJO, 500, 70))
        assertTrue(banco.hay(ETIQUETA_ALERTA_ICONO_PREFIJO + "FIJO"))
        val banner = banco.conEtiqueta(ETIQUETA_ALERTA_BANNER)
        assertTrue("RADAR FIJO" in banco.textos(banner))
        assertEquals(listOf("500 m"), banco.textos(banco.conEtiqueta(ETIQUETA_ALERTA_DISTANCIA)))
        assertEquals(listOf("70"), banco.textos(banco.conEtiqueta(ETIQUETA_ALERTA_LIMITE)))
        assertEquals("Radar fijo, a 500 m. Límite de velocidad: 70 kilómetros por hora", descripcion())
    }

    @Test
    fun `radar sin limite conocido no pinta la senal`() {
        poner(AvisoAlerta.Radar(ClaseRadar.FIJO, 300, null))
        assertFalse(banco.hay(ETIQUETA_ALERTA_LIMITE))
        assertEquals("Radar fijo, a 300 m", descripcion())
    }

    @Test
    fun `zona de radar movil se dice probable`() {
        poner(AvisoAlerta.Radar(ClaseRadar.MOVIL, 800, null))
        assertTrue(banco.hay(ETIQUETA_ALERTA_ICONO_PREFIJO + "MOVIL"))
        assertTrue("RADAR MÓVIL" in banco.textos(banco.conEtiqueta(ETIQUETA_ALERTA_BANNER)))
        assertTrue("Zona probable" in banco.todosLosTextos())
    }

    @Test
    fun `radar de tramo con su limite`() {
        poner(AvisoAlerta.Radar(ClaseRadar.TRAMO, 400, 60))
        assertTrue(banco.hay(ETIQUETA_ALERTA_ICONO_PREFIJO + "TRAMO"))
        assertTrue("RADAR DE TRAMO" in banco.todosLosTextos())
        assertEquals(listOf("60"), banco.textos(banco.conEtiqueta(ETIQUETA_ALERTA_LIMITE)))
    }

    @Test
    fun `cada tipo de incidencia tiene su icono y su nombre`() {
        val nombres =
            mapOf(
                TipoIncidencia.OBRAS to "OBRAS",
                TipoIncidencia.ACCIDENTE to "ACCIDENTE",
                TipoIncidencia.RETENCION to "RETENCIÓN",
                TipoIncidencia.CORTE to "CARRETERA CORTADA",
                TipoIncidencia.METEO to "PELIGRO POR EL TIEMPO",
                TipoIncidencia.OTRA to "INCIDENCIA",
            )
        poner(null)
        nombres.forEach { (tipo, nombre) ->
            aviso = AvisoAlerta.Incidencia(tipo, null, 200)
            banco.esperar(1_000)
            assertTrue("icono de $tipo", banco.hay(ETIQUETA_ALERTA_ICONO_PREFIJO + tipo.name))
            assertTrue("nombre de $tipo", nombre in banco.todosLosTextos())
        }
    }

    @Test
    fun `incidencia con carretera y distancia lo dice a TalkBack`() {
        poner(AvisoAlerta.Incidencia(TipoIncidencia.OBRAS, "Ma-10", 350))
        assertTrue("Ma-10" in banco.todosLosTextos())
        assertEquals("Obras en Ma-10, a 350 m", descripcion())
        assertFalse(banco.hay(ETIQUETA_ALERTA_LIMITE))
    }

    @Test
    fun `dentro de la incidencia no hay distancia`() {
        poner(AvisoAlerta.Incidencia(TipoIncidencia.RETENCION, null, 0))
        assertFalse(banco.hay(ETIQUETA_ALERTA_DISTANCIA))
        assertEquals("Retención, en este punto", descripcion())
    }

    @Test
    fun `la incidencia con datos viejos lleva reloj y hace cuanto`() {
        poner(
            AvisoAlerta.Incidencia(TipoIncidencia.OBRAS, "Ma-10", 350),
            antiguedad =
                FrescuraIncidencias(2 * 3_600_000L, false),
        )
        assertEquals(listOf("hace 2 h"), banco.textos(banco.conEtiqueta(ETIQUETA_ALERTA_FRESCURA)))
        assertEquals("Obras en Ma-10, a 350 m. Datos de incidencias de hace 2 h", descripcion())
    }

    @Test
    fun `con error de conexion el indicador es una nube tachada y se dice sin conexion`() {
        poner(
            AvisoAlerta.Incidencia(TipoIncidencia.CORTE, null, 100),
            antiguedad = FrescuraIncidencias(40 * 60_000L, true),
        )
        assertEquals(listOf("hace 40 min"), banco.textos(banco.conEtiqueta(ETIQUETA_ALERTA_FRESCURA)))
        assertTrue(descripcion().endsWith("Sin conexión, datos de incidencias de hace 40 min"))
    }

    @Test
    fun `sin indicador de antiguedad no se pinta`() {
        poner(AvisoAlerta.Incidencia(TipoIncidencia.OBRAS, "Ma-10", 350))
        assertFalse(banco.hay(ETIQUETA_ALERTA_FRESCURA))
    }

    @Test
    fun `un radar no lleva indicador de antiguedad aunque haya datos viejos`() {
        poner(AvisoAlerta.Radar(ClaseRadar.FIJO, 300, 50), antiguedad = FrescuraIncidencias(3 * 3_600_000L, false))
        assertFalse(banco.hay(ETIQUETA_ALERTA_FRESCURA))
    }

    @Test
    fun `es una region viva para que TalkBack lo anuncie al aparecer`() {
        poner(AvisoAlerta.Radar(ClaseRadar.FIJO, 300, 50))
        val nodo = banco.conEtiqueta(ETIQUETA_ALERTA_BANNER)
        val region = nodo.config.getOrNull(SemanticsProperties.LiveRegion)
        assertEquals(LiveRegionMode.Polite, region)
    }

    @Test
    fun `el banner entra al llegar el aviso y sale al pasar`() {
        poner(null)
        aviso = AvisoAlerta.Radar(ClaseRadar.FIJO, 500, 50)
        banco.esperar(1_000)
        assertTrue(banco.hay(ETIQUETA_ALERTA_BANNER))
        aviso = null
        banco.esperar(1_000)
        assertFalse(banco.hay(ETIQUETA_ALERTA_BANNER))
    }

    @Test
    fun `compacto cabe en una fila sin subtitulo`() {
        poner(AvisoAlerta.Radar(ClaseRadar.MOVIL, 600, 50), compacto = true)
        assertTrue(banco.hay(ETIQUETA_ALERTA_BANNER))
        assertFalse("Zona probable" in banco.todosLosTextos())
        assertTrue(banco.hay(ETIQUETA_ALERTA_LIMITE))
        assertTrue(banco.hay(ETIQUETA_ALERTA_DISTANCIA))
    }

    @Test
    fun `compacto con incidencia vieja pone el indicador al final`() {
        poner(
            AvisoAlerta.Incidencia(TipoIncidencia.OBRAS, "Ma-10", 250),
            compacto = true,
            antiguedad = FrescuraIncidencias(3_600_000L, false),
        )
        assertEquals(listOf("hace 1 h"), banco.textos(banco.conEtiqueta(ETIQUETA_ALERTA_FRESCURA)))
    }
}
