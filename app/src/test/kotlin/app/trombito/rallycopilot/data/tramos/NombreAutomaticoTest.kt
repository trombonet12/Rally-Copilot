package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.domain.tramos.NombreAutomatico
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NombreAutomaticoTest {
    @Test
    fun `con carreteras el nombre guarda titulo y subtitulo y se separan igual`() {
        val guardado = NombreAutomatico.componer("Palma → Montuïri", "Ma-13 / Ma-15")

        assertEquals("Palma → Montuïri\nMa-13 / Ma-15", guardado)
        assertEquals("Palma → Montuïri", NombreAutomatico.titulo(guardado))
        assertEquals("Ma-13 / Ma-15", NombreAutomatico.subtitulo(guardado))
    }

    @Test
    fun `sin subtitulo o con uno en blanco queda solo el titulo`() {
        assertEquals("Palma → Montuïri", NombreAutomatico.componer("Palma → Montuïri", null))
        assertEquals("Palma → Montuïri", NombreAutomatico.componer(" Palma → Montuïri ", "  "))
    }

    @Test
    fun `un nombre antiguo sin salto de linea es todo titulo`() {
        assertEquals("Ma-13 · Santa María", NombreAutomatico.titulo("Ma-13 · Santa María"))
        assertNull(NombreAutomatico.subtitulo("Ma-13 · Santa María"))
        assertNull(NombreAutomatico.subtitulo("Ma-13\n  "))
    }
}
