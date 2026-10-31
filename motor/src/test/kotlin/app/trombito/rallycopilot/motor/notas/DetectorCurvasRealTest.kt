package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class DetectorCurvasRealTest {
    private val detector = DetectorCurvas()
    private val ma2141 by lazy { TrazasDePrueba.cargarGpx("ma-2141-sa-calobra.gpx") }

    @Test
    fun `la Ma-2141 se lee entera`() {
        assertEquals(1116, ma2141.size)
    }

    @Test
    fun `Ma-2141 tiene entre 60 y 200 curvas`() {
        val curvas = detector.detectar(ma2141)
        println("Ma-2141: ${curvas.size} curvas")
        println("Radio central (m): " + distribucion(curvas.map { it.radioCentralM }))
        assertTrue("${curvas.size} curvas", curvas.size in 60..200)
        assertTrue(curvas.zipWithNext().all { (a, b) -> a.finM <= b.inicioM })
    }

    @Test
    fun `el lazo del Nus de sa Corbata sale como una curva de al menos 230 grados`() {
        val curvas = detector.detectar(ma2141)
        val corbata = curvas.filter { it.inicioM < 3500.0 && it.finM > 2900.0 }.maxByOrNull { it.anguloGrados }
        assertNotNull("no hay curva hacia los 3,2 km", corbata)
        println("Corbata: $corbata")
        assertTrue("angulo ${corbata!!.anguloGrados}", corbata.anguloGrados >= 230.0)
    }

    @Test
    fun `15 km se procesan en menos de un segundo tras calentar`() {
        val sinuosa = carreteraSinuosa(15_000.0)
        repeat(20) { detector.detectar(sinuosa) }
        val inicio = System.nanoTime()
        val curvas = detector.detectar(sinuosa)
        val ms = (System.nanoTime() - inicio) / 1e6
        println("15 km: ${curvas.size} curvas en $ms ms")
        assertTrue(curvas.isNotEmpty())
        assertTrue("$ms ms", ms < 1000.0)
    }

    /** Carretera de [largoM] metros que serpentea con un punto cada 5 m. */
    private fun carreteraSinuosa(largoM: Double): List<PuntoGeo> =
        TrazasDePrueba.enPlano(
            (0..(largoM / 5.0).toInt()).map { i ->
                val s = i * 5.0
                (150.0 * sin(s / 120.0)) to s
            },
        )

    private fun distribucion(radios: List<Double>): String {
        val tramos = listOf(0.0, 27.0, 55.0, 95.0, 160.0, 260.0, 400.0, Double.MAX_VALUE)
        return tramos
            .zipWithNext()
            .joinToString(", ") { (a, b) -> "[$a,$b): ${radios.count { it >= a && it < b }}" } +
            " | mediana ${radios.sorted()[radios.size / 2].toInt()}"
    }
}
