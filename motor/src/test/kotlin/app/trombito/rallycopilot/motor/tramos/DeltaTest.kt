package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.motor.voz.TipoFrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeltaTest {
    /** Pasada de 4 km a velocidad constante [mS], con una marca cada 500 m. */
    private fun pasada(mS: Double) =
        Pasada(
            "t",
            (0..8).map { MarcaTiempo(it * 500.0, Math.round(it * 500.0 / mS * 1000.0)) },
            Math.round(4000.0 / mS * 1000.0),
        )

    private val lenta = pasada(20.0) // 200 s
    private val rapida = pasada(25.0) // 160 s

    @Test
    fun `delta por distancia interpolado entre marcas`() {
        // A 750 m: lenta 37,5 s, rápida 30 s.
        assertEquals(7_500L, Delta.enDistancia(lenta, rapida, 750.0))
        assertEquals(-7_500L, Delta.enDistancia(rapida, lenta, 750.0))
        assertEquals(40_000L, Delta.enDistancia(lenta, rapida, 4000.0))
    }

    @Test
    fun `delta fuera del rango de la pasada es nulo`() {
        assertNull(Delta.enDistancia(lenta, rapida, 4500.0))
        assertNull(Delta.enDistancia(lenta, rapida, -1.0))
    }

    @Test
    fun `parciales de sector con acumulado y diferencia del propio sector`() {
        val parciales = Delta.parciales(listOf(1000.0, 2000.0, 3000.0), 4000.0, lenta, rapida)
        assertEquals(4, parciales.size)
        assertEquals(listOf(50_000L, 50_000L, 50_000L, 50_000L), parciales.map { it.actualMs })
        assertEquals(listOf(40_000L, 40_000L, 40_000L, 40_000L), parciales.map { it.mejorMs })
        assertEquals(listOf(10_000L, 10_000L, 10_000L, 10_000L), parciales.map { it.diferenciaMs })
        assertEquals(listOf(10_000L, 20_000L, 30_000L, 40_000L), parciales.map { it.deltaAcumuladoMs })
        assertEquals(listOf(1, 2, 3, 4), parciales.map { it.numero })
    }

    @Test
    fun `texto de voz con decimales hasta diez segundos`() {
        assertEquals("más dos décimas", Delta.texto(200))
        assertEquals("más una décima", Delta.texto(100))
        assertEquals("menos uno con tres", Delta.texto(-1_300))
        assertEquals("más cuatro segundos", Delta.texto(4_000))
        assertEquals("más un segundo", Delta.texto(1_000))
        assertEquals("menos nueve con nueve", Delta.texto(-9_900))
    }

    @Test
    fun `texto de voz con segundos enteros desde diez`() {
        assertEquals("más diez segundos", Delta.texto(10_000))
        assertEquals("más diez segundos", Delta.texto(9_960))
        assertEquals("menos doce segundos", Delta.texto(-12_400))
        assertEquals("más veintiún segundos", Delta.texto(21_000))
        assertEquals("más treinta y un segundos", Delta.texto(31_000))
        assertEquals("menos cuarenta y cinco segundos", Delta.texto(-45_200))
    }

    @Test
    fun `igual cuando la diferencia es menor de cinco centesimas`() {
        assertEquals("igual", Delta.texto(0))
        assertEquals("igual", Delta.texto(49))
        assertEquals("igual", Delta.texto(-49))
        assertEquals("más una décima", Delta.texto(50))
        assertEquals("menos una décima", Delta.texto(-50))
    }

    @Test
    fun `la frase de sector y de meta es de tipo crono, sin posicion y con id estable por pasada`() {
        val sector = Delta.fraseSector("t", "p1", 2, -1_300)
        assertEquals("menos uno con tres", sector.texto)
        assertEquals(TipoFrase.CRONO, sector.tipo)
        assertNull(sector.puntoM)
        assertEquals(sector, Delta.fraseSector("t", "p1", 2, -1_300))
        assertFalse(sector.id == Delta.fraseSector("t", "p2", 2, -1_300).id)
        assertFalse(sector.id == Delta.fraseSector("t", "p1", 3, -1_300).id)
        val meta = Delta.fraseMeta("t", "p1", 4_000)
        assertEquals("más cuatro segundos", meta.texto)
        assertEquals(TipoFrase.CRONO, meta.tipo)
        assertFalse(meta.id == sector.id)
    }

    @Test
    fun `solo el modo completo deja hablar al cronometraje`() {
        val frase = Delta.fraseMeta("t", "p1", 0)
        assertTrue(ModoCopiloto.COMPLETO.permite(frase.tipo))
        assertFalse(ModoCopiloto.NOTAS.permite(frase.tipo))
    }
}
