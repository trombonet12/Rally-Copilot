package app.trombito.rallycopilot.domain.navegacion

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EdicionParadasTest {
    private val a = Destino("A", PuntoGeo(39.1, 2.1))
    private val b = Destino("B", PuntoGeo(39.2, 2.2))
    private val c = Destino("C", PuntoGeo(39.3, 2.3))

    @Test
    fun `anadir pone la parada al final`() {
        assertEquals(listOf(a, b), EdicionParadas.anadir(listOf(a), b))
        assertEquals(listOf(a), EdicionParadas.anadir(emptyList(), a))
    }

    @Test
    fun `no se pasa del maximo de paradas`() {
        val llena = List(EdicionParadas.MAX_PARADAS) { Destino("P$it", PuntoGeo(39.0 + it / 100.0, 2.5)) }
        assertSame(llena, EdicionParadas.anadir(llena, a))
    }

    @Test
    fun `quitar saca la del indice y un indice fuera de rango no cambia nada`() {
        assertEquals(listOf(a, c), EdicionParadas.quitar(listOf(a, b, c), 1))
        val lista = listOf(a, b)
        assertSame(lista, EdicionParadas.quitar(lista, 2))
        assertSame(lista, EdicionParadas.quitar(lista, -1))
    }

    @Test
    fun `mover deja la parada en la posicion pedida`() {
        assertEquals(listOf(c, a, b), EdicionParadas.mover(listOf(a, b, c), 2, 0))
        assertEquals(listOf(b, c, a), EdicionParadas.mover(listOf(a, b, c), 0, 2))
        assertEquals(listOf(a, c, b), EdicionParadas.mover(listOf(a, b, c), 1, 2))
    }

    @Test
    fun `mover a la misma posicion o fuera de rango no cambia nada`() {
        val lista = listOf(a, b, c)
        assertSame(lista, EdicionParadas.mover(lista, 1, 1))
        assertSame(lista, EdicionParadas.mover(lista, 0, 3))
        assertSame(lista, EdicionParadas.mover(lista, -1, 1))
    }

    @Test
    fun `las ordenes se aplican a la lista`() {
        assertEquals(listOf(a, b), OrdenParadas.Anadir(b).aplicar(listOf(a)))
        assertEquals(listOf(b), OrdenParadas.Quitar(0).aplicar(listOf(a, b)))
        assertEquals(listOf(b, a), OrdenParadas.Mover(1, 0).aplicar(listOf(a, b)))
    }
}
