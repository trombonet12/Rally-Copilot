package app.trombito.rallycopilot.motor.notas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RemuestreoCurvaturaTest {
    @Test
    fun `remuestrea a 5 m y s mide la longitud de arco`() {
        val traza = Remuestreo.remuestrear(TrazasDePrueba.recta(1000.0, pasoM = 50.0))
        assertEquals(201, traza.size)
        assertEquals(0.0, traza.s.first(), 1e-9)
        assertEquals(1000.0, traza.s.last(), 0.5)
        for (i in 1 until traza.size) {
            assertEquals(5.0, traza.s[i] - traza.s[i - 1], 1e-9)
        }
    }

    @Test
    fun `una recta sigue recta tras suavizar y su curvatura es cero`() {
        val traza = Remuestreo.remuestrear(TrazasDePrueba.recta(500.0))
        val k = Curvatura.calcular(traza)
        assertTrue(k.all { abs(it) < 1e-9 })
        assertTrue(traza.x.all { abs(it) < 1e-6 })
    }

    @Test
    fun `el suavizado respeta el arco y deja el radio casi intacto`() {
        val puntos = TrazasDePrueba.enPlano(TrazasDePrueba.arcoEnMetros(radioM = 100.0, anguloGrados = 180.0))
        val k = Curvatura.calcular(Remuestreo.remuestrear(puntos))
        val central = k.max()
        assertEquals(1.0 / 100.0, central, 0.002)
    }

    @Test
    fun `curvatura positiva a la izquierda y negativa a la derecha`() {
        val izq = Curvatura.calcular(Remuestreo.remuestrear(arco(izquierda = true)))
        val der = Curvatura.calcular(Remuestreo.remuestrear(arco(izquierda = false)))
        assertTrue(izq.max() > 0.015 && izq.min() > -1e-3)
        assertTrue(der.min() < -0.015 && der.max() < 1e-3)
    }

    @Test
    fun `sin suavizado la curvatura de un arco exacto es 1 entre R`() {
        val traza = Remuestreo.remuestrear(arco(izquierda = true, radioM = 60.0), sigmaM = 0.0)
        val k = Curvatura.calcular(traza)
        val enArco = k.filter { it > 1e-3 }
        assertTrue(enArco.isNotEmpty())
        assertEquals(1.0 / 60.0, enArco.sorted()[enArco.size / 2], 0.002)
    }

    @Test
    fun `menos de dos puntos o parametros invalidos`() {
        assertEquals(0, Remuestreo.remuestrear(emptyList()).size)
        assertEquals(0, Remuestreo.remuestrear(TrazasDePrueba.recta(0.0)).size)
        val fallo = runCatching { Remuestreo.remuestrear(TrazasDePrueba.recta(100.0), pasoM = 0.0) }.exceptionOrNull()
        assertTrue(fallo is IllegalArgumentException)
    }

    private fun arco(
        izquierda: Boolean,
        radioM: Double = 50.0,
    ) = TrazasDePrueba.enPlano(TrazasDePrueba.arcoEnMetros(radioM, 90.0, izquierda))
}
