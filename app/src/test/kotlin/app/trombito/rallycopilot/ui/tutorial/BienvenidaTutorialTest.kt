package app.trombito.rallycopilot.ui.tutorial

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

private const val MS_PASO_PAGINA = 800L

/** La bienvenida: seis páginas, indicador, «Saltar» y «Siguiente/Empezar». */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BienvenidaTutorialTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun permisos(estado: EstadoPermiso = EstadoPermiso.PENDIENTE) =
        TipoPermiso.entries.map { PermisoBienvenida(it, estado, onPedir = {}, onAbrirAjustes = {}) }

    private fun poner(
        onTerminar: () -> Unit = {},
        paginaInicial: Int = 0,
        permisos: List<PermisoBienvenida> = permisos(),
    ) = banco.poner {
        RallyCopilotTheme { BienvenidaScreen(onTerminar, permisos, paginaInicial = paginaInicial) }
    }

    private fun siguiente() {
        banco.pulsar(banco.conEtiqueta(ETIQUETA_BIENVENIDA_SIGUIENTE))
        banco.esperar(MS_PASO_PAGINA)
    }

    private fun indicador() = banco.descripcion(banco.conEtiqueta(ETIQUETA_BIENVENIDA_INDICADOR))

    @Test
    fun `empieza en la primera pagina con su titulo y el indicador`() {
        poner()
        val textos = banco.todosLosTextos()
        assertTrue(textos.toString(), "Notas de rally en cada curva" in textos)
        assertTrue("Siguiente" in textos)
        assertTrue("Saltar" in textos)
        assertEquals("Página 1 de 6", indicador())
    }

    @Test
    fun `saltar termina sin recorrer las paginas`() {
        var terminada = 0
        poner(onTerminar = { terminada++ })
        banco.pulsar(banco.conEtiqueta(ETIQUETA_BIENVENIDA_SALTAR))
        assertEquals(1, terminada)
    }

    @Test
    fun `siguiente recorre las seis paginas con sus ilustraciones y en la ultima empezar termina`() {
        var terminada = 0
        poner(onTerminar = { terminada++ })
        val titulos =
            listOf(
                "Di «Copiloto» y pregunta",
                "Cada trayecto es un tramo",
                "Radares y avisos a tiempo",
                "Todo funciona sin conexión",
                "Un último paso",
            )
        titulos.forEachIndexed { i, titulo ->
            siguiente()
            assertTrue("Falta «$titulo» en ${banco.todosLosTextos()}", titulo in banco.todosLosTextos())
            assertEquals("Página ${i + 2} de 6", indicador())
        }
        assertEquals(0, terminada)
        assertTrue("Empezar" in banco.todosLosTextos())
        assertFalse("En la última página ya no hay Saltar", banco.hay(ETIQUETA_BIENVENIDA_SALTAR))
        siguiente()
        assertEquals(1, terminada)
    }

    @Test
    fun `la pagina de permisos ofrece conceder los tres y explica lo que falla si se deniegan`() {
        poner(paginaInicial = 5)
        TipoPermiso.entries.forEach {
            assertTrue(banco.esPulsable(banco.conEtiqueta(ETIQUETA_PERMISO_BOTON_PREFIJO + it.clave)))
        }
        assertTrue("Conceder" in banco.todosLosTextos())

        val denegados = permisos(EstadoPermiso.DENEGADO)
        banco.poner { RallyCopilotTheme { BienvenidaScreen({}, denegados, paginaInicial = 5) } }
        val textos = banco.todosLosTextos()
        assertTrue(textos.toString(), "Sin ubicación no hay mapa en vivo, notas ni cronómetro." in textos)
        assertTrue("Abrir ajustes" in textos)
    }

    @Test
    fun `pedir un permiso llama a su callback`() {
        val pedidos = mutableListOf<TipoPermiso>()
        val lista = TipoPermiso.entries.map { PermisoBienvenida(it, EstadoPermiso.PENDIENTE, { pedidos += it }, {}) }
        poner(paginaInicial = 5, permisos = lista)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_PERMISO_BOTON_PREFIJO + TipoPermiso.MICROFONO.clave))
        assertEquals(listOf(TipoPermiso.MICROFONO), pedidos)
    }
}
