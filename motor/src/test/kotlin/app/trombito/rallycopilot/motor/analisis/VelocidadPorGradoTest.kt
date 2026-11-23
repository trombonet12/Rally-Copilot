package app.trombito.rallycopilot.motor.analisis

import app.trombito.rallycopilot.motor.notas.TrazasDePrueba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VelocidadPorGradoTest {
    private val rectaKmh = 60.0

    /**
     * Pone tiempo a una traza de [metros] (un punto cada 5 m): [kmhEn] da la velocidad en cada punto según los metros
     * recorridos desde el inicio. Devuelve los puntos con su reloj (empieza en [t0Ms]).
     */
    private fun conTiempo(
        metros: List<Pair<Double, Double>>,
        t0Ms: Long = 0L,
        kmhEn: (Double) -> Double,
    ): List<PuntoTiempo> {
        val geo = TrazasDePrueba.enPlano(metros)
        var t = t0Ms.toDouble()
        var s = 0.0
        return geo.mapIndexed { i, p ->
            if (i > 0) {
                val d = geo[i - 1].distanciaA(p)
                s += d
                t += d / (kmhEn(s) / 3.6) * 1_000.0
            }
            PuntoTiempo(p, Math.round(t))
        }
    }

    /** Recta de 200 m, curva de [angulo] grados y recta de 200 m a [rectaKmh], con [kmhCurva] en la curva. */
    private fun curva(
        angulo: Double,
        radioM: Double,
        kmhCurva: Double,
        t0Ms: Long = 0L,
    ): List<PuntoTiempo> {
        val metros = TrazasDePrueba.arcoEnMetros(radioM, angulo)
        val arco = Math.toRadians(angulo) * radioM
        return conTiempo(metros, t0Ms) { s -> if (s in 190.0..(210.0 + arco)) kmhCurva else rectaKmh }
    }

    @Test
    fun `sin curvas no hay nada`() {
        val recta = conTiempo(TrazasDePrueba.enPlano(emptyList()).let { (0..100).map { 0.0 to it * 5.0 } }) { rectaKmh }
        assertEquals(VelocidadPorGrado.VACIO, VelocidadPorGrado.analizar(recta))
        assertEquals(VelocidadPorGrado.VACIO, VelocidadPorGrado.analizar(emptyList()))
    }

    @Test
    fun `una curva de grado 5 mide su velocidad de paso`() {
        val medida = VelocidadPorGrado.analizar(curva(angulo = 45.0, radioM = 120.0, kmhCurva = 50.0))
        assertEquals(1, medida.curvas)
        assertEquals(1, medida.deGrado(5).curvas)
        assertEquals(50.0, medida.deGrado(5).velMediaKmh!!, 2.0)
    }

    @Test
    fun `cada angulo cae en su grado`() {
        val casos = listOf(75.0 to 4, 105.0 to 3, 135.0 to 2, 165.0 to 1)
        for ((angulo, grado) in casos) {
            val medida = VelocidadPorGrado.analizar(curva(angulo, radioM = 60.0, kmhCurva = 40.0))
            assertEquals("ángulo $angulo", 1, medida.deGrado(grado).curvas)
            assertEquals("ángulo $angulo", 1, medida.curvas)
        }
    }

    @Test
    fun `una horquilla va aparte de los grados`() {
        val medida = VelocidadPorGrado.analizar(curva(angulo = 200.0, radioM = 25.0, kmhCurva = 25.0))
        assertEquals(1, medida.horquillas.curvas)
        assertEquals(25.0, medida.horquillas.velMediaKmh!!, 2.0)
        assertEquals(0, medida.porGrado.sumOf { it.curvas })
    }

    @Test
    fun `la velocidad es la minima dentro de la curva y no la de la recta`() {
        val medida = VelocidadPorGrado.analizar(curva(angulo = 75.0, radioM = 80.0, kmhCurva = 35.0))
        assertEquals(35.0, medida.deGrado(4).velMediaKmh!!, 2.0)
    }

    @Test
    fun `una curva por debajo de 10 kmh se descarta`() {
        val lenta = VelocidadPorGrado.analizar(curva(angulo = 75.0, radioM = 80.0, kmhCurva = 8.0))
        assertEquals(VelocidadPorGrado.VACIO, lenta)
    }

    @Test
    fun `una parada dentro de la curva la descarta`() {
        val base = curva(angulo = 75.0, radioM = 80.0, kmhCurva = 40.0)
        val i = base.size / 2
        val parado = (1..20).map { PuntoTiempo(base[i].punto, base[i].tiempoMs + it * 1_000L) }
        val desplazados = base.drop(i + 1).map { it.copy(tiempoMs = it.tiempoMs + 20_000L) }
        val medida = VelocidadPorGrado.analizar(base.take(i + 1) + parado + desplazados)
        assertEquals(0, medida.curvas)
    }

    @Test
    fun `un hueco de GPS dentro de la curva la descarta`() {
        val base = curva(angulo = 75.0, radioM = 80.0, kmhCurva = 40.0)
        val i = base.size / 2
        // 8 s sin fijaciones en mitad de la curva (menos de 250 m: no es un salto de posición).
        val conHueco = base.take(i) + base.drop(i).map { it.copy(tiempoMs = it.tiempoMs + 8_000L) }
        assertEquals(0, VelocidadPorGrado.analizar(conHueco).curvas)
    }

    @Test
    fun `un pico aislado de ruido del GPS no hunde la velocidad`() {
        val base = curva(angulo = 75.0, radioM = 80.0, kmhCurva = 40.0)
        val i = base.size / 2
        // Un solo punto con la velocidad del GPS casi parada: la mediana de tres pasos la absorbe.
        val ruido =
            base.mapIndexed { k, p -> p.copy(velocidadMs = if (k == i) 0.3 else p.velocidadMs ?: (40.0 / 3.6)) }
        val medida = VelocidadPorGrado.analizar(ruido)
        assertEquals(1, medida.curvas)
        assertTrue(medida.deGrado(4).velMediaKmh!! > 30.0)
    }

    @Test
    fun `la velocidad del GPS manda sobre la deducida`() {
        val base = curva(angulo = 45.0, radioM = 120.0, kmhCurva = 50.0)
        val conGps = base.map { it.copy(velocidadMs = 30.0 / 3.6) }
        assertEquals(30.0, VelocidadPorGrado.analizar(conGps).deGrado(5).velMediaKmh!!, 0.5)
    }

    @Test
    fun `una escuadra no cuenta como grado 3`() {
        val metros = TrazasDePrueba.arcoEnMetros(radioM = 12.0, anguloGrados = 90.0, pasoM = 2.0)
        val traza = conTiempo(metros) { 20.0 }
        val medida = VelocidadPorGrado.analizar(traza)
        assertEquals(0, medida.deGrado(3).curvas)
    }

    @Test
    fun `un salto de GPS parte la traza y no inventa curvas`() {
        val a = curva(angulo = 45.0, radioM = 120.0, kmhCurva = 50.0)
        val b = curva(angulo = 45.0, radioM = 120.0, kmhCurva = 50.0, t0Ms = a.last().tiempoMs + 1_000L)
        // La segunda copia empieza 5 km al norte (lat + 0,05): salto > 250 m.
        val lejos = b.map { it.copy(punto = it.punto.copy(lat = it.punto.lat + 0.05)) }
        val medida = VelocidadPorGrado.analizar(a + lejos)
        assertEquals(2, medida.deGrado(5).curvas)
    }

    @Test
    fun `combinar suma cuentas y sumas y la media sale de las dos`() {
        val a = VelocidadPorGrado.analizar(curva(45.0, 120.0, kmhCurva = 40.0))
        val b = VelocidadPorGrado.analizar(curva(45.0, 120.0, kmhCurva = 60.0))
        val juntas = VelocidadPorGrado.combinar(listOf(a, b))
        assertEquals(2, juntas.deGrado(5).curvas)
        assertEquals((a.deGrado(5).sumaKmh + b.deGrado(5).sumaKmh), juntas.deGrado(5).sumaKmh, 1e-9)
        assertEquals(50.0, juntas.deGrado(5).velMediaKmh!!, 3.0)
        assertEquals(juntas, a.mas(b))
        assertEquals(juntas, a + b)
        assertEquals(juntas, b + a)
    }

    @Test
    fun `combinar nada es vacio y vacio no cambia nada`() {
        val a = VelocidadPorGrado.analizar(curva(45.0, 120.0, kmhCurva = 40.0))
        assertEquals(VelocidadPorGrado.VACIO, VelocidadPorGrado.combinar(emptyList()))
        assertEquals(a, a.mas(VelocidadPorGrado.VACIO))
        assertNull(VelocidadPorGrado.VACIO.deGrado(1).velMediaKmh)
        assertNotEquals(a, VelocidadPorGrado.VACIO)
    }

    @Test
    fun `es determinista`() {
        val traza = curva(75.0, 80.0, kmhCurva = 35.0)
        assertEquals(VelocidadPorGrado.analizar(traza), VelocidadPorGrado.analizar(traza))
    }
}
