package app.trombito.rallycopilot.motor.ruta

import app.trombito.rallycopilot.motor.geo.ProyeccionPlana
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparadorRutasTest {
    private val plano = ProyeccionPlana(PuntoGeo(39.6, 2.9))

    private fun ruta(vararg xy: Pair<Double, Double>): Ruta {
        val puntos = xy.map { plano.aPunto(it.first, it.second) }
        return Ruta(puntos, emptyList(), puntos.longitudMetros(), puntos.longitudMetros() / 15.0)
    }

    private fun recta(
        largoM: Double,
        pasoM: Double,
        desviacionM: Double = 0.0,
    ): Ruta {
        val xs = generateSequence(0.0) { it + pasoM }.takeWhile { it <= largoM }
        return ruta(*xs.map { it to desviacionM }.toList().toTypedArray())
    }

    @Test
    fun `la misma ruta es igual a si misma y a una copia`() {
        val a = recta(10_000.0, 100.0)
        assertTrue(ComparadorRutas.sonIguales(a, a))
        assertTrue(ComparadorRutas.sonIguales(a, a.copy()))
    }

    @Test
    fun `la misma geometria con otra densidad de puntos es igual`() {
        assertTrue(ComparadorRutas.sonIguales(recta(10_000.0, 100.0), recta(10_000.0, 25.0)))
    }

    @Test
    fun `una ruta desplazada 10 m es igual y desplazada 60 m no`() {
        assertTrue(ComparadorRutas.sonIguales(recta(10_000.0, 100.0), recta(10_000.0, 100.0, desviacionM = 10.0)))
        assertFalse(ComparadorRutas.sonIguales(recta(10_000.0, 100.0), recta(10_000.0, 100.0, desviacionM = 60.0)))
    }

    @Test
    fun `una ruta con casi la misma longitud pero un rodeo de 200 m no es igual`() {
        val rodeo = ruta(0.0 to 0.0, 5_000.0 to 200.0, 10_000.0 to 0.0)
        assertFalse(ComparadorRutas.sonIguales(recta(10_000.0, 100.0), rodeo))
        assertFalse(ComparadorRutas.sonIguales(rodeo, recta(10_000.0, 100.0)))
    }

    @Test
    fun `una ruta que ocupa el mismo pasillo pero mide mas de un 1 por ciento mas no es igual`() {
        val zigzag =
            ruta(*(0..500).map { (it * 20.0) to (if (it % 2 == 0) -10.0 else 10.0) }.toTypedArray())
        assertFalse(ComparadorRutas.sonIguales(recta(10_000.0, 100.0), zigzag))
    }

    @Test
    fun `un trozo de la ruta no es igual a la ruta entera`() {
        assertFalse(ComparadorRutas.sonIguales(recta(10_000.0, 100.0), recta(6_000.0, 100.0)))
    }

    @Test
    fun `rutas en otro lugar no son iguales`() {
        val lejos = ruta(0.0 to 5_000.0, 10_000.0 to 5_000.0)
        assertFalse(ComparadorRutas.sonIguales(recta(10_000.0, 100.0), lejos))
    }

    @Test
    fun `una ruta de sentido contrario con la misma geometria es igual`() {
        val a = recta(10_000.0, 100.0)
        assertTrue(ComparadorRutas.sonIguales(a, a.copy(puntos = a.puntos.reversed())))
    }

    @Test
    fun `rutas sin puntos suficientes solo son iguales si son identicas`() {
        val vacia = Ruta(emptyList(), emptyList(), 0.0, 0.0)
        val unPunto = Ruta(listOf(PuntoGeo(39.6, 2.9)), emptyList(), 0.0, 0.0)
        assertTrue(ComparadorRutas.sonIguales(vacia, vacia))
        assertTrue(ComparadorRutas.sonIguales(unPunto, unPunto.copy()))
        assertFalse(ComparadorRutas.sonIguales(vacia, unPunto))
        assertFalse(ComparadorRutas.sonIguales(unPunto, recta(1_000.0, 100.0)))
    }

    @Test
    fun `una ruta larga con rutas casi iguales se compara sin tardar`() {
        val larga = recta(120_000.0, 20.0)
        val casi = recta(120_000.0, 35.0, desviacionM = 5.0)
        val inicio = System.nanoTime()
        assertTrue(ComparadorRutas.sonIguales(larga, casi))
        assertTrue("tardó ${(System.nanoTime() - inicio) / 1_000_000} ms", System.nanoTime() - inicio < 2_000_000_000L)
    }
}
