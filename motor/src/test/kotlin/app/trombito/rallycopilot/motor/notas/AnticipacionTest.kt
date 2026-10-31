package app.trombito.rallycopilot.motor.notas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnticipacionTest {
    private val anticipacion = Anticipacion()

    private fun nota(posicionM: Double) =
        ElementoHoja.DeNota(Nota(posicionM, posicionM + 30.0, Direccion.DERECHA, grado = 3))

    private val hoja = HojaNotas(listOf(100.0, 300.0, 320.0, 800.0).map(::nota))

    private fun selector() = SelectorNotas(anticipacion)

    private fun posiciones(visibles: List<NotaVisible>) = visibles.map { it.elemento.posicionM }

    @Test
    fun `los niveles duran lo especificado`() {
        val segundos = NivelAnticipacion.entries.map { it.segundos }
        assertEquals(listOf(8.0, 6.5, 5.0, 3.5, 2.5), segundos)
    }

    @Test
    fun `distancia es velocidad por nivel mas tiempo de voz`() {
        assertEquals(120.0, anticipacion.distanciaM(20.0, NivelAnticipacion.NORMAL), 1e-9)
        assertEquals(180.0, anticipacion.distanciaM(20.0, NivelAnticipacion.MUY_PRONTO), 1e-9)
        assertEquals(150.0, anticipacion.distanciaM(20.0, NivelAnticipacion.PRONTO), 1e-9)
        assertEquals(90.0, anticipacion.distanciaM(20.0, NivelAnticipacion.TARDE), 1e-9)
        assertEquals(70.0, anticipacion.distanciaM(20.0, NivelAnticipacion.MUY_TARDE), 1e-9)
    }

    @Test
    fun `la distancia se acota entre el minimo y el maximo`() {
        assertEquals(40.0, anticipacion.distanciaM(2.0, NivelAnticipacion.NORMAL), 1e-9)
        assertEquals(40.0, anticipacion.distanciaM(0.0, NivelAnticipacion.MUY_PRONTO), 1e-9)
        assertEquals(400.0, anticipacion.distanciaM(100.0, NivelAnticipacion.NORMAL), 1e-9)
        val acotada = Anticipacion(tVozS = 0.0, minM = 10.0, maxM = 200.0)
        assertEquals(200.0, acotada.distanciaM(50.0, NivelAnticipacion.NORMAL), 1e-9)
    }

    @Test
    fun `antes de la primera nota salen las tres primeras y solo la cercana esta activa`() {
        val visibles = selector().siguientes(hoja, 0.0, 20.0, NivelAnticipacion.NORMAL)
        assertEquals(listOf(100.0, 300.0, 320.0), posiciones(visibles))
        assertEquals(listOf(100.0, 300.0, 320.0), visibles.map { it.distanciaM })
        assertEquals(listOf(true, false, false), visibles.map { it.activa })
    }

    @Test
    fun `entre notas salta las ya pasadas`() {
        val visibles = selector().siguientes(hoja, 150.0, 20.0, NivelAnticipacion.NORMAL)
        assertEquals(listOf(300.0, 320.0, 800.0), posiciones(visibles))
        assertEquals(listOf(150.0, 170.0, 650.0), visibles.map { it.distanciaM })
        assertEquals(listOf(false, false, false), visibles.map { it.activa })
    }

    @Test
    fun `la nota que se esta pasando se ve hasta cinco metros despues`() {
        val pasando = selector().siguientes(hoja, 105.0, 20.0, NivelAnticipacion.NORMAL, n = 1).single()
        assertEquals(100.0, pasando.elemento.posicionM, 0.0)
        assertEquals(-5.0, pasando.distanciaM, 1e-9)
        assertTrue(pasando.activa)
        val pasada = selector().siguientes(hoja, 105.1, 20.0, NivelAnticipacion.NORMAL, n = 1).single()
        assertEquals(300.0, pasada.elemento.posicionM, 0.0)
    }

    @Test
    fun `activa cambia con la velocidad y con el nivel`() {
        val parado = selector().siguientes(hoja, 0.0, 5.0, NivelAnticipacion.NORMAL, n = 1).single()
        assertFalse(parado.activa)
        val rapido = selector().siguientes(hoja, 0.0, 30.0, NivelAnticipacion.NORMAL, n = 1).single()
        assertTrue(rapido.activa)
        val justo = selector().siguientes(hoja, 0.0, 30.0, NivelAnticipacion.MUY_TARDE, n = 1).single()
        assertTrue(justo.activa)
        val tarde = selector().siguientes(hoja, 0.0, 10.0, NivelAnticipacion.MUY_TARDE, n = 1).single()
        assertFalse(tarde.activa)
    }

    @Test
    fun `una nota justo a la distancia de anticipacion ya esta activa`() {
        val visible = selector().siguientes(hoja, 100.0 - 120.0, 20.0, NivelAnticipacion.NORMAL, n = 1).single()
        assertEquals(120.0, visible.distanciaM, 1e-9)
        assertTrue(visible.activa)
    }

    @Test
    fun `al final de la hoja salen las que quedan y despues ninguna`() {
        val fin = selector().siguientes(hoja, 790.0, 20.0, NivelAnticipacion.NORMAL)
        assertEquals(listOf(800.0), posiciones(fin))
        assertTrue(selector().siguientes(hoja, 900.0, 20.0, NivelAnticipacion.NORMAL).isEmpty())
    }

    @Test
    fun `respeta n y una hoja vacia no devuelve nada`() {
        val dos = selector().siguientes(hoja, 0.0, 20.0, NivelAnticipacion.NORMAL, n = 2)
        assertEquals(listOf(100.0, 300.0), posiciones(dos))
        assertTrue(selector().siguientes(hoja, 0.0, 20.0, NivelAnticipacion.NORMAL, n = 0).isEmpty())
        assertTrue(selector().siguientes(HojaNotas(emptyList()), 0.0, 20.0, NivelAnticipacion.NORMAL).isEmpty())
    }

    @Test
    fun `con una hoja grande coincide con el filtro lineal`() {
        val grande = HojaNotas((0 until 100_000).map { nota(it * 10.0) })
        for (pos in listOf(-50.0, 0.0, 4.9, 5.0, 12_345.6, 999_990.0, 999_996.0, 1_000_000.0)) {
            val esperado =
                grande.elementos
                    .filter { it.posicionM >= pos - 5.0 }
                    .take(3)
                    .map { it.posicionM }
            val obtenido = selector().siguientes(grande, pos, 20.0, NivelAnticipacion.NORMAL)
            assertEquals("pos=$pos", esperado, posiciones(obtenido))
        }
    }

    @Test
    fun `elementos con la misma posicion salen todos en su orden`() {
        val aviso = ElementoHoja.DeAviso(Aviso(300.0, TipoAviso.RASANTE))
        val conAviso = HojaNotas(listOf(nota(100.0), aviso, nota(300.0)))
        val visibles = selector().siguientes(conAviso, 250.0, 20.0, NivelAnticipacion.NORMAL)
        assertEquals(listOf(aviso, nota(300.0)), visibles.map { it.elemento })
    }
}
