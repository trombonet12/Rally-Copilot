package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.TipoAviso
import app.trombito.rallycopilot.motor.ruta.Maniobra
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltroIndicacionesTest {
    private val todas = FiltroIndicaciones()
    private val sinCruces = FiltroIndicaciones(avisarCruces = false)
    private val sinSalidas = FiltroIndicaciones(avisarSalidasAutopista = false)
    private val sinRotondas = FiltroIndicaciones(avisarRotondas = false)
    private val sinPuentes = FiltroIndicaciones(avisarPuentes = false)

    private fun aviso(
        posicionM: Double,
        tipo: TipoAviso,
    ) = ElementoHoja.DeAviso(Aviso(posicionM, tipo))

    private val curva = ElementoHoja.DeNota(Nota(300.0, 330.0, Direccion.DERECHA, grado = 3))
    private val hoja =
        HojaNotas(
            listOf(
                aviso(100.0, TipoAviso.CRUCE),
                aviso(200.0, TipoAviso.ROTONDA),
                curva,
                aviso(400.0, TipoAviso.PUENTE),
                aviso(500.0, TipoAviso.STOP),
            ),
        )

    private fun frases(hoja: HojaNotas) = hoja.elementos.map { it.frase }

    private fun tipos(hoja: HojaNotas) = hoja.elementos.filterIsInstance<ElementoHoja.DeAviso>().map { it.aviso.tipo }

    // Avisos de la hoja (visual y voz de las notas: ambos leen la misma hoja filtrada)

    @Test
    fun `con todo activo la hoja no cambia y es la misma instancia`() {
        assertSame(hoja, todas.aplicar(hoja))
        assertTrue(todas.todoActivo)
    }

    @Test
    fun `sin cruces la hoja pierde solo el aviso de cruce`() {
        assertEquals(
            listOf(TipoAviso.ROTONDA, TipoAviso.PUENTE, TipoAviso.STOP),
            tipos(sinCruces.aplicar(hoja)),
        )
        assertTrue(curva in sinCruces.aplicar(hoja).elementos)
    }

    @Test
    fun `sin rotondas la hoja pierde solo el aviso de rotonda`() {
        assertEquals(
            listOf(TipoAviso.CRUCE, TipoAviso.PUENTE, TipoAviso.STOP),
            tipos(sinRotondas.aplicar(hoja)),
        )
    }

    @Test
    fun `sin puentes la hoja pierde solo el aviso de puente`() {
        assertEquals(
            listOf(TipoAviso.CRUCE, TipoAviso.ROTONDA, TipoAviso.STOP),
            tipos(sinPuentes.aplicar(hoja)),
        )
        assertTrue(curva in sinPuentes.aplicar(hoja).elementos)
    }

    @Test
    fun `un puente unido a una curva se quita de su frase solo sin puentes`() {
        val unida =
            ElementoHoja.DeNota(
                Nota(300.0, 330.0, Direccion.DERECHA, grado = 3),
                avisosUnidos = listOf(Aviso(290.0, TipoAviso.PUENTE)),
            )
        assertEquals("puente, derecha tres", unida.frase)
        val hojaUnida = HojaNotas(listOf(unida))
        assertEquals("derecha tres", frases(sinPuentes.aplicar(hojaUnida)).single())
        assertEquals("puente, derecha tres", frases(sinCruces.aplicar(hojaUnida)).single())
    }

    @Test
    fun `puentes y cruces apagados dejan la rotonda y el resto, y el filtro ya no es todo activo`() {
        val filtro = FiltroIndicaciones(avisarCruces = false, avisarPuentes = false)
        assertEquals(listOf(TipoAviso.ROTONDA, TipoAviso.STOP), tipos(filtro.aplicar(hoja)))
        assertTrue(!filtro.todoActivo && !sinPuentes.todoActivo)
    }

    @Test
    fun `sin salidas de autopista la hoja no cambia porque no tiene avisos de salida`() {
        assertSame(hoja, sinSalidas.aplicar(hoja))
    }

    @Test
    fun `un cruce unido a una curva se quita de su frase y la curva se conserva`() {
        val unida =
            ElementoHoja.DeNota(
                Nota(300.0, 330.0, Direccion.DERECHA, grado = 3),
                avisosUnidos = listOf(Aviso(290.0, TipoAviso.CRUCE), Aviso(295.0, TipoAviso.RASANTE)),
            )
        assertEquals("cruce, rasante, derecha tres", unida.frase)
        val filtrada = sinCruces.aplicar(HojaNotas(listOf(unida))).elementos.single() as ElementoHoja.DeNota
        assertEquals("rasante, derecha tres", filtrada.frase)
        assertEquals(unida.nota, filtrada.nota)
    }

    @Test
    fun `una rotonda unida a una curva se quita de su frase solo sin rotondas`() {
        val unida =
            ElementoHoja.DeNota(
                Nota(300.0, 330.0, Direccion.IZQUIERDA, grado = 2),
                avisosUnidos = listOf(Aviso(290.0, TipoAviso.ROTONDA)),
            )
        assertEquals("rotonda, izquierda dos", unida.frase)
        val sin = sinRotondas.aplicar(HojaNotas(listOf(unida))).elementos.single()
        assertEquals("izquierda dos", sin.frase)
        assertEquals(
            "rotonda, izquierda dos",
            sinCruces
                .aplicar(HojaNotas(listOf(unida)))
                .elementos
                .single()
                .frase,
        )
    }

    @Test
    fun `todo desactivado quita cruces, puentes y rotondas pero deja el resto`() {
        val nada =
            FiltroIndicaciones(
                avisarCruces = false,
                avisarSalidasAutopista = false,
                avisarRotondas = false,
                avisarPuentes = false,
            )
        assertEquals(listOf(TipoAviso.STOP), tipos(nada.aplicar(hoja)))
    }

    // Maniobras de navegación (voz)

    @Test
    fun `permite las maniobras segun su familia`() {
        val salidas =
            setOf(
                TipoManiobra.SALIDA_AUTOVIA_DERECHA,
                TipoManiobra.SALIDA_AUTOVIA_IZQUIERDA,
                TipoManiobra.INCORPORACION,
            )
        val rotondas = setOf(TipoManiobra.ROTONDA_ENTRADA, TipoManiobra.ROTONDA_SALIDA)
        for (tipo in TipoManiobra.entries) {
            assertTrue("$tipo con todo activo", todas.permite(tipo))
            assertEquals("$tipo sin salidas", tipo !in salidas, sinSalidas.permite(tipo))
            assertEquals("$tipo sin rotondas", tipo !in rotondas, sinRotondas.permite(tipo))
            assertTrue("$tipo sin cruces", sinCruces.permite(tipo))
            assertTrue("$tipo sin puentes", sinPuentes.permite(tipo))
        }
    }

    @Test
    fun `girar en un cruce de la ruta se dice siempre`() {
        val nada = FiltroIndicaciones(avisarCruces = false, avisarSalidasAutopista = false, avisarRotondas = false)
        val girosYCompania =
            listOf(
                TipoManiobra.DERECHA,
                TipoManiobra.IZQUIERDA,
                TipoManiobra.LIGERA_DERECHA,
                TipoManiobra.LIGERA_IZQUIERDA,
                TipoManiobra.CERRADA_DERECHA,
                TipoManiobra.CERRADA_IZQUIERDA,
                TipoManiobra.RECTO,
                TipoManiobra.CAMBIO_SENTIDO,
                TipoManiobra.BIFURCACION_DERECHA,
                TipoManiobra.BIFURCACION_IZQUIERDA,
                TipoManiobra.DESTINO,
            )
        assertTrue(girosYCompania.all { nada.permite(it) })
    }

    // AvisosManiobra con filtro: preaviso, inminente y distinguir de las demas

    private fun maniobra(tipo: TipoManiobra) = Maniobra(tipo, "instruccion", listOf("Ma-10"), 0, 0, 100.0, 10.0, null)

    private val avisosManiobra =
        AvisosManiobra(
            maniobras =
                listOf(
                    maniobra(TipoManiobra.SALIDA),
                    maniobra(TipoManiobra.ROTONDA_ENTRADA),
                    maniobra(TipoManiobra.SALIDA_AUTOVIA_DERECHA),
                    maniobra(TipoManiobra.DERECHA),
                ),
            posicionesM = listOf(0.0, 1000.0, 1000.0, 1000.0),
        )

    private fun ids(
        filtro: FiltroIndicaciones,
        posicionM: Double,
    ) = avisosManiobra.candidatas(posicionM, 20.0, filtro).map { it.id }

    @Test
    fun `con todo activo las candidatas de maniobra no cambian`() {
        assertEquals(avisosManiobra.candidatas(700.0, 20.0), avisosManiobra.candidatas(700.0, 20.0, todas))
        assertEquals(
            listOf("maniobra-1-preaviso", "maniobra-2-preaviso", "maniobra-3-preaviso"),
            ids(todas, 700.0),
        )
    }

    @Test
    fun `sin rotondas se calla el preaviso y el inminente de la rotonda y nada mas`() {
        assertEquals(listOf("maniobra-2-preaviso", "maniobra-3-preaviso"), ids(sinRotondas, 700.0))
        assertEquals(listOf("maniobra-2-inminente", "maniobra-3-inminente"), ids(sinRotondas, 950.0))
    }

    @Test
    fun `sin salidas de autopista se calla la salida y nada mas`() {
        assertEquals(listOf("maniobra-1-preaviso", "maniobra-3-preaviso"), ids(sinSalidas, 700.0))
        assertEquals(listOf("maniobra-1-inminente", "maniobra-3-inminente"), ids(sinSalidas, 950.0))
    }

    @Test
    fun `sin cruces las maniobras se dicen igual`() {
        assertEquals(ids(todas, 700.0), ids(sinCruces, 700.0))
        assertEquals(ids(todas, 950.0), ids(sinCruces, 950.0))
    }

    // HojaFiltrada

    @Test
    fun `la hoja filtrada se reutiliza mientras no cambian la hoja ni el filtro`() {
        val cache = HojaFiltrada()
        val primera = cache.de(hoja, sinCruces)
        assertSame(primera, cache.de(hoja, FiltroIndicaciones(avisarCruces = false)))
        assertEquals(listOf(TipoAviso.ROTONDA, TipoAviso.PUENTE, TipoAviso.STOP), tipos(primera))
    }

    @Test
    fun `al cambiar el filtro o la hoja se recalcula`() {
        val cache = HojaFiltrada()
        cache.de(hoja, sinCruces)
        assertEquals(listOf(TipoAviso.CRUCE, TipoAviso.PUENTE, TipoAviso.STOP), tipos(cache.de(hoja, sinRotondas)))
        assertSame(hoja, cache.de(hoja, todas))
        val otra = HojaNotas(listOf(aviso(100.0, TipoAviso.CRUCE)))
        assertEquals(emptyList<TipoAviso>(), tipos(cache.de(otra, sinCruces)))
    }
}
