package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** REN-15: la estela se parte en cabeza (cambia por bloques) y cola (cambia siempre) para no reenviarla entera. */
class EstelaPartidaTest {
    private fun puntos(n: Int) = (0 until n).map { PuntoGeo(39.70, 2.90 + it * 0.0001) }

    @Test
    fun `sin estela no hay nada que pintar`() {
        val partida = partirEstela(emptyList())
        assertTrue(partida.cabeza.isEmpty() && partida.cola.isEmpty())
    }

    @Test
    fun `un segmento corto va entero a la cola`() {
        val corto = puntos(PUNTOS_COLA_MINIMOS + BLOQUE_CABEZA - 1)
        val partida = partirEstela(listOf(corto))
        assertTrue(partida.cabeza.isEmpty())
        assertEquals(listOf(corto), partida.cola)
    }

    @Test
    fun `la cabeza crece de bloque en bloque y la cola repite su ultimo punto para no cortar la linea`() {
        val largo = puntos(PUNTOS_COLA_MINIMOS + 2 * BLOQUE_CABEZA + 7)
        val partida = partirEstela(listOf(largo))
        val cabeza = partida.cabeza.single()
        val cola = partida.cola.single()
        assertEquals(2 * BLOQUE_CABEZA, cabeza.size)
        assertEquals(cabeza.last(), cola.first())
        assertEquals(largo.last(), cola.last())
        assertEquals(largo.size - cabeza.size + 1, cola.size)
        assertTrue("la cola nunca baja de lo minimo", cola.size >= PUNTOS_COLA_MINIMOS)
    }

    @Test
    fun `entre dos publicaciones del mismo bloque la cabeza no cambia y la cola si`() {
        val base = puntos(PUNTOS_COLA_MINIMOS + BLOQUE_CABEZA + 3)
        val antes = partirEstela(listOf(base))
        val despues = partirEstela(listOf(base + PuntoGeo(39.71, 2.95)))
        assertEquals(antes.clave(), despues.clave())
        assertNotEquals(antes.cola, despues.cola)
    }

    @Test
    fun `al completar otro bloque la cabeza cambia y hay que reenviarla`() {
        val bloque = PUNTOS_COLA_MINIMOS + BLOQUE_CABEZA
        val antes = partirEstela(listOf(puntos(bloque)))
        val despues = partirEstela(listOf(puntos(bloque + BLOQUE_CABEZA)))
        assertNotEquals(antes.clave(), despues.clave())
    }

    @Test
    fun `los segmentos anteriores van enteros a la cabeza y un segmento nuevo cambia la clave`() {
        val anterior = puntos(5)
        val partida = partirEstela(listOf(anterior, puntos(4)))
        assertEquals(listOf(anterior), partida.cabeza)
        assertEquals(1, partida.cola.size)
        assertNotEquals(partirEstela(listOf(anterior)).clave(), partida.clave())
    }
}
