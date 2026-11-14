package app.trombito.rallycopilot.ui.mapa

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.navegacion.AccionesNavegacion
import app.trombito.rallycopilot.ui.navegacion.NavegacionEnMapa
import app.trombito.rallycopilot.ui.navegacion.NavegacionUiState
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La pantalla solo se mantiene encendida conduciendo (Libre o Guiando); en reposo se apaga con el tiempo del móvil. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PantallaEncendidaMapaTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val datos = EstadoDatosMapa(instalados = DatosInstalados("2026.10.1", 1_000_000, conSatelite = false))

    /** Monta el mapa y devuelve la vista Compose; [control] se puede cambiar después. */
    private fun montar(control: () -> EstadoNavegacion): View {
        lateinit var vista: View
        banco.poner {
            vista = LocalView.current
            RallyCopilotTheme {
                MapaScreen(
                    estado = MapaUiState(cargando = false, datos = datos, estilos = EstilosMapa("{}", null)),
                    vista = EstadoVistaMapa(),
                    acciones = AccionesMapa(),
                    mapa = { _, m -> Box(m.fillMaxSize()) },
                    navegacion = NavegacionEnMapa(NavegacionUiState(control = control()), AccionesNavegacion()),
                )
            }
        }
        return vista
    }

    @Test
    fun `en reposo la pantalla no se mantiene encendida`() {
        assertFalse(montar { EstadoNavegacion.Inactiva }.keepScreenOn)
    }

    @Test
    fun `en modo libre la pantalla se mantiene encendida`() {
        assertTrue(montar { EstadoNavegacion.Libre(0L, 0.0) }.keepScreenOn)
    }

    @Test
    fun `el flag se pone al empezar a conducir y se quita al terminar`() {
        var control: EstadoNavegacion by mutableStateOf(EstadoNavegacion.Inactiva)
        val vista = montar { control }
        assertFalse(vista.keepScreenOn)
        control = EstadoNavegacion.Libre(0L, 0.0)
        banco.esperar(100)
        assertTrue(vista.keepScreenOn)
        control = EstadoNavegacion.Inactiva
        banco.esperar(100)
        assertFalse(vista.keepScreenOn)
    }
}
