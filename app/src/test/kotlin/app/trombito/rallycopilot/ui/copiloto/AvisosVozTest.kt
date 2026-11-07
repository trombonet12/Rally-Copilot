package app.trombito.rallycopilot.ui.copiloto

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.trombito.rallycopilot.domain.voz.VelocidadHabla
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Los interruptores de avisos por voz y la velocidad de habla de la hoja del copiloto ([ControlesAvisosVoz]). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AvisosVozTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private var estado by mutableStateOf(CopilotoUiState(cargado = true))
    private val habladas = mutableListOf<VelocidadHabla>()

    private val acciones =
        AccionesCopiloto(
            onAvisarRadares = { estado = estado.copy(avisarRadares = it) },
            onAvisarIncidencias = { estado = estado.copy(avisarIncidencias = it) },
            onAvisarVelocidad = { estado = estado.copy(avisarVelocidad = it) },
            onVelocidadHabla = {
                habladas += it
                estado = estado.copy(velocidadHabla = it)
            },
        )

    private fun poner() = banco.poner { RallyCopilotTheme { ControlesAvisosVoz(estado, acciones) } }

    @Test
    fun `por defecto los tres avisos estan activados y el habla es normal`() {
        poner()

        listOf(ETIQUETA_AVISAR_RADARES, ETIQUETA_AVISAR_INCIDENCIAS, ETIQUETA_AVISAR_VELOCIDAD).forEach {
            assertEquals("Activado", banco.descripcionEstado(it))
        }
        assertEquals("Seleccionado", banco.descripcionEstado(ETIQUETA_HABLA_PREFIJO + "normal"))
        assertEquals(null, banco.descripcionEstado(ETIQUETA_HABLA_PREFIJO + "rapida"))
    }

    @Test
    fun `los controles son iconos con su nombre debajo y TalkBack los nombra`() {
        poner()

        val textos = banco.todosLosTextos()
        listOf("Radares", "Incidencias", "Velocidad", "Normal", "Rápida").forEach {
            assertTrue("Falta $it", it in textos)
        }
        assertEquals("Avisar de radares por voz", banco.descripcion(banco.conEtiqueta(ETIQUETA_AVISAR_RADARES)))
        assertEquals("Hablar más rápido", banco.descripcion(banco.conEtiqueta(ETIQUETA_HABLA_PREFIJO + "rapida")))
        assertEquals(emptyList<Any>(), banco.pulsablesSinNombre())
    }

    @Test
    fun `cada interruptor apaga y enciende solo su familia`() {
        poner()

        banco.pulsar(banco.conEtiqueta(ETIQUETA_AVISAR_RADARES))
        assertEquals(
            Triple(false, true, true),
            Triple(estado.avisarRadares, estado.avisarIncidencias, estado.avisarVelocidad),
        )
        assertEquals("Desactivado", banco.descripcionEstado(ETIQUETA_AVISAR_RADARES))

        banco.pulsar(banco.conEtiqueta(ETIQUETA_AVISAR_INCIDENCIAS))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_AVISAR_VELOCIDAD))
        assertEquals(
            Triple(false, false, false),
            Triple(estado.avisarRadares, estado.avisarIncidencias, estado.avisarVelocidad),
        )

        banco.pulsar(banco.conEtiqueta(ETIQUETA_AVISAR_VELOCIDAD))
        assertEquals(true, estado.avisarVelocidad)
        assertEquals("Activado", banco.descripcionEstado(ETIQUETA_AVISAR_VELOCIDAD))
        assertEquals("Desactivado", banco.descripcionEstado(ETIQUETA_AVISAR_INCIDENCIAS))
    }

    @Test
    fun `elegir la velocidad de habla la pide y marca la elegida`() {
        poner()

        banco.pulsar(banco.conEtiqueta(ETIQUETA_HABLA_PREFIJO + "rapida"))

        assertEquals(listOf(VelocidadHabla.RAPIDA), habladas)
        assertEquals("Seleccionado", banco.descripcionEstado(ETIQUETA_HABLA_PREFIJO + "rapida"))
        assertEquals(null, banco.descripcionEstado(ETIQUETA_HABLA_PREFIJO + "normal"))

        banco.pulsar(banco.conEtiqueta(ETIQUETA_HABLA_PREFIJO + "normal"))
        assertEquals(listOf(VelocidadHabla.RAPIDA, VelocidadHabla.NORMAL), habladas)
    }
}
