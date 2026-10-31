package app.trombito.rallycopilot.ui.navegacion.notas

import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.GradoSimplificado
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.TipoAviso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconosNotaTest {
    @Test
    fun `cada grado y direccion tiene su icono y las horquillas el suyo`() {
        val iconos =
            Direccion.entries.flatMap { dir -> (1..6).map { iconoCurva(nota(it, dir), simplificada = false) } } +
                Direccion.entries.map { iconoCurva(nota(null, it), simplificada = false) }
        assertEquals(14, iconos.toSet().size)
    }

    @Test
    fun `las notas simplificadas dibujan un icono por categoria`() {
        val cerrada = iconoCurva(nota(1), simplificada = true)
        val media = iconoCurva(nota(3), simplificada = true)
        val suave = iconoCurva(nota(5), simplificada = true)
        assertEquals(3, setOf(cerrada, media, suave).size)
        assertEquals(iconoCurva(nota(2), simplificada = true), cerrada)
        assertEquals(iconoCurva(nota(4), simplificada = true), media)
        assertEquals(iconoCurva(nota(6), simplificada = true), suave)
        assertNotEquals(iconoCurva(nota(null), simplificada = true), cerrada)
    }

    @Test
    fun `todo tipo de aviso tiene icono y texto`() {
        TipoAviso.entries.forEach {
            assertTrue("icono de $it", iconoAviso(it) != 0)
            assertTrue("texto de $it", nombreAviso(it) != 0)
        }
        // Rasante y cambio de rasante comparten dibujo; el resto son distintos.
        val distintos =
            TipoAviso.entries
                .map { iconoAviso(it) }
                .toSet()
                .size
        assertEquals(TipoAviso.entries.size - 1, distintos)
    }

    @Test
    fun `todo modificador y categoria simplificada tiene texto`() {
        Modificador.entries.forEach { assertTrue(nombreModificador(it) != 0) }
        GradoSimplificado.entries.forEach { assertTrue(nombreSimplificado(it) != 0) }
    }
}
