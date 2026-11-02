package app.trombito.rallycopilot.ui.perfil

import app.trombito.rallycopilot.motor.analisis.PuntoEvolucion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscalaEvolucionTest {
    private fun punto(
        id: String,
        fecha: Long,
        tiempo: Long,
        record: Boolean = false,
    ) = PuntoEvolucion(id, fecha, tiempo, record)

    @Test
    fun `sin datos no hay puntos`() {
        val escala = escalarEvolucion(emptyList())
        assertTrue(escala.puntos.isEmpty())
    }

    @Test
    fun `el mejor tiempo va arriba y el peor abajo, con las fechas en proporcion`() {
        val escala =
            escalarEvolucion(
                listOf(
                    punto("1", 0L, 120_000L, record = true),
                    punto("2", 1_000L, 130_000L),
                    punto("3", 4_000L, 110_000L, record = true),
                ),
            )
        assertEquals(110_000L, escala.mejorMs)
        assertEquals(130_000L, escala.peorMs)
        assertEquals(0L, escala.desdeMs)
        assertEquals(4_000L, escala.hastaMs)
        assertEquals(listOf(0f, 0.25f, 1f), escala.puntos.map { it.x })
        assertEquals(listOf(0.5f, 1f, 0f), escala.puntos.map { it.y })
        assertEquals(listOf(true, false, true), escala.puntos.map { it.esRecord })
    }

    @Test
    fun `un solo punto queda en el centro`() {
        val escala = escalarEvolucion(listOf(punto("1", 5_000L, 100_000L, record = true)))
        assertEquals(0.5f, escala.puntos.single().x, 0f)
        assertEquals(0.5f, escala.puntos.single().y, 0f)
    }

    @Test
    fun `tiempos iguales dan una linea horizontal en el centro`() {
        val escala = escalarEvolucion(listOf(punto("1", 0L, 90_000L), punto("2", 10L, 90_000L)))
        assertEquals(listOf(0.5f, 0.5f), escala.puntos.map { it.y })
        assertEquals(listOf(0f, 1f), escala.puntos.map { it.x })
    }

    @Test
    fun `fechas iguales se reparten por orden`() {
        val escala =
            escalarEvolucion(listOf(punto("1", 7L, 100_000L), punto("2", 7L, 99_000L), punto("3", 7L, 98_000L)))
        assertEquals(listOf(0f, 0.5f, 1f), escala.puntos.map { it.x })
    }

    @Test
    fun `se ordena por fecha aunque lleguen desordenados`() {
        val escala = escalarEvolucion(listOf(punto("b", 20L, 100_000L), punto("a", 10L, 110_000L)))
        assertEquals(listOf("a", "b"), escala.puntos.map { it.pasadaId })
        assertEquals(listOf(0f, 1f), escala.puntos.map { it.x })
    }

    @Test
    fun `todo queda dentro de cero y uno`() {
        val datos = (0 until 50).map { punto("$it", it * 86_400_000L, 100_000L + (it * 7919L) % 9_000L) }
        escalarEvolucion(datos).puntos.forEach {
            assertTrue(it.x in 0f..1f)
            assertTrue(it.y in 0f..1f)
        }
    }
}
