package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
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

/** El menú de capas con el interruptor de calor y sus dos modos, y la ficha de la carretera tocada. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class MapaScreenCalorTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val datos = EstadoDatosMapa(instalados = DatosInstalados("2026.10.1", 1_000_000, conSatelite = true))
    private val mapaFalso: @Composable (String, Modifier) -> Unit = { _, modificador ->
        Box(modificador.fillMaxSize())
    }

    private val estilos = EstilosMapa(vectorial = "{}", satelite = null)

    private fun conCalor(calor: CalorUi = CalorUi()) =
        MapaUiState(cargando = false, datos = datos, estilos = estilos, calor = calor)

    private fun poner(
        estado: MapaUiState,
        acciones: AccionesMapa = AccionesMapa(),
    ) = banco.poner { RallyCopilotTheme { MapaScreen(estado, EstadoVistaMapa(), acciones, mapaFalso) } }

    private fun abrirCapas() = banco.pulsar(banco.conEtiqueta(ETIQUETA_CAPAS_BOTON))

    @Test
    fun `el menu de capas trae el interruptor de calor y los dos modos solo con iconos`() {
        poner(conCalor())
        abrirCapas()
        listOf(ETIQUETA_CALOR, ETIQUETA_CALOR_USO, ETIQUETA_CALOR_VELOCIDAD).forEach { etiqueta ->
            val nodo = banco.conEtiqueta(etiqueta)
            assertTrue("$etiqueta no debe llevar texto", banco.textos(nodo).isEmpty())
            assertTrue("$etiqueta necesita descripcion", !banco.descripcion(nodo).isNullOrBlank())
        }
    }

    @Test
    fun `el interruptor pide lo contrario de su estado`() {
        val pedidos = mutableListOf<Boolean>()
        val acciones = AccionesMapa(onCalor = { pedidos += it })
        var estado by mutableStateOf(conCalor(CalorUi(activo = true)))
        banco.poner { RallyCopilotTheme { MapaScreen(estado, EstadoVistaMapa(), acciones, mapaFalso) } }
        abrirCapas()
        assertEquals("Activado", banco.descripcionEstado(ETIQUETA_CALOR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_CALOR))
        assertEquals(listOf(false), pedidos)
        estado = conCalor(CalorUi(activo = false))
        banco.reposo()
        assertEquals("Desactivado", banco.descripcionEstado(ETIQUETA_CALOR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_CALOR))
        assertEquals(listOf(false, true), pedidos)
    }

    @Test
    fun `los modos piden su valor y el elegido queda seleccionado`() {
        val pedidos = mutableListOf<ModoCalor>()
        poner(
            conCalor(CalorUi(modo = ModoCalor.VELOCIDAD)),
            AccionesMapa(onModoCalor = { pedidos += it }),
        )
        abrirCapas()
        assertEquals("Seleccionado", banco.descripcionEstado(ETIQUETA_CALOR_VELOCIDAD))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_CALOR_USO))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_CALOR_VELOCIDAD))
        assertEquals(listOf(ModoCalor.USO, ModoCalor.VELOCIDAD), pedidos)
    }

    @Test
    fun `con la capa apagada los modos no se pueden cambiar`() {
        poner(conCalor(CalorUi(activo = false)))
        abrirCapas()
        assertFalse(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_CALOR_USO)))
        assertFalse(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_CALOR_VELOCIDAD)))
        assertTrue(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_CALOR)))
    }

    @Test
    fun `sin ficha no hay tarjeta`() {
        poner(conCalor())
        assertFalse(banco.hay(ETIQUETA_CALOR_FICHA))
    }

    @Test
    fun `la ficha enseña carretera, pasadas, kilometros y velocidad media y se cierra`() {
        var cierres = 0
        val ficha = FichaCalor("Ma-10", "Carretera de Valldemossa", 12, 34.56, 62.4)
        poner(conCalor(CalorUi(ficha = ficha)), AccionesMapa(onCerrarCalor = { cierres++ }))
        assertTrue(banco.hay(ETIQUETA_CALOR_FICHA))
        assertEquals(
            listOf("Ma-10 · Carretera de Valldemossa"),
            banco.textos(banco.conEtiqueta(ETIQUETA_CALOR_FICHA_TITULO)),
        )
        assertEquals("12 pasadas", banco.descripcion(banco.conEtiqueta(ETIQUETA_CALOR_FICHA_PASADAS)))
        // El formato decimal sigue el idioma del móvil (coma en español): el test no lo fija.
        assertEquals("%.1f km".format(34.56), banco.descripcion(banco.conEtiqueta(ETIQUETA_CALOR_FICHA_KM)))
        assertEquals("62 km/h de media", banco.descripcion(banco.conEtiqueta(ETIQUETA_CALOR_FICHA_VELOCIDAD)))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_CALOR_FICHA_CERRAR))
        assertEquals(1, cierres)
    }

    @Test
    fun `una ficha sin nombre ni velocidad lo dice`() {
        poner(conCalor(CalorUi(ficha = FichaCalor(null, null, 1, 2.0, null))))
        assertEquals(
            listOf("Carretera sin nombre"),
            banco.textos(banco.conEtiqueta(ETIQUETA_CALOR_FICHA_TITULO)),
        )
        assertEquals("1 pasada", banco.descripcion(banco.conEtiqueta(ETIQUETA_CALOR_FICHA_PASADAS)))
        assertEquals("Sin velocidad", banco.descripcion(banco.conEtiqueta(ETIQUETA_CALOR_FICHA_VELOCIDAD)))
    }
}
