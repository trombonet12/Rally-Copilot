package app.trombito.rallycopilot.motor.voz

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Tabla de qué modo del copiloto permite qué tipo de frase: los cuatro modos × los cuatro tipos. */
@RunWith(Parameterized::class)
class ModoCopilotoTest(
    private val modo: ModoCopiloto,
    private val tipo: TipoFrase,
    private val esperado: Boolean,
) {
    @Test
    fun `el modo permite o calla el tipo segun la tabla`() {
        assertEquals("$modo permite $tipo", esperado, modo.permite(tipo))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} / {1} = {2}")
        fun casos(): List<Array<Any>> =
            listOf(
                arrayOf(ModoCopiloto.SILENCIO, TipoFrase.ALERTA, false),
                arrayOf(ModoCopiloto.SILENCIO, TipoFrase.MANIOBRA, false),
                arrayOf(ModoCopiloto.SILENCIO, TipoFrase.NOTA, false),
                arrayOf(ModoCopiloto.SILENCIO, TipoFrase.CRONO, false),
                arrayOf(ModoCopiloto.SOLO_ALERTAS, TipoFrase.ALERTA, true),
                arrayOf(ModoCopiloto.SOLO_ALERTAS, TipoFrase.MANIOBRA, true),
                arrayOf(ModoCopiloto.SOLO_ALERTAS, TipoFrase.NOTA, false),
                arrayOf(ModoCopiloto.SOLO_ALERTAS, TipoFrase.CRONO, false),
                arrayOf(ModoCopiloto.NOTAS, TipoFrase.ALERTA, true),
                arrayOf(ModoCopiloto.NOTAS, TipoFrase.MANIOBRA, true),
                arrayOf(ModoCopiloto.NOTAS, TipoFrase.NOTA, true),
                arrayOf(ModoCopiloto.NOTAS, TipoFrase.CRONO, false),
                arrayOf(ModoCopiloto.COMPLETO, TipoFrase.ALERTA, true),
                arrayOf(ModoCopiloto.COMPLETO, TipoFrase.MANIOBRA, true),
                arrayOf(ModoCopiloto.COMPLETO, TipoFrase.NOTA, true),
                arrayOf(ModoCopiloto.COMPLETO, TipoFrase.CRONO, true),
            )
    }
}

class ModoCopilotoDefectoTest {
    @Test
    fun `por defecto el modo es completo`() {
        assertEquals(ModoCopiloto.COMPLETO, ModoCopiloto.POR_DEFECTO)
    }

    @Test
    fun `la prioridad va de alerta a crono`() {
        assertEquals(
            listOf(TipoFrase.ALERTA, TipoFrase.MANIOBRA, TipoFrase.NOTA, TipoFrase.CRONO),
            TipoFrase.entries.sortedBy { it.prioridad },
        )
    }
}
