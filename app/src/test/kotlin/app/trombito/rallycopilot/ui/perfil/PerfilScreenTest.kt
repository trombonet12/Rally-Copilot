package app.trombito.rallycopilot.ui.perfil

import app.trombito.rallycopilot.motor.analisis.PasadaRegistrada
import app.trombito.rallycopilot.motor.analisis.PerfilPiloto
import app.trombito.rallycopilot.motor.analisis.RecorridoRegistrado
import app.trombito.rallycopilot.motor.analisis.TramoRegistrado
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.comun.pasadaFalsa
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
class PerfilScreenTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private val perfil =
        PerfilPiloto.de(
            tramos =
                listOf(
                    TramoRegistrado(
                        "a",
                        "Ma-2141 · Escorca",
                        4_990.0,
                        listOf(
                            PasadaRegistrada("1", pasadaFalsa("a", 250_000L), 1_760_000_000_000L),
                            PasadaRegistrada("2", pasadaFalsa("a", 240_000L), 1_761_000_000_000L),
                            PasadaRegistrada("3", pasadaFalsa("a", 245_500L), 1_762_000_000_000L),
                        ),
                    ),
                    TramoRegistrado(
                        "b",
                        "Ma-10 · Valldemossa",
                        9_990.0,
                        listOf(PasadaRegistrada("4", pasadaFalsa("b", 500_000L), 1_762_500_000_000L)),
                    ),
                ),
            recorridos = listOf(RecorridoRegistrado(1_762_500_000_000L, 42_500.0)),
        )
    private val estado = PerfilUiState(cargando = false, perfil = perfil)

    @Test
    fun `los totales muestran kilometros, tramos, pasadas y velocidad media`() {
        banco.poner { RallyCopilotTheme { PerfilScreen(estado, AccionesPerfil()) } }
        assertTrue("42,5 km" in banco.textos(banco.conEtiqueta(ETIQUETA_PERFIL_KM)))
        assertTrue("2" in banco.textos(banco.conEtiqueta(ETIQUETA_PERFIL_TRAMOS)))
        assertTrue("4" in banco.textos(banco.conEtiqueta(ETIQUETA_PERFIL_PASADAS)))
        val velocidad = banco.textos(banco.conEtiqueta(ETIQUETA_PERFIL_VELOCIDAD))
        assertTrue("VELOCIDAD MEDIA" in velocidad)
        assertTrue(velocidad.any { it.endsWith("km/h") })
    }

    @Test
    fun `los mejores tiempos listan los tramos con su mejor tiempo y el de mas pasadas va primero`() {
        banco.poner { RallyCopilotTheme { PerfilScreen(estado, AccionesPerfil()) } }
        assertEquals(listOf("perfil-mejor-a", "perfil-mejor-b"), banco.etiquetas(ETIQUETA_MEJOR_PREFIJO))
        val a = banco.textos(banco.conEtiqueta("${ETIQUETA_MEJOR_PREFIJO}a"))
        assertTrue("04:00.0" in a)
        assertTrue("Ma-2141 · Escorca" in a)
        assertTrue(a.any { it.contains("3 pasadas") })
    }

    @Test
    fun `tocar un tramo elige su evolucion`() {
        val elegidos = mutableListOf<String>()
        banco.poner { RallyCopilotTheme { PerfilScreen(estado, AccionesPerfil(onSeleccionar = { elegidos += it })) } }
        banco.pulsar(banco.conEtiqueta("${ETIQUETA_MEJOR_PREFIJO}b"))
        assertEquals(listOf("b"), elegidos)
    }

    @Test
    fun `la evolucion es la del tramo elegido y el grafico se describe`() {
        banco.poner { RallyCopilotTheme { PerfilScreen(estado.copy(seleccionadoId = "a"), AccionesPerfil()) } }
        assertEquals("Ma-2141 · Escorca", banco.textos(banco.conEtiqueta(ETIQUETA_EVOLUCION_TITULO)).single())
        val descripcion = banco.descripcion(banco.conEtiqueta(ETIQUETA_GRAFICO))
        assertEquals("Evolución del tiempo: 3 pasadas, mejor tiempo 04:00.0, 2 récords", descripcion)
    }

    // Pantalla alta: la lista perezosa solo compone lo que cabe.
    @Test
    @Config(qualifiers = "w411dp-h2400dp-xxhdpi")
    fun `el historial muestra las pasadas recientes con su diferencia y los records`() {
        banco.poner { RallyCopilotTheme { PerfilScreen(estado, AccionesPerfil()) } }
        val ultimas = banco.etiquetas(ETIQUETA_HISTORIAL_PREFIJO)
        assertEquals(
            listOf("perfil-historial-4", "perfil-historial-3", "perfil-historial-2", "perfil-historial-1"),
            ultimas,
        )
        val tercera = banco.textos(banco.conEtiqueta("${ETIQUETA_HISTORIAL_PREFIJO}3"))
        assertTrue("04:05.5" in tercera)
        assertTrue("+5,5" in tercera)
        val mejor = banco.textos(banco.conEtiqueta("${ETIQUETA_HISTORIAL_PREFIJO}2"))
        assertTrue("MEJOR" in mejor)
        assertTrue("RÉCORD" in mejor)
    }

    @Test
    fun `sin datos sale el estado vacio amable`() {
        val vacio = PerfilUiState(cargando = false, perfil = PerfilPiloto.de(emptyList(), emptyList()))
        banco.poner { RallyCopilotTheme { PerfilScreen(vacio, AccionesPerfil()) } }
        assertTrue(banco.hay(ETIQUETA_PERFIL_VACIO))
        assertTrue("TU PERFIL EMPIEZA AQUÍ" in banco.todosLosTextos())
        assertFalse(banco.hay(ETIQUETA_PERFIL_KM))
    }

    @Test
    fun `con kilometros pero sin tramos avisa en vez de dejar huecos`() {
        val soloKm = PerfilPiloto.de(emptyList(), listOf(RecorridoRegistrado(1L, 3_000.0)))
        banco.poner { RallyCopilotTheme { PerfilScreen(PerfilUiState(false, soloKm), AccionesPerfil()) } }
        assertTrue("3,0 km" in banco.textos(banco.conEtiqueta(ETIQUETA_PERFIL_KM)))
        assertTrue("Todavía no has completado ningún tramo." in banco.todosLosTextos())
        assertFalse(banco.hay(ETIQUETA_GRAFICO))
    }

    @Test
    fun `mientras carga se ve el indicador`() {
        banco.poner { RallyCopilotTheme { PerfilScreen(PerfilUiState(), AccionesPerfil()) } }
        assertTrue(banco.hay(ETIQUETA_PERFIL_CARGANDO))
    }
}
