package app.trombito.rallycopilot.data.datosmapa

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.datos.Complemento
import org.junit.Assert.assertEquals
import org.junit.Test

class ComplementosElegidosTest {
    @Test
    fun `sin voz ni relieve no se pide nada`() {
        assertEquals(emptySet<Complemento>(), AjustesConduccion().complementosElegidos())
        assertEquals(emptySet<Complemento>(), AjustesConduccion(voz = VozCopiloto.DAVEFX).complementosElegidos())
    }

    @Test
    fun `sharvard y relieve se piden cuando estan elegidos`() {
        val ajustes = AjustesConduccion(voz = VozCopiloto.SHARVARD, relieve = true)

        assertEquals(setOf(Complemento.VOZ_SHARVARD, Complemento.RELIEVE), ajustes.complementosElegidos())
    }
}
