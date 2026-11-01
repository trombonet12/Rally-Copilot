package app.trombito.rallycopilot.ui.navegacion.notas

import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.notas.Direccion
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
        val muyCerrada = iconoCurva(nota(1), simplificada = true)
        val cerrada = iconoCurva(nota(2), simplificada = true)
        val media = iconoCurva(nota(3), simplificada = true)
        val suave = iconoCurva(nota(5), simplificada = true)
        assertEquals(4, setOf(muyCerrada, cerrada, media, suave).size)
        assertEquals(iconoCurva(nota(4), simplificada = true), media)
        assertEquals(iconoCurva(nota(6), simplificada = true), suave)
        assertNotEquals(iconoCurva(nota(null), simplificada = true), cerrada)
    }

    @Test
    fun `la muy cerrada simplificada dibuja el icono del grado 1 y la cerrada el del 2`() {
        assertEquals(iconoCurva(nota(1), simplificada = false), iconoCurva(nota(1), simplificada = true))
        assertEquals(iconoCurva(nota(2), simplificada = false), iconoCurva(nota(2), simplificada = true))
        assertEquals(
            iconoCurva(nota(1, Direccion.IZQUIERDA), simplificada = false),
            iconoCurva(nota(1, Direccion.IZQUIERDA), simplificada = true),
        )
    }

    @Test
    fun `la compresion tiene su propio icono distinto del de la rasante`() {
        assertEquals(R.drawable.ic_nota_compresion, iconoAviso(TipoAviso.COMPRESION))
        assertNotEquals(iconoAviso(TipoAviso.RASANTE), iconoAviso(TipoAviso.COMPRESION))
        assertEquals("compresión", TipoAviso.COMPRESION.frase)
    }

    @Test
    fun `todo tipo de aviso tiene icono y frase`() {
        TipoAviso.entries.forEach {
            assertTrue("icono de $it", iconoAviso(it) != 0)
            assertTrue("frase de $it", it.frase.isNotEmpty())
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
    fun `el resalto se llama resalto y conserva el icono del bache`() {
        assertEquals("cuidado, resalto", TipoAviso.BACHE.frase)
        assertTrue(TipoAviso.BACHE.esCuidado)
        assertTrue(iconoAviso(TipoAviso.BACHE) != 0)
    }
}
