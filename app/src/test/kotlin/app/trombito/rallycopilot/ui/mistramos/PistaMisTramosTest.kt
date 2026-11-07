package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.IntRect
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.comun.resumenFalso
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import app.trombito.rallycopilot.ui.tutorial.ETIQUETA_PISTA
import app.trombito.rallycopilot.ui.tutorial.EstadoPistas
import app.trombito.rallycopilot.ui.tutorial.LadoPista
import app.trombito.rallycopilot.ui.tutorial.LocalPistas
import app.trombito.rallycopilot.ui.tutorial.LocalPresentadorPista
import app.trombito.rallycopilot.ui.tutorial.PistaContexto
import app.trombito.rallycopilot.ui.tutorial.RETARDO_PISTA_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La ayuda de la estrella de «Mis tramos» no sale tras un diálogo ni sigue pendiente tras usar la estrella. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PistaMisTramosTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val tramo = resumenFalso("a", "Ma-2141", pasadas = 1, mejorMs = 252_300L)

    private val enLinea: @Composable (IntRect, String, LadoPista, () -> Unit) -> Unit =
        { _, texto, lado, onEntendido -> PistaContexto(texto, lado, flecha = 20, onEntendido = onEntendido) }

    private fun poner(
        estado: MisTramosUiState,
        pistas: EstadoPistas,
        acciones: AccionesMisTramos = AccionesMisTramos(),
        importacion: EstadoImportacion = EstadoImportacion.Inactivo,
    ) = banco.poner {
        RallyCopilotTheme {
            CompositionLocalProvider(LocalPistas provides pistas, LocalPresentadorPista provides enLinea) {
                MisTramosScreen(estado, acciones, importacion = importacion)
            }
        }
    }

    private val estadoBase get() = MisTramosUiState(cargando = false, tramos = listOf(tramo))

    @Test
    fun `sin dialogo la pista sale`() {
        poner(estadoBase, EstadoPistas(emptySet()) {})
        banco.esperar(RETARDO_PISTA_MS + 600)
        assertTrue(banco.hay(ETIQUETA_PISTA))
    }

    @Test
    fun `con el dialogo de importar abierto no sale la pista detras`() {
        poner(estadoBase, EstadoPistas(emptySet()) {}, importacion = EstadoImportacion.Pendiente("Mi ruta", 12_400.0))
        banco.esperar(RETARDO_PISTA_MS + 600)
        assertFalse(banco.hay(ETIQUETA_PISTA))
    }

    @Test
    fun `con el dialogo de renombrar abierto no sale la pista detras`() {
        poner(estadoBase.copy(renombrando = tramo), EstadoPistas(emptySet()) {})
        banco.esperar(RETARDO_PISTA_MS + 600)
        assertFalse(banco.hay(ETIQUETA_PISTA))
    }

    @Test
    fun `usar la estrella da la pista por entendida`() {
        val entendidas = mutableListOf<Any>()
        val pistas = EstadoPistas(emptySet()) { entendidas += it }
        poner(estadoBase, pistas)
        banco.pulsar(banco.conEtiqueta("$ETIQUETA_FAVORITO_PREFIJO${tramo.id}"))
        assertEquals(1, entendidas.size)
    }
}
