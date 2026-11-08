package app.trombito.rallycopilot.motor.notas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class GeneradorNotasTest {
    private val generador = GeneradorNotas()

    @Suppress("LongParameterList")
    private fun curva(
        inicio: Double = 0.0,
        arco: Double = 60.0,
        direccion: Direccion = Direccion.DERECHA,
        angulo: Double = 70.0,
        radio: Double = 100.0,
        entrada: Double = radio,
        salida: Double = radio,
    ) = Curva(inicio, inicio + arco, direccion, angulo, radio, entrada, salida, radio)

    private fun una(curva: Curva): Nota = generador.generar(listOf(curva)).single()

    @Test
    fun `una curva da su direccion posicion y grado`() {
        val nota = una(curva(inicio = 120.0, arco = 40.0, direccion = Direccion.IZQUIERDA, angulo = 100.0))
        assertEquals(Nota(120.0, 160.0, Direccion.IZQUIERDA, 3, radioMinM = 100.0), nota)
    }

    @Test
    fun `la nota lleva el radio minimo de la curva`() {
        val curva = Curva(0.0, 40.0, Direccion.DERECHA, 70.0, 100.0, 100.0, 100.0, 37.5)
        assertEquals(37.5, una(curva).radioMinM!!, 0.0)
    }

    @Test
    fun `el grado depende solo del angulo y no del radio`() {
        listOf(20.0, 100.0, 300.0).forEach { radio ->
            assertEquals(6, una(curva(radio = radio, angulo = 20.0)).grado)
            assertEquals(4, una(curva(radio = radio, angulo = 75.0)).grado)
            assertEquals(2, una(curva(radio = radio, angulo = 140.0)).grado)
        }
    }

    @Test
    fun `por debajo de 10 grados no hay nota y desde 10 es un seis`() {
        assertTrue(generador.generar(listOf(curva(radio = 20.0, angulo = 9.9))).isEmpty())
        assertEquals(6, una(curva(radio = 300.0, angulo = 10.0)).grado)
        assertEquals(5, una(curva(radio = 300.0, angulo = 30.1)).grado)
        assertEquals(6, una(curva(radio = 300.0, angulo = 30.0)).grado)
    }

    @Test
    fun `la velocidad de referencia ni el factor cambian el grado`() {
        val c = curva(radio = 40.0, angulo = 90.0)
        assertEquals(4, generador.generar(listOf(c)) { 50.0 }.single().grado)
        assertEquals(4, generador.generar(listOf(c)) { 150.0 }.single().grado)
        assertEquals(4, GeneradorNotas(TablaGrados(factor = 0.7)).generar(listOf(c)) { 90.0 }.single().grado)
    }

    @Test
    fun `la velocidad de referencia se consulta en la posicion de cada curva para cierra y abre`() {
        val curvas =
            listOf(
                curva(inicio = 0.0, radio = 100.0, entrada = 100.0, salida = 60.0),
                curva(inicio = 1000.0, radio = 100.0, entrada = 100.0, salida = 60.0),
            )
        // Con 150 km/h R 100 y R 60 son el mismo grado (sin salto); sin referencia son 4 y 3.
        val notas = generador.generar(curvas) { pos -> if (pos < 500.0) 150.0 else null }
        assertEquals(listOf(0.0, 1000.0), notas.map { it.posicionM })
        assertTrue(notas[0].modificadores.isEmpty())
        assertEquals(setOf(Modificador.CIERRA), notas[1].modificadores)
    }

    @Test
    fun `una curva de mas de 180 grados es horquilla sin grado ni modificadores de tendencia`() {
        val nota = una(curva(radio = 15.0, angulo = 181.0, arco = 90.0, entrada = 30.0, salida = 10.0))
        assertNull(nota.grado)
        assertTrue(nota.modificadores.isEmpty())
        assertEquals(GradoSimplificado.HORQUILLA, nota.simplificada())
    }

    @Test
    fun `180 grados todavia es un grado uno y no una horquilla`() {
        assertEquals(1, una(curva(radio = 15.0, angulo = 180.0, arco = 90.0)).grado)
        assertNull(una(curva(radio = 100.0, angulo = 180.1, arco = 90.0)).grado)
    }

    @Test
    fun `la horquilla es larga desde 100 m de arco y muy larga desde 200`() {
        fun mods(arco: Double) = una(curva(radio = 10.0, angulo = 200.0, arco = arco)).modificadores
        assertTrue(mods(99.9).isEmpty())
        assertEquals(setOf(Modificador.LARGA), mods(100.0))
        assertEquals(setOf(Modificador.LARGA), mods(199.9))
        assertEquals(setOf(Modificador.MUY_LARGA), mods(200.0))
        // La corbata de la Ma-2141 (~263 grados).
        val corbata = una(curva(radio = 10.0, angulo = 263.0, arco = 250.0, entrada = 30.0, salida = 6.0))
        assertNull(corbata.grado)
        assertEquals(setOf(Modificador.MUY_LARGA), corbata.modificadores)
        assertEquals("horquilla derecha muy larga", FrasesNota.frase(corbata))
    }

    @Test
    fun `larga desde 100 m de arco y muy larga desde 200`() {
        assertTrue(una(curva(arco = 99.9)).modificadores.isEmpty())
        assertEquals(setOf(Modificador.LARGA), una(curva(arco = 100.0)).modificadores)
        assertEquals(setOf(Modificador.LARGA), una(curva(arco = 199.0)).modificadores)
        assertEquals(setOf(Modificador.LARGA), una(curva(arco = 199.9)).modificadores)
        assertEquals(setOf(Modificador.MUY_LARGA), una(curva(arco = 200.0)).modificadores)
    }

    @Test
    fun `larga depende del arco y no del angulo`() {
        assertEquals(setOf(Modificador.MUY_LARGA), una(curva(arco = 400.0, angulo = 20.0, radio = 600.0)).modificadores)
        assertTrue(una(curva(arco = 30.0, angulo = 150.0)).modificadores.isEmpty())
        assertEquals(setOf(Modificador.MUY_LARGA), una(curva(arco = 400.0, angulo = 60.0)).modificadores)
    }

    @Test
    fun `larga se aplica tambien a los grados 5 y 6`() {
        assertEquals(setOf(Modificador.LARGA), una(curva(arco = 120.0, radio = 200.0, angulo = 40.0)).modificadores)
        assertEquals(setOf(Modificador.LARGA), una(curva(arco = 120.0, radio = 300.0, angulo = 20.0)).modificadores)
    }

    @Test
    fun `una escuadra es un tres con modificador escuadra y sin larga`() {
        val nota = una(curva(angulo = 90.0, arco = 30.0, radio = 12.0))
        assertEquals(3, nota.grado)
        assertEquals(setOf(Modificador.ESCUADRA), nota.modificadores)
        assertEquals("derecha escuadra", FrasesNota.frase(nota))
        assertEquals(GradoSimplificado.MUY_CERRADA, nota.simplificada())
    }

    @Test
    fun `los limites de la escuadra son 70 a 110 grados, 30 m de arco y 15 m de radio`() {
        fun escuadra(
            angulo: Double = 90.0,
            arco: Double = 30.0,
            radio: Double = 15.0,
        ) = Modificador.ESCUADRA in una(curva(angulo = angulo, arco = arco, radio = radio)).modificadores
        assertTrue(escuadra())
        assertTrue(escuadra(angulo = 70.0))
        assertTrue(escuadra(angulo = 110.0))
        assertFalse(escuadra(angulo = 69.9))
        assertFalse(escuadra(angulo = 110.1))
        assertFalse(escuadra(arco = 30.1))
        assertFalse(escuadra(radio = 15.1))
    }

    @Test
    fun `una curva de 90 grados con 60 m de arco es un cuatro y no una escuadra`() {
        val nota = una(curva(angulo = 90.0, arco = 60.0, radio = 12.0))
        assertEquals(4, nota.grado)
        assertTrue(nota.modificadores.isEmpty())
    }

    @Test
    fun `la escuadra no lleva larga ni cierra`() {
        val nota = una(curva(angulo = 100.0, arco = 30.0, radio = 10.0, entrada = 100.0, salida = 10.0))
        assertEquals(setOf(Modificador.ESCUADRA), nota.modificadores)
    }

    @Test
    fun `cierra con salto de grado y R de entrada al menos 1,4 veces la de salida`() {
        // R 100 es grado 4 (desde 94,7 m), R 70 es grado 3.
        assertEquals(setOf(Modificador.CIERRA), una(curva(entrada = 100.0, salida = 70.0)).modificadores)
        assertEquals(setOf(Modificador.CIERRA), una(curva(entrada = 200.0, salida = 100.0)).modificadores)
        // Salto de grado (4 a 3) pero 100 / 72 = 1,39.
        assertTrue(una(curva(entrada = 100.0, salida = 72.0)).modificadores.isEmpty())
    }

    @Test
    fun `abre con salto de grado y R de salida al menos 1,6 veces la de entrada`() {
        // R 90 es grado 3, R 144 es grado 4: 144 / 90 = 1,6.
        assertEquals(setOf(Modificador.ABRE), una(curva(entrada = 90.0, salida = 144.0)).modificadores)
        assertTrue(una(curva(entrada = 90.0, salida = 143.0)).modificadores.isEmpty())
        assertEquals(setOf(Modificador.ABRE), una(curva(entrada = 50.0, salida = 100.0)).modificadores)
    }

    @Test
    fun `cierra y abre piden un arco de al menos 40 m`() {
        assertTrue(una(curva(arco = 39.9, entrada = 100.0, salida = 50.0)).modificadores.isEmpty())
        assertEquals(setOf(Modificador.CIERRA), una(curva(arco = 40.0, entrada = 100.0, salida = 50.0)).modificadores)
        assertTrue(una(curva(arco = 39.9, entrada = 50.0, salida = 100.0)).modificadores.isEmpty())
    }

    @Test
    fun `cierra y abre solo hasta el grado 4`() {
        // Grado 5 (30 a 60 grados) con entrada y salida muy distintas.
        assertTrue(una(curva(angulo = 40.0, radio = 200.0, entrada = 200.0, salida = 100.0)).modificadores.isEmpty())
        assertTrue(una(curva(angulo = 40.0, radio = 200.0, entrada = 100.0, salida = 250.0)).modificadores.isEmpty())
        // Grado 4 si lo admite.
        val grado4 = una(curva(angulo = 70.0, radio = 100.0, entrada = 200.0, salida = 100.0))
        assertEquals(setOf(Modificador.CIERRA), grado4.modificadores)
    }

    @Test
    fun `sin cambio de grado no hay cierra ni abre aunque el radio varie mucho`() {
        // Ambos radios son grado 4 (95 a 158 m): 150 / 100 = 1,5.
        assertTrue(una(curva(radio = 130.0, entrada = 150.0, salida = 100.0)).modificadores.isEmpty())
        assertTrue(una(curva(radio = 130.0, entrada = 100.0, salida = 150.0)).modificadores.isEmpty())
    }

    @Test
    fun `larga y cierra se combinan`() {
        val nota = una(curva(arco = 100.0, entrada = 100.0, salida = 50.0))
        assertEquals(setOf(Modificador.LARGA, Modificador.CIERRA), nota.modificadores)
    }

    @Test
    fun `cierra y abre usan los grados relativos a la velocidad de referencia`() {
        // Con 150 km/h de referencia R 100 y R 60 son grados 2 y 2 (sin salto): nada. Con 130 son 3 y 2.
        val c = curva(radio = 100.0, entrada = 100.0, salida = 60.0)
        assertTrue(
            generador
                .generar(listOf(c)) { 150.0 }
                .single()
                .modificadores
                .isEmpty(),
        )
        assertEquals(setOf(Modificador.CIERRA), generador.generar(listOf(c)) { 130.0 }.single().modificadores)
    }

    @Test
    fun `se unen las notas con un hueco menor de 20 m`() {
        // R lenta: 1,0 s son menos de 20 m.
        val notas =
            generador.generar(
                listOf(
                    curva(inicio = 0.0, radio = 20.0),
                    curva(inicio = 79.9, radio = 20.0, direccion = Direccion.IZQUIERDA),
                ),
            )
        assertTrue(notas[0].unidaConSiguiente)
        assertNull(notas[0].distanciaSiguienteM)
    }

    @Test
    fun `con un hueco de 20 m en curva lenta ya no se unen`() {
        val notas = generador.generar(listOf(curva(inicio = 0.0, radio = 20.0), curva(inicio = 80.0, radio = 20.0)))
        assertFalse(notas[0].unidaConSiguiente)
        assertEquals(20, notas[0].distanciaSiguienteM)
    }

    @Test
    fun `se unen por tiempo si el hueco es menor de 1,0 s a la velocidad de la curva`() {
        // R = 100 m: v = 24,26 m/s, 1,0 s son 24,3 m.
        val unidas = generador.generar(listOf(curva(inicio = 0.0), curva(inicio = 84.0)))
        assertTrue(unidas[0].unidaConSiguiente)
        assertNull(unidas[0].distanciaSiguienteM)
        val separadas = generador.generar(listOf(curva(inicio = 0.0), curva(inicio = 85.0)))
        assertFalse(separadas[0].unidaConSiguiente)
        assertEquals(20, separadas[0].distanciaSiguienteM)
    }

    @Test
    fun `el tiempo del encadenado usa el minimo entre la velocidad de curva y la de referencia`() {
        // R = 300 m: v = 151 km/h (42 m/s, 42 m en 1,0 s); con 90 km/h de referencia son 25 m.
        val curvas =
            listOf(
                curva(inicio = 0.0, radio = 300.0, angulo = 30.0),
                curva(inicio = 90.0, radio = 300.0, angulo = 30.0),
            )
        assertTrue(generador.generar(curvas)[0].unidaConSiguiente)
        val conLimite = generador.generar(curvas) { 90.0 }
        assertFalse(conLimite[0].unidaConSiguiente)
        assertEquals(30, conLimite[0].distanciaSiguienteM)
    }

    @Test
    fun `la ultima nota no tiene distancia ni va unida`() {
        val nota = una(curva())
        assertNull(nota.distanciaSiguienteM)
        assertFalse(nota.unidaConSiguiente)
    }

    @Test
    fun `la distancia se redondea hacia abajo a la tabla`() {
        val esperado =
            mapOf(
                25.0 to 20,
                29.9 to 20,
                30.0 to 30,
                39.9 to 30,
                40.0 to 40,
                69.9 to 60,
                70.0 to 70,
                85.0 to 80,
                99.9 to 80,
                100.0 to 100,
                119.9 to 100,
                120.0 to 120,
                199.0 to 150,
                260.0 to 250,
                399.0 to 300,
                450.0 to 400,
                500.0 to 500,
                599.0 to 500,
                600.0 to 600,
                700.0 to 700,
                799.0 to 700,
                800.0 to 800,
                2000.0 to 800,
            )
        esperado.forEach { (hueco, distancia) ->
            val notas =
                generador.generar(
                    listOf(curva(inicio = 0.0, radio = 20.0), curva(inicio = 60.0 + hueco, radio = 20.0)),
                )
            assertEquals("hueco $hueco", distancia, notas[0].distanciaSiguienteM)
            assertFalse("hueco $hueco", notas[0].unidaConSiguiente)
        }
    }

    @Test
    fun `una curva omitida no cuenta para el encadenado y el hueco se mide a la siguiente nota`() {
        val curvas =
            listOf(
                curva(inicio = 0.0, radio = 20.0),
                curva(inicio = 100.0, radio = 300.0, angulo = 5.0),
                curva(inicio = 260.0, radio = 20.0),
            )
        val notas = generador.generar(curvas)
        assertEquals(2, notas.size)
        assertEquals(200, notas[0].distanciaSiguienteM)
    }

    private fun notasDeTraza(metros: List<Pair<Double, Double>>): List<Nota> =
        generador.generar(DetectorCurvas().detectar(TrazasDePrueba.enPlano(metros)))

    /** [segundo] (al norte desde el origen) girado [grados] a la izquierda y pegado al final de [primero]. */
    private fun encadenar(
        primero: List<Pair<Double, Double>>,
        segundo: List<Pair<Double, Double>>,
        grados: Double,
    ): List<Pair<Double, Double>> {
        val (x0, y0) = primero.last()
        val g = Math.toRadians(grados)
        return primero +
            segundo.drop(1).map { (x, y) -> (x0 + x * cos(g) - y * sin(g)) to (y0 + x * sin(g) + y * cos(g)) }
    }

    @Test
    fun `una curva abierta de 15 grados y R 600 m canta seis izquierda`() {
        val nota = notasDeTraza(TrazasDePrueba.arcoEnMetros(600.0, 15.0)).single()
        assertEquals(6, nota.grado)
        assertEquals(Direccion.IZQUIERDA, nota.direccion)
    }

    @Test
    fun `una compuesta de 40 y 40 grados con 60 m de recta son dos cincos`() {
        val primero = TrazasDePrueba.arcoEnMetros(100.0, 40.0, entradaM = 150.0, salidaM = 60.0)
        val segundo = TrazasDePrueba.arcoEnMetros(100.0, 40.0, entradaM = 0.0, salidaM = 150.0)
        val notas = notasDeTraza(encadenar(primero, segundo, 40.0))
        assertEquals(listOf(5, 5), notas.map { it.grado })
        assertTrue(notas.all { it.direccion == Direccion.IZQUIERDA })
    }

    @Test
    fun `una S izquierda derecha da dos notas de sentido contrario`() {
        val primero = TrazasDePrueba.arcoEnMetros(120.0, 40.0, izquierda = true, entradaM = 150.0, salidaM = 0.0)
        val segundo = TrazasDePrueba.arcoEnMetros(120.0, 40.0, izquierda = false, entradaM = 0.0, salidaM = 150.0)
        val notas = notasDeTraza(encadenar(primero, segundo, 40.0))
        assertEquals(listOf(Direccion.IZQUIERDA, Direccion.DERECHA), notas.map { it.direccion })
    }

    @Test
    fun `una recta con ruido de 2 grados de rumbo no canta nada`() {
        val azar = Random(5)
        val puntos = mutableListOf(0.0 to 0.0)
        repeat(150) {
            val rumbo = Math.toRadians(azar.nextDouble(-2.0, 2.0))
            puntos += (puntos.last().first + 10.0 * sin(rumbo)) to (puntos.last().second + 10.0 * cos(rumbo))
        }
        assertTrue(notasDeTraza(puntos).isEmpty())
    }

    @Test
    fun `una curva muy abierta no se une a la siguiente por una velocidad de curva enorme`() {
        val curvas =
            listOf(
                curva(inicio = 0.0, radio = 3000.0, angulo = 15.0),
                curva(inicio = 160.0, radio = 3000.0, angulo = 15.0),
            )
        val primera = generador.generar(curvas).first()
        assertFalse(primera.unidaConSiguiente)
        assertEquals(100, primera.distanciaSiguienteM)
    }

    @Test
    fun `la tabla calibrada no cambia el grado de una curva`() {
        val nota = GeneradorNotas(TablaGrados(factor = 0.5)).generar(listOf(curva(angulo = 75.0))).single()
        assertEquals(4, nota.grado)
    }
}
