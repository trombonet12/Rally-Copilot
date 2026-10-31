package app.trombito.rallycopilot.ui.maniobras

import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconoManiobraTest {
    @Test
    fun `cada tipo tiene icono en las tres variantes`() {
        for (variante in VarianteManiobra.entries) {
            for (tipo in TipoManiobra.entries) {
                assertTrue("$tipo $variante", iconoManiobra(tipo, variante, null) != 0)
            }
        }
    }

    @Test
    fun `los iconos de una variante no se repiten salvo las dos rotondas`() {
        for (variante in VarianteManiobra.entries) {
            val ids = TipoManiobra.entries.map { iconoManiobra(it, variante, null) }
            assertEquals(TipoManiobra.entries.size - 1, ids.toSet().size)
        }
        assertEquals(
            iconoManiobra(TipoManiobra.ROTONDA_ENTRADA, VarianteManiobra.NOTA_RALLY, 1),
            iconoManiobra(TipoManiobra.ROTONDA_SALIDA, VarianteManiobra.NOTA_RALLY, 3),
        )
    }

    @Test
    fun `las variantes dan recursos distintos para el mismo tipo`() {
        for (tipo in TipoManiobra.entries) {
            val ids = VarianteManiobra.entries.map { iconoManiobra(tipo, it, null) }
            assertEquals(ids.size, ids.toSet().size)
        }
        assertNotEquals(
            iconoManiobra(TipoManiobra.IZQUIERDA, VarianteManiobra.CHEVRON_HUD, null),
            iconoManiobra(TipoManiobra.DERECHA, VarianteManiobra.CHEVRON_HUD, null),
        )
    }
}
