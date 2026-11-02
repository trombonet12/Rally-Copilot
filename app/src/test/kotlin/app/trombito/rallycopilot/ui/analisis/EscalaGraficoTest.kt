package app.trombito.rallycopilot.ui.analisis

import app.trombito.rallycopilot.motor.analisis.PuntoSerie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EscalaGraficoTest {
    private fun serie(vararg pares: Pair<Double, Double>) = pares.map { PuntoSerie(it.first, it.second) }

    @Test
    fun `la escala de velocidad sube al multiplo de 20 que abarca las dos series`() {
        val propia = serie(0.0 to 30.0, 100.0 to 87.0)
        val comparada = serie(0.0 to 25.0, 100.0 to 103.0)
        val escala = escalaVelocidad(listOf(propia, comparada), 4_000.0)
        assertEquals(0.0, escala.minimo, 0.0)
        assertEquals(120.0, escala.maximo, 0.0)
        assertEquals(4_000.0, escala.distanciaMaxM, 0.0)
    }

    @Test
    fun `una traza casi parada no hace una escala minuscula`() {
        val escala = escalaVelocidad(listOf(serie(0.0 to 3.0, 50.0 to 5.0)), 1_000.0)
        assertEquals(40.0, escala.maximo, 0.0)
        assertEquals(40.0, escalaVelocidad(emptyList(), 1_000.0).maximo, 0.0)
    }

    @Test
    fun `la escala del delta es simetrica con un techo redondo`() {
        val escala = escalaDelta(serie(0.0 to 0.0, 100.0 to -3_400.0, 200.0 to 1_200.0), 2_000.0)
        assertEquals(-5_000.0, escala.minimo, 1e-6)
        assertEquals(5_000.0, escala.maximo, 1e-6)
        assertEquals(0.5f, escala.fraccionY(0.0), 1e-6f)
    }

    @Test
    fun `el delta tiene un minimo de un segundo y escala exacta no se redondea de mas`() {
        assertEquals(1_000.0, escalaDelta(emptyList(), 100.0).maximo, 1e-6)
        assertEquals(2_000.0, escalaDelta(serie(0.0 to 2_000.0), 100.0).maximo, 1e-6)
        assertEquals(20_000.0, escalaDelta(serie(0.0 to 12_000.0), 100.0).maximo, 1e-6)
    }

    @Test
    fun `las fracciones van de 0 a 1 y se acotan`() {
        val escala = EscalaGrafico(distanciaMaxM = 1_000.0, minimo = 0.0, maximo = 100.0)
        assertEquals(0f, escala.fraccionX(0.0), 0f)
        assertEquals(0.25f, escala.fraccionX(250.0), 1e-6f)
        assertEquals(1f, escala.fraccionX(5_000.0), 0f)
        assertEquals(0f, escala.fraccionX(-10.0), 0f)
        // Arriba es el máximo
        assertEquals(0f, escala.fraccionY(100.0), 0f)
        assertEquals(1f, escala.fraccionY(0.0), 0f)
        assertEquals(0.75f, escala.fraccionY(25.0), 1e-6f)
        assertEquals(1f, escala.fraccionY(-50.0), 0f)
    }

    @Test
    fun `una escala degenerada no divide por cero`() {
        val plana = EscalaGrafico(0.0, 5.0, 5.0)
        assertEquals(0f, plana.fraccionX(10.0), 0f)
        assertEquals(0.5f, plana.fraccionY(5.0), 0f)
    }

    @Test
    fun `el toque se convierte en distancia dentro del area y se acota fuera`() {
        val escala = EscalaGrafico(4_000.0, 0.0, 100.0)
        // Área: empieza en 100 px y mide 400 px
        assertEquals(2_000.0, distanciaDeToque(300f, 100f, 400f, escala), 1e-6)
        assertEquals(0.0, distanciaDeToque(20f, 100f, 400f, escala), 0.0)
        assertEquals(4_000.0, distanciaDeToque(900f, 100f, 400f, escala), 0.0)
        assertEquals(0.0, distanciaDeToque(300f, 100f, 0f, escala), 0.0)
        assertEquals(1_000.0, escala.distanciaEn(0.25f), 1e-6)
    }

    @Test
    fun `el valor en una distancia se interpola y se acota a los extremos`() {
        val s = serie(0.0 to 50.0, 100.0 to 100.0, 200.0 to 60.0)
        assertEquals(75.0, valorEn(s, 50.0)!!, 1e-9)
        assertEquals(80.0, valorEn(s, 150.0)!!, 1e-9)
        assertEquals(50.0, valorEn(s, -10.0)!!, 0.0)
        assertEquals(60.0, valorEn(s, 900.0)!!, 0.0)
        assertEquals(100.0, valorEn(s, 100.0)!!, 0.0)
        assertNull(valorEn(emptyList(), 10.0))
    }

    @Test
    fun `las marcas del eje son redondas y ascendentes`() {
        assertEquals(listOf(0.0, 20.0, 40.0, 60.0, 80.0, 100.0, 120.0), marcasEje(0.0, 120.0, 6))
        assertEquals(listOf(0.0, 50.0, 100.0), marcasEje(0.0, 120.0, 3))
        assertEquals(listOf(0.0, 2.0, 4.0), marcasEje(0.0, 4.4, 4))
        assertEquals(listOf(0.0, 0.5, 1.0, 1.5), marcasEje(0.0, 1.7, 4))
        assertTrue(marcasEje(10.0, 10.0).isEmpty())
        assertTrue(marcasEje(0.0, 10.0, 0).isEmpty())
    }

    @Test
    fun `el techo redondo es 1, 2, 5 o 10 por una potencia de diez`() {
        assertEquals(1.0, techoRedondo(0.7), 1e-9)
        assertEquals(2.0, techoRedondo(1.1), 1e-9)
        assertEquals(5.0, techoRedondo(3.0), 1e-9)
        assertEquals(10.0, techoRedondo(5.1), 1e-9)
        assertEquals(500.0, techoRedondo(500.0), 1e-9)
        assertEquals(1.0, techoRedondo(0.0), 0.0)
    }

    @Test
    fun `las etiquetas de los ejes usan coma decimal y menos tipografico`() {
        assertEquals("0", etiquetaKm(0.0))
        assertEquals("2", etiquetaKm(2.0))
        assertEquals("1,5", etiquetaKm(1.5))
        assertEquals("+5", etiquetaDeltaS(5.0))
        assertEquals("−5", etiquetaDeltaS(-5.0))
        assertEquals("0", etiquetaDeltaS(0.0))
        assertEquals("+0,5", etiquetaDeltaS(0.5))
    }
}
