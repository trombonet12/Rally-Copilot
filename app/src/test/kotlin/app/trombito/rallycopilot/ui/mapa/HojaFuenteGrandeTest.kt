package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ConEscalaDeFuente
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import app.trombito.rallycopilot.ui.tramos.AHORA_PRUEBA_MS
import app.trombito.rallycopilot.ui.tramos.cronoSinTramo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** UX-15: la hoja de conducción con la fuente del sistema a 1,3 y a 2,0 no desborda ni saca nada de pantalla. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HojaFuenteGrandeTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val acciones = AccionesHoja({}, {}, {}, {})

    private fun poner(escala: Float) =
        banco.poner {
            RallyCopilotTheme {
                ConEscalaDeFuente(escala) {
                    HojaConduccion(
                        DatosHoja(
                            MapaUiState(cargando = false, via = ViaActual("Carretera de Valldemossa", "Ma-1110", 50)),
                            EstadoNavegacion.Libre(0L, 3_240.0),
                            cronoSinTramo(95_000L),
                        ),
                        acciones,
                        reloj = { AHORA_PRUEBA_MS },
                    )
                }
            }
        }

    @Test
    fun `con 1,3 y 2,0 los textos y botones de la hoja quedan dentro de ella`() {
        listOf(1.3f, 2.0f).forEach { escala ->
            poner(escala)
            val hoja = banco.conEtiqueta(ETIQUETA_HOJA).boundsInRoot
            val raiz = banco.raiz().boundsInRoot
            assertTrue("con $escala la hoja $hoja sale de $raiz", hoja.left >= 0f && hoja.right <= raiz.right)
            banco.nodos().filter { it.children.isEmpty() && it.size.width > 0 }.forEach {
                val b = it.boundsInRoot
                assertTrue(
                    "con $escala un elemento $b sale de la hoja $hoja",
                    b.left >= hoja.left - 1 && b.right <= hoja.right + 1 && b.top >= hoja.top - 1 &&
                        b.bottom <= hoja.bottom + 1,
                )
            }
            assertTrue(banco.hay(ETIQUETA_KM_RECORRIDOS))
        }
    }
}
