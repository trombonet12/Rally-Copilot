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

    @Test
    fun `una curva abierta de 15 grados y R 600 m es una curva aunque no baje de R 400`() {
        val curvas = detector.detectar(TrazasDePrueba.enPlano(TrazasDePrueba.arcoEnMetros(600.0, 15.0)))
        assertEquals(1, curvas.size)
        val c = curvas.single()
        assertEquals(Direccion.IZQUIERDA, c.direccion)
        assertEquals(15.0, c.anguloGrados, 3.0)
        assertEquals(600.0, c.radioCentralM, 120.0)
    }

    @Test
    fun `una curva muy abierta de 12 grados y R 1500 m tambien`() {
        val curvas =
            detector.detectar(
                TrazasDePrueba.enPlano(TrazasDePrueba.arcoEnMetros(1500.0, 12.0, izquierda = false)),
            )
        assertEquals(1, curvas.size)
        assertEquals(Direccion.DERECHA, curvas.single().direccion)
        assertEquals(12.0, curvas.single().anguloGrados, 3.0)
    }

    @Test
    fun `una compuesta de 40 y 40 grados con 60 m de recta en medio son dos curvas`() {
        val curvas =
            detector.detectar(
                TrazasDePrueba.enPlano(dosArcos(radioM = 100.0, anguloGrados = 40.0, huecoM = 60.0)),
            )
        assertEquals(2, curvas.size)
        assertTrue(curvas.all { it.direccion == Direccion.IZQUIERDA })
        curvas.forEach { assertEquals(40.0, it.anguloGrados, 5.0) }
        assertTrue(curvas[0].finM < curvas[1].inicioM)
    }

    @Test
    fun `una S izquierda derecha sin recta en medio son dos curvas de signo contrario`() {
        val primero = TrazasDePrueba.arcoEnMetros(120.0, 40.0, izquierda = true, entradaM = 150.0, salidaM = 0.0)
        val segundo = TrazasDePrueba.arcoEnMetros(120.0, 40.0, izquierda = false, entradaM = 0.0, salidaM = 150.0)
        val (x0, y0) = primero.last()
        val curvas =
            detector.detectar(
                TrazasDePrueba.enPlano(primero + segundo.drop(1).map { (x, y) -> girar(x, y, x0, y0, 40.0) }),
            )
        assertEquals(2, curvas.size)
        assertEquals(Direccion.IZQUIERDA, curvas[0].direccion)
        assertEquals(Direccion.DERECHA, curvas[1].direccion)
        curvas.forEach { assertEquals(40.0, it.anguloGrados, 6.0) }
    }

    @Test
    fun `una recta con ruido de mas o menos 2 grados de rumbo no tiene curvas`() {
        assertTrue(detector.detectar(TrazasDePrueba.enPlano(rectaConRuidoDeRumbo(2.0, 1500.0, 7))).isEmpty())
        assertTrue(
            detector.detectar(TrazasDePrueba.enPlano(rectaConRuidoDeRumbo(2.0, 1500.0, 11, pasoM = 25.0))).isEmpty(),
        )
    }

    @Test
    fun `una recta en zigzag de 3 grados por vertice cada 20 m no tiene curvas`() {
        val zigzag = mutableListOf(0.0 to 0.0)
        for (i in 1..80) {
            val rumbo = Math.toRadians(if (i % 2 == 0) 3.0 else -3.0)
            zigzag += (zigzag.last().first + 20.0 * sin(rumbo)) to (zigzag.last().second + 20.0 * cos(rumbo))
        }
        assertTrue(detector.detectar(TrazasDePrueba.enPlano(zigzag)).isEmpty())
    }

    /** Recta de [largoM] m al norte con el rumbo de cada tramo de [pasoM] m desviado al azar ±[maxGrados]. */
    private fun rectaConRuidoDeRumbo(
        maxGrados: Double,
        largoM: Double,
        semilla: Int,
        pasoM: Double = 10.0,
    ): List<Pair<Double, Double>> {
        val azar = Random(semilla)
        val puntos = mutableListOf(0.0 to 0.0)
        repeat((largoM / pasoM).toInt()) {
            val rumbo = Math.toRadians(azar.nextDouble(-maxGrados, maxGrados))
            puntos += (puntos.last().first + pasoM * sin(rumbo)) to (puntos.last().second + pasoM * cos(rumbo))
        }
        return puntos
    }

    /** Dos arcos de [radioM] y [anguloGrados] a la izquierda, separados por [huecoM] metros de recta. */
    private fun dosArcos(
        huecoM: Double,
        radioM: Double = 60.0,
        anguloGrados: Double = 60.0,
    ): List<Pair<Double, Double>> {
        val primero = TrazasDePrueba.arcoEnMetros(radioM, anguloGrados, entradaM = 150.0, salidaM = huecoM)
        val segundo = TrazasDePrueba.arcoEnMetros(radioM, anguloGrados, entradaM = 0.0, salidaM = 150.0)
        val (x0, y0) = primero.last()
        return primero + segundo.drop(1).map { (x, y) -> girar(x, y, x0, y0, anguloGrados) }
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
