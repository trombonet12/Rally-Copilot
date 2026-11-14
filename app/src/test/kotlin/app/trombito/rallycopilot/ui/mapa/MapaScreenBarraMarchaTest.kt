package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.testTag
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.navegacion.ETIQUETA_BUSCADOR
import app.trombito.rallycopilot.ui.navegacion.NavegacionEnMapa
import app.trombito.rallycopilot.ui.navegacion.NavegacionUiState
import app.trombito.rallycopilot.ui.navegacion.panelGuiadoMapa
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La barra «¿A dónde?» en modo libre: parado sale; en marcha (más de 20 km/h) no, aunque no haya notas. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class MapaScreenBarraMarchaTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val datos = EstadoDatosMapa(instalados = DatosInstalados("2026.10.1", 1_000_000, conSatelite = true))
    private val mapa = MapaUiState(cargando = false, datos = datos, estilos = EstilosMapa("{\"v\":1}", null))

    private fun poner(velocidadKmh: Int?) =
        banco.poner {
            RallyCopilotTheme {
                MapaScreen(
                    estado = mapa.copy(velocidadKmh = velocidadKmh),
                    vista = EstadoVistaMapa(),
                    acciones = AccionesMapa(),
                    mapa = { _, modificador -> Box(modificador.fillMaxSize().testTag("mapa-falso")) },
                    navegacion =
                        NavegacionEnMapa(
                            NavegacionUiState(control = EstadoNavegacion.Libre(0L, 0.0)),
                            panelGuiado = panelGuiadoMapa(),
                        ),
                )
            }
        }

    @Test
    fun `parado y sin notas la barra de destino se ve`() {
        poner(velocidadKmh = 0)
        assertTrue(banco.hay(ETIQUETA_BUSCADOR))
    }

    @Test
    fun `en marcha y sin notas la barra de destino no sale`() {
        poner(velocidadKmh = 63)
        assertFalse(banco.hay(ETIQUETA_BUSCADOR))
    }
}
