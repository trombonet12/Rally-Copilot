package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ETIQUETA_SIN_GPS
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.navegacion.AccionesNavegacion
import app.trombito.rallycopilot.ui.navegacion.ETIQUETA_TARJETA_PUNTO
import app.trombito.rallycopilot.ui.navegacion.NavegacionEnMapa
import app.trombito.rallycopilot.ui.navegacion.NavegacionUiState
import app.trombito.rallycopilot.ui.navegacion.PuntoElegido
import app.trombito.rallycopilot.ui.navegacion.SOLLER
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

/** La banda de «Sin señal GPS» y la reserva del mapa sobre la tarjeta del punto elegido. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SenalGpsEnPantallaTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val datos = EstadoDatosMapa(instalados = DatosInstalados("2026.10.1", 1_000_000, conSatelite = false))

    private fun poner(
        estado: MapaUiState,
        navegacion: NavegacionUiState = NavegacionUiState(control = EstadoNavegacion.Libre(0L, 0.0)),
        acciones: AccionesMapa = AccionesMapa(),
    ) = banco.poner {
        RallyCopilotTheme {
            MapaScreen(
                estado = estado,
                vista = EstadoVistaMapa(),
                acciones = acciones,
                mapa = { _, m -> Box(m.fillMaxSize().testTag(ETIQUETA_MAPA_FALSO)) },
                navegacion = NavegacionEnMapa(navegacion, AccionesNavegacion()),
            )
        }
    }

    private fun mapa(resto: MapaUiState.() -> MapaUiState = { this }) =
        MapaUiState(cargando = false, datos = datos, estilos = EstilosMapa("{}", null)).resto()

    @Test
    fun `con señal no hay banda`() {
        poner(mapa { copy(velocidadKmh = 60) })
        assertFalse(banco.hay(ETIQUETA_SIN_GPS))
    }

    @Test
    fun `sin señal sale la banda ambar bajo lo de arriba, es region viva educada y la velocidad es una raya`() {
        poner(mapa { copy(velocidadKmh = 60, sinSenalGps = true) })
        val banda = banco.conEtiqueta(ETIQUETA_SIN_GPS)
        assertEquals(LiveRegionMode.Polite, banda.config.getOrNull(SemanticsProperties.LiveRegion))
        assertTrue("Sin señal GPS" in banco.textos(banda))
        assertTrue("—" in banco.textos(banco.conEtiqueta(ETIQUETA_VELOCIDAD)))
        val hoja = banco.conEtiqueta(ETIQUETA_HOJA).boundsInRoot
        assertTrue("la banda va arriba, sobre la hoja", banda.boundsInRoot.bottom < hoja.top)
    }

    @Test
    fun `sin tarjeta de punto el mapa no reserva nada por abajo y al cerrarla se libera`() {
        val reservas = mutableListOf<Int>()
        var punto by mutableIntStateOf(0)
        banco.poner {
            RallyCopilotTheme {
                MapaScreen(
                    estado = mapa(),
                    vista = EstadoVistaMapa(),
                    acciones = AccionesMapa(onReservaInferiorPunto = { reservas += it }),
                    mapa = { _, m -> Box(m.fillMaxSize().testTag(ETIQUETA_MAPA_FALSO)) },
                    navegacion =
                        NavegacionEnMapa(
                            NavegacionUiState(
                                control = EstadoNavegacion.Libre(0L, 0.0),
                                puntoElegido = if (punto == 1) PuntoElegido(SOLLER, "Sóller") else null,
                            ),
                            AccionesNavegacion(),
                        ),
                )
            }
        }
        assertEquals(0, reservas.last())
        punto = 1
        banco.esperar(200)
        assertTrue(banco.hay(ETIQUETA_TARJETA_PUNTO))
        reservas.clear()
        punto = 0
        banco.esperar(200)
        assertEquals(listOf(0), reservas)
    }
}
