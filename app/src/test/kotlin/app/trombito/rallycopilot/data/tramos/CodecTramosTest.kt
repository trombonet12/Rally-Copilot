package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.motor.tramos.MarcaTiempo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecTramosTest {
    @Test
    fun `los cortes ida y vuelta sin perder precision y vacio es lista vacia`() {
        val cortes = listOf(1000.0, 2531.3333333333335, 3000.5)
        assertEquals(cortes, CodecTramos.decodificarCortes(CodecTramos.codificarCortes(cortes)))
        assertEquals("", CodecTramos.codificarCortes(emptyList()))
        assertEquals(emptyList<Double>(), CodecTramos.decodificarCortes(""))
    }

    @Test
    fun `las marcas ida y vuelta con el milimetro y el milisegundo exactos`() {
        val marcas = listOf(MarcaTiempo(0.0, 0), MarcaTiempo(12.345, 1_234), MarcaTiempo(1000.0, 61_000))

        val vuelta = CodecTramos.decodificarMarcas(CodecTramos.codificarMarcas(marcas))

        assertEquals(marcas.map { it.tMs }, vuelta.map { it.tMs })
        assertEquals(marcas.map { it.distanciaM }, vuelta.map { it.distanciaM })
        assertEquals(emptyList<MarcaTiempo>(), CodecTramos.decodificarMarcas(""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unas marcas ilegibles lanzan IllegalArgumentException`() {
        CodecTramos.decodificarMarcas("1:2;basura")
    }

    @Test
    fun `compactar deja una marca cada 50 m y conserva salida, meta y cortes exactos`() {
        // Una marca cada 10 m a 10 m/s durante 1 km, más el corte exacto en 333,3 m.
        val marcas = (0..100).map { MarcaTiempo(it * 10.0, it * 1000L) } + MarcaTiempo(333.3, 33_330)
        val ordenadas = marcas.sortedBy { it.distanciaM }

        val compactas = CodecTramos.compactar(ordenadas, listOf(333.3))

        assertTrue("${compactas.size} marcas", compactas.size in 20..30)
        assertEquals(0.0, compactas.first().distanciaM, 0.0)
        assertEquals(1000.0, compactas.last().distanciaM, 0.0)
        assertTrue(compactas.any { it.distanciaM == 333.3 })
        assertTrue(compactas.zipWithNext().all { (a, b) -> b.distanciaM > a.distanciaM })
    }

    @Test
    fun `compactar conserva la marca anterior a una parada larga`() {
        val marcas =
            listOf(
                MarcaTiempo(0.0, 0),
                MarcaTiempo(10.0, 1_000),
                MarcaTiempo(20.0, 2_000),
                MarcaTiempo(30.0, 60_000), // parado casi un minuto
                MarcaTiempo(40.0, 61_000),
            )

        val compactas = CodecTramos.compactar(marcas, emptyList())

        assertEquals(listOf(0.0, 20.0, 40.0), compactas.map { it.distanciaM })
    }
}
