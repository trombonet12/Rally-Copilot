package app.trombito.rallycopilot.ui.perfil

import app.trombito.rallycopilot.domain.perfil.TableroPiloto
import app.trombito.rallycopilot.domain.perfil.UltimoViaje
import app.trombito.rallycopilot.motor.analisis.Conclusion
import app.trombito.rallycopilot.motor.analisis.TipoConclusion
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ETIQUETA_MASCOTA_VACIO
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

// Pantalla alta: la lista perezosa solo compone lo que cabe.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class PerfilScreenTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private val tablero =
        TableroPiloto(
            viajes = 10,
            kmTotales = 412.3,
            primerViajeMs = 1_762_000_000_000L,
            ultimoViaje = UltimoViaje(7L, 1_762_500_000_000L, 12_400.0, "Sóller"),
            conclusiones =
                listOf(
                    Conclusion(
                        TipoConclusion.RITMO,
                        "71 km/h",
                        "Vas a 71 km/h de media en marcha, sin contar las paradas.",
                    ),
                    Conclusion(
                        TipoConclusion.CARRETERA_FAVORITA,
                        "Ma-10",
                        "Tu carretera es la Ma-10: 6 de tus 10 viajes.",
                    ),
                    Conclusion(TipoConclusion.RECORDS, "2", "Has mejorado tu mejor tiempo 2 veces."),
                ),
        )
    private val estado = PerfilUiState(cargando = false, tablero = tablero)

    @Test
    fun `la cabecera dice desde cuando, cuantos viajes y cuantos kilometros`() {
        banco.poner { RallyCopilotTheme { PerfilScreen(estado, AccionesPerfil()) } }
        val cabecera = banco.textos(banco.conEtiqueta(ETIQUETA_PERFIL_CABECERA)).single()
        assertTrue(cabecera, cabecera.startsWith("Desde el "))
        assertTrue(cabecera, "10 viajes" in cabecera)
        assertTrue(cabecera, "412,3 km" in cabecera)
    }

    @Test
    fun `un solo viaje va en singular`() {
        val uno = estado.copy(tablero = tablero.copy(viajes = 1))
        banco.poner { RallyCopilotTheme { PerfilScreen(uno, AccionesPerfil()) } }
        val cabecera = banco.textos(banco.conEtiqueta(ETIQUETA_PERFIL_CABECERA)).single()
        assertTrue(cabecera, "1 viaje ·" in cabecera)
    }

    @Test
    fun `hay una tarjeta por conclusion con su cifra y su frase, en el orden del tablero`() {
        banco.poner { RallyCopilotTheme { PerfilScreen(estado, AccionesPerfil()) } }
        assertEquals(
            listOf("perfil-conclusion-RITMO", "perfil-conclusion-CARRETERA_FAVORITA", "perfil-conclusion-RECORDS"),
            banco.etiquetas(ETIQUETA_PERFIL_CONCLUSION_PREFIJO),
        )
        val ritmo = banco.textos(banco.conEtiqueta("${ETIQUETA_PERFIL_CONCLUSION_PREFIJO}RITMO"))
        assertTrue("Tu ritmo en marcha" in ritmo)
        assertTrue("71 km/h" in ritmo)
        assertTrue(ritmo.any { it.contains("sin contar las paradas") })
    }

    @Test
    fun `el ultimo viaje muestra el destino y abre su resumen`() {
        val abiertos = mutableListOf<Long>()
        banco.poner { RallyCopilotTheme { PerfilScreen(estado, AccionesPerfil(onVerViaje = { abiertos += it })) } }
        val textos = banco.textos(banco.conEtiqueta(ETIQUETA_PERFIL_ULTIMO_VIAJE))
        assertTrue("Último viaje" in textos)
        assertTrue("Sóller" in textos)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_PERFIL_ULTIMO_VIAJE))
        assertEquals(listOf(7L), abiertos)
    }

    @Test
    fun `un viaje libre no tiene destino y lo dice`() {
        val libre =
            estado.copy(
                tablero = tablero.copy(ultimoViaje = UltimoViaje(8L, 1_762_500_000_000L, 5_000.0, null)),
            )
        banco.poner { RallyCopilotTheme { PerfilScreen(libre, AccionesPerfil()) } }
        assertTrue("Viaje libre" in banco.textos(banco.conEtiqueta(ETIQUETA_PERFIL_ULTIMO_VIAJE)))
    }

    @Test
    fun `los enlaces abren los viajes y las carreteras`() {
        var viajes = 0
        var carreteras = 0
        banco.poner {
            RallyCopilotTheme {
                PerfilScreen(estado, AccionesPerfil(onVerViajes = { viajes++ }, onVerCarreteras = { carreteras++ }))
            }
        }
        banco.pulsar(banco.conEtiqueta(ETIQUETA_PERFIL_VER_VIAJES))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_PERFIL_VER_CARRETERAS))
        assertEquals(1, viajes)
        assertEquals(1, carreteras)
        assertTrue("Ver todos los viajes" in banco.todosLosTextos())
        assertTrue("Ver carreteras" in banco.todosLosTextos())
    }

    @Test
    fun `ya no hay mejores tiempos, historial ni evolucion`() {
        banco.poner { RallyCopilotTheme { PerfilScreen(estado, AccionesPerfil()) } }
        val textos = banco.todosLosTextos()
        listOf("Mejores tiempos", "Historial reciente", "Evolución", "Km/h medios").forEach {
            assertFalse(it, it in textos)
        }
    }

    @Test
    fun `con viajes pero sin conclusiones avisa en vez de dejar huecos`() {
        val sinConclusiones = estado.copy(tablero = tablero.copy(conclusiones = emptyList()))
        banco.poner { RallyCopilotTheme { PerfilScreen(sinConclusiones, AccionesPerfil()) } }
        assertTrue(banco.hay(ETIQUETA_PERFIL_SIN_CONCLUSIONES))
        assertTrue(banco.hay(ETIQUETA_PERFIL_ULTIMO_VIAJE))
        assertTrue(banco.etiquetas(ETIQUETA_PERFIL_CONCLUSION_PREFIJO).isEmpty())
    }

    @Test
    fun `sin viajes sale el estado vacio con Trombito dormido`() {
        banco.poner { RallyCopilotTheme { PerfilScreen(PerfilUiState(cargando = false), AccionesPerfil()) } }
        assertTrue(banco.hay(ETIQUETA_PERFIL_VACIO))
        assertTrue(banco.hay(ETIQUETA_MASCOTA_VACIO))
        assertTrue("Tu perfil empieza aquí" in banco.todosLosTextos())
        assertFalse(banco.hay(ETIQUETA_PERFIL_CABECERA))
    }

    @Test
    fun `un fallo de lectura se cuenta sin cerrar la pantalla`() {
        banco.poner {
            RallyCopilotTheme {
                PerfilScreen(
                    PerfilUiState(cargando = false, error = true),
                    AccionesPerfil(),
                )
            }
        }
        assertTrue(banco.hay(ETIQUETA_PERFIL_ERROR))
        assertFalse(banco.hay(ETIQUETA_PERFIL_VACIO))
    }

    @Test
    fun `mientras carga se ve el indicador`() {
        banco.poner { RallyCopilotTheme { PerfilScreen(PerfilUiState(), AccionesPerfil()) } }
        assertTrue(banco.hay(ETIQUETA_PERFIL_CARGANDO))
    }
}
