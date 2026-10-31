package app.trombito.rallycopilot.motor.notas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class DetectorCurvasTest {
    private val detector = DetectorCurvas()

    @Test
    fun `un arco de R 50 y 90 grados es una curva a la izquierda`() {
        val curvas = detector.detectar(TrazasDePrueba.enPlano(TrazasDePrueba.arcoEnMetros(50.0, 90.0)))
        assertEquals(1, curvas.size)
        val c = curvas.single()
        assertEquals(Direccion.IZQUIERDA, c.direccion)
        assertEquals(90.0, c.anguloGrados, 5.0)
        assertEquals(50.0, c.radioCentralM, 5.0)
        assertTrue("inicio ${c.inicioM}", c.inicioM in 150.0..215.0)
        assertTrue(c.radioMinM <= c.radioCentralM + 1e-9)
    }

    @Test
    fun `el mismo arco a la derecha sale a la derecha`() {
        val derecha = TrazasDePrueba.arcoEnMetros(50.0, 90.0, izquierda = false)
        val curvas = detector.detectar(TrazasDePrueba.enPlano(derecha))
        assertEquals(1, curvas.size)
        assertEquals(Direccion.DERECHA, curvas.single().direccion)
        assertEquals(90.0, curvas.single().anguloGrados, 5.0)
    }

    @Test
    fun `una curva en S da dos curvas de signo contrario`() {
        // Arco a la izquierda, 100 m de recta y arco a la derecha, en una sola polilínea continua.
        val primero = TrazasDePrueba.arcoEnMetros(60.0, 80.0, izquierda = true, entradaM = 150.0, salidaM = 100.0)
        val segundo = TrazasDePrueba.arcoEnMetros(60.0, 80.0, izquierda = false, entradaM = 0.0, salidaM = 150.0)
        val (x0, y0) = primero.last()
        // El segundo arco, girado 80 grados a la izquierda y trasladado al final del primero.
        val girado = segundo.drop(1).map { (x, y) -> girar(x, y, x0, y0, 80.0) }
        val curvas = detector.detectar(TrazasDePrueba.enPlano(primero + girado))
        assertEquals(2, curvas.size)
        assertEquals(Direccion.IZQUIERDA, curvas[0].direccion)
        assertEquals(Direccion.DERECHA, curvas[1].direccion)
        assertTrue(curvas[0].finM < curvas[1].inicioM)
    }

    @Test
    fun `una recta de 1 km no tiene curvas`() {
        assertTrue(detector.detectar(TrazasDePrueba.recta(1000.0)).isEmpty())
    }

    @Test
    fun `el ruido determinista de 1 m no rompe ni inventa curvas`() {
        val azar = Random(2026)
        val ruidoso =
            TrazasDePrueba.arcoEnMetros(50.0, 90.0, pasoM = 10.0).map { (x, y) ->
                (x + azar.nextDouble(-1.0, 1.0)) to (y + azar.nextDouble(-1.0, 1.0))
            }
        val curvas = detector.detectar(TrazasDePrueba.enPlano(ruidoso))
        assertEquals(1, curvas.size)
        assertEquals(90.0, curvas.single().anguloGrados, 10.0)
    }

    @Test
    fun `dos curvas del mismo sentido muy juntas se fusionan y separadas no`() {
        val cerca = detector.detectar(TrazasDePrueba.enPlano(dosArcos(huecoM = 10.0)))
        assertEquals(1, cerca.size)
        val lejos = detector.detectar(TrazasDePrueba.enPlano(dosArcos(huecoM = 120.0)))
        assertEquals(2, lejos.size)
        assertTrue(lejos.all { it.direccion == Direccion.IZQUIERDA })
    }

    @Test
    fun `es determinista`() {
        val puntos = TrazasDePrueba.enPlano(TrazasDePrueba.arcoEnMetros(70.0, 120.0))
        assertEquals(detector.detectar(puntos), detector.detectar(puntos))
    }

    /** Dos arcos de 60 m de radio y 60 grados a la izquierda, separados por [huecoM] metros de recta. */
    private fun dosArcos(huecoM: Double): List<Pair<Double, Double>> {
        val primero = TrazasDePrueba.arcoEnMetros(60.0, 60.0, entradaM = 150.0, salidaM = huecoM)
        val segundo = TrazasDePrueba.arcoEnMetros(60.0, 60.0, entradaM = 0.0, salidaM = 150.0)
        val (x0, y0) = primero.last()
        return primero + segundo.drop(1).map { (x, y) -> girar(x, y, x0, y0, 60.0) }
    }

    /** Gira (x, y) [grados] a la izquierda y lo traslada a (x0, y0). */
    private fun girar(
        x: Double,
        y: Double,
        x0: Double,
        y0: Double,
        grados: Double,
    ): Pair<Double, Double> {
        val g = Math.toRadians(grados)
        return (x0 + x * cos(g) - y * sin(g)) to (y0 + x * sin(g) + y * cos(g))
    }
}
