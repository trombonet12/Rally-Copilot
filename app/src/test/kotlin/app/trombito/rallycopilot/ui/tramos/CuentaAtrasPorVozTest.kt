package app.trombito.rallycopilot.ui.tramos

import app.trombito.rallycopilot.domain.escucha.DialogoVoz
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.motor.edicion.Orden
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La orden de voz «salida» con la salida manual enciende las luces de la cuenta atrás, sin voz propia. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CuentaAtrasPorVozTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val esperando = cronoListo().copy(esperaSalidaManual = true)

    private fun poner(
        dialogo: DialogoVoz?,
        crono: CronoEnCurso? = esperando,
    ) = banco.poner { RallyCopilotTheme { CuentaAtrasPorVoz(dialogo, crono) } }

    @Test
    fun `la orden salida sin respuesta enciende las luces`() {
        poner(DialogoVoz(1, "salida", null, Orden.Salida))
        banco.esperar(300)
        assertTrue(banco.hay(ETIQUETA_CUENTA_ATRAS))
        assertTrue(banco.hay(ETIQUETA_CUENTA_ATRAS_LUZ_PREFIJO + 1))
    }

    @Test
    fun `con respuesta (salida automatica o nada pendiente) no hay luces`() {
        poner(DialogoVoz(1, "salida", "la salida es automática", Orden.Salida))
        banco.esperar(300)
        assertFalse(banco.hay(ETIQUETA_CUENTA_ATRAS))
    }

    @Test
    fun `otra orden no enciende las luces`() {
        poner(DialogoVoz(1, "modo notas", null, Orden.Silencio))
        banco.esperar(300)
        assertFalse(banco.hay(ETIQUETA_CUENTA_ATRAS))
    }

    @Test
    fun `sin salida manual pendiente no hay luces`() {
        poner(DialogoVoz(1, "salida", null, Orden.Salida), crono = cronoListo())
        banco.esperar(300)
        assertFalse(banco.hay(ETIQUETA_CUENTA_ATRAS))
    }
}
