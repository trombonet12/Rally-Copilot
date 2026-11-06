package app.trombito.rallycopilot.motor.edicion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Las órdenes de navegación por voz: ir a un lugar, parada, casa, cancelar, el más cercano y «otro». */
class OrdenesLugarTest {
    private fun orden(texto: String) = InterpreteOrdenes.interpretar(texto)

    @Test
    fun `llevame a y vamos a dan el lugar normalizado`() {
        assertEquals(Orden.IrA("soller"), orden("llévame a Sóller"))
        assertEquals(Orden.IrA("soller"), orden("vamos a sóller"))
        assertEquals(Orden.IrA("cala d or"), orden("llévame a cala d or"))
    }

    @Test
    fun `el relleno de delante y de detras no forma parte del lugar`() {
        assertEquals(Orden.IrA("inca"), orden("oye copiloto, llévame a Inca, por favor"))
        assertEquals(Orden.IrA("puerto de pollensa"), orden("llévame al puerto de Pollensa"))
    }

    @Test
    fun `un lugar con una palabra de orden sigue siendo un lugar`() {
        assertEquals(Orden.IrA("son salida"), orden("llévame a Son Salida"))
        assertEquals(Orden.IrA("son radar"), orden("vamos a Son Radar"))
    }

    @Test
    fun `anade parada en da la parada`() {
        assertEquals(Orden.ParadaEn("manacor"), orden("añade parada en Manacor"))
        assertEquals(Orden.ParadaEn("manacor"), orden("añade una parada en Manacor"))
        assertEquals(Orden.ParadaEn("sa pobla"), orden("añade parada en sa pobla"))
    }

    @Test
    fun `llevame a casa es volver a casa y las frases fijas tambien`() {
        assertEquals(Orden.VolverACasa, orden("llévame a casa"))
        assertEquals(Orden.VolverACasa, orden("vamos a casa"))
        assertEquals(Orden.VolverACasa, orden("volver a casa"))
        assertEquals(Orden.VolverACasa, orden("vuelve a casa"))
        assertEquals(Orden.VolverACasa, orden("ir a casa"))
        assertEquals(Orden.ParadaEn("casa"), orden("añade parada en casa"))
    }

    @Test
    fun `sin lugar no hay orden de lugar`() {
        assertNull(orden("llévame a"))
        assertNull(orden("vamos"))
        assertNull(orden("añade parada en"))
    }

    @Test
    fun `cancelar la ruta pide la orden entera y no se confunde con cancelar a secas`() {
        assertEquals(Orden.CancelarRuta, orden("cancela la ruta"))
        assertEquals(Orden.CancelarRuta, orden("quita la ruta"))
        assertEquals(Orden.CancelarRuta, orden("cancelar la ruta"))
        assertEquals(Orden.Confirma(false), orden("cancela"))
        assertEquals(Orden.Quitar, orden("quita"))
    }

    @Test
    fun `lo mas cercano de cada categoria`() {
        assertEquals(Orden.MasCercano(TipoLugar.GASOLINERA), orden("gasolinera más cercana"))
        assertEquals(Orden.MasCercano(TipoLugar.GASOLINERA), orden("la gasolinera más cercana"))
        assertEquals(Orden.MasCercano(TipoLugar.HOSPITAL), orden("hospital más cercano"))
        assertEquals(Orden.MasCercano(TipoLugar.FARMACIA), orden("farmacia más próxima"))
        assertEquals(Orden.MasCercano(TipoLugar.APARCAMIENTO), orden("aparcamiento cercano"))
        assertEquals(Orden.MasCercano(TipoLugar.CAFETERIA), orden("cafetería más cercana"))
    }

    @Test
    fun `otro solo vale solo`() {
        assertEquals(Orden.Otro, orden("otro"))
        assertEquals(Orden.Otro, orden("oye copiloto otro"))
        assertEquals(Orden.Repite, orden("otra vez"))
        assertNull(orden("otro derecha"))
    }

    @Test
    fun `cada tipo de lugar tiene frases en su genero`() {
        assertEquals(
            listOf("gasolinera más cercana", "gasolinera más próxima", "gasolinera cercana"),
            TipoLugar.GASOLINERA.frases(),
        )
        assertEquals(
            listOf("hospital más cercano", "hospital más próximo", "hospital cercano"),
            TipoLugar.HOSPITAL.frases(),
        )
    }
}
