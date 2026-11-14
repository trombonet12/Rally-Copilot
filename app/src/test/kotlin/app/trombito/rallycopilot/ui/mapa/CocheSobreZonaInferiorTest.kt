package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.testTag
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.ui.alertas.AlertasPanel
import app.trombito.rallycopilot.ui.alertas.AlertasUiState
import app.trombito.rallycopilot.ui.alertas.MediaTramoUi
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ConEscalaDeFuente
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.navegacion.AccionesNavegacion
import app.trombito.rallycopilot.ui.navegacion.NavegacionEnMapa
import app.trombito.rallycopilot.ui.navegacion.NavegacionUiState
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ETIQUETA_MAPA_PRUEBA = "mapa-prueba"
private const val DENSIDAD_XHDPI = 2f

/** El coche que calcula la cámara queda justo por encima de la píldora de velocidad en una pantalla pequeña. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class CocheSobreZonaInferiorTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private var zona: ZonaInferiorMedida? = null

    private fun poner(
        escala: Float,
        alertas: AlertasUiState,
    ) = banco.poner {
        RallyCopilotTheme {
            ConEscalaDeFuente(escala) {
                MapaScreen(
                    estado =
                        MapaUiState(
                            cargando = false,
                            datos = EstadoDatosMapa(instalados = DatosInstalados("2026.10.1", 1_000_000, false)),
                            estilos = EstilosMapa("{}", null),
                            velocidadKmh = 148,
                            via = ViaActual("Carretera de Valldemossa", "Ma-1110", 120),
                        ),
                    vista = EstadoVistaMapa(),
                    acciones = AccionesMapa(),
                    mapa = { _, m ->
                        zona = LocalZonaInferior.current
                        Box(m.fillMaxSize().testTag(ETIQUETA_MAPA_PRUEBA))
                    },
                    navegacion =
                        NavegacionEnMapa(
                            NavegacionUiState(control = EstadoNavegacion.Libre(0L, 0.0)),
                            AccionesNavegacion(),
                        ),
                    alertas = alertas,
                )
            }
        }
        banco.esperar(300)
    }

    private fun comprobarCocheSobreLaPildora() {
        val medida = checkNotNull(zona)
        assertTrue("la zona se midió", medida.establePx.intValue > 0)
        val mapa = banco.conEtiqueta(ETIQUETA_MAPA_PRUEBA).boundsInRoot
        val fraccion =
            fraccionMarcador(mapa.width.toInt(), mapa.height.toInt(), medida.establePx.intValue, DENSIDAD_XHDPI)
        val centro = mapa.top + fraccion * mapa.height
        val bordeInferiorIcono = centro + MITAD_ICONO_MAX_DP * DENSIDAD_XHDPI
        val techoPildora = banco.conEtiqueta(ETIQUETA_VELOCIDAD).boundsInRoot.top
        assertTrue(
            "el icono del coche (hasta $bordeInferiorIcono) tapa la píldora (desde $techoPildora)",
            bordeInferiorIcono + MARGEN_SOBRE_ZONA_DP * DENSIDAD_XHDPI <= techoPildora + 1f,
        )
        // Y no queda mucho más arriba: «un pelillo» sobre la píldora (la reserva del icono más alto, 60 dp).
        assertTrue(
            "el coche queda demasiado alto: $bordeInferiorIcono frente a $techoPildora",
            techoPildora - bordeInferiorIcono <= (MARGEN_SOBRE_ZONA_DP + 2) * DENSIDAD_XHDPI + 1f || fraccion >= 0.79,
        )
    }

    @Test
    fun `con la media y la flecha de exceso al lado de la pildora el coche queda por encima de ella`() {
        val tramo = MediaTramoUi(130, supera = true, terminado = false)
        poner(1.3f, AlertasUiState(panel = AlertasPanel(rojo = true, exceso = true, tramo = tramo)))
        assertNotNull(zona)
        comprobarCocheSobreLaPildora()
        val media = banco.conEtiqueta(ETIQUETA_MEDIA).boundsInRoot
        val pildora = banco.conEtiqueta(ETIQUETA_VELOCIDAD).boundsInRoot
        assertTrue("la media va a la derecha, no encima", media.left >= pildora.right - 1f)
        assertTrue("la media no sobresale de la píldora", media.top >= pildora.top - 2f)
    }

    @Test
    fun `solo con la pildora el coche queda por encima de ella con fuente 1,3`() {
        poner(1.3f, AlertasUiState())
        comprobarCocheSobreLaPildora()
    }

    @Test
    fun `la zona medida es el alto desde el borde inferior hasta lo mas alto de la pildora`() {
        poner(1.0f, AlertasUiState(panel = AlertasPanel(tramo = MediaTramoUi(78, supera = false, terminado = false))))
        val mapa = banco.conEtiqueta(ETIQUETA_MAPA_PRUEBA).boundsInRoot
        val pildora = banco.conEtiqueta(ETIQUETA_VELOCIDAD).boundsInRoot
        val esperado = mapa.bottom - pildora.top
        // Los botones de la derecha son más altos que la píldora y no cuentan.
        assertEquals(esperado, checkNotNull(zona).medidaPx.intValue.toFloat(), 2f)
        assertEquals(checkNotNull(zona).medidaPx.intValue, zona!!.establePx.intValue)
    }
}
