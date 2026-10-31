package app.trombito.rallycopilot.motor.notas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneradorNotasTest {
    private val generador = GeneradorNotas()

    @Suppress("LongParameterList")
    private fun curva(
        inicio: Double = 0.0,
        arco: Double = 60.0,
        direccion: Direccion = Direccion.DERECHA,
        angulo: Double = 60.0,
        radio: Double = 100.0,
        entrada: Double = radio,
        salida: Double = radio,
    ) = Curva(inicio, inicio + arco, direccion, angulo, radio, entrada, salida, radio)

    private fun una(curva: Curva): Nota = generador.generar(listOf(curva)).single()

    @Test
    fun `una curva da su direccion posicion y grado`() {
        val nota = una(curva(inicio = 120.0, arco = 40.0, direccion = Direccion.IZQUIERDA, radio = 70.0))
        assertEquals(Nota(120.0, 160.0, Direccion.IZQUIERDA, 3), nota)
    }

    @Test
    fun `el grado 6 con menos de 20 grados se omite y con 20 o mas se canta`() {
        assertTrue(generador.generar(listOf(curva(radio = 300.0, angulo = 19.9))).isEmpty())
        assertEquals(6, una(curva(radio = 300.0, angulo = 20.0)).grado)
        assertEquals(5, una(curva(radio = 200.0, angulo = 5.0)).grado)
    }

    @Test
    fun `la velocidad de referencia se consulta en la posicion de cada curva`() {
        val curvas = listOf(curva(inicio = 0.0, angulo = 10.0), curva(inicio = 1000.0, angulo = 10.0))
        // R = 100 m: v = 87,3 km/h. A 80 km/h es seis pero no llega al radio de confort (144 m): cinco.
        val notas = generador.generar(curvas) { pos -> if (pos < 500.0) 80.0 else null }
        assertEquals(listOf(0.0, 1000.0), notas.map { it.posicionM })
        assertEquals(listOf(5, 4), notas.map { it.grado })
    }

    @Test
    fun `a 50 km por hora una curva de 40 m no es seis y con factor 0,7 una de 60 m no es uno`() {
        val lenta = curva(radio = 40.0, angulo = 90.0)
        assertEquals(5, generador.generar(listOf(lenta)) { 50.0 }.single().grado)
        val calibrado = GeneradorNotas(TablaGrados(factor = 0.7))
        val nota = calibrado.generar(listOf(curva(radio = 60.0, angulo = 90.0))) { 90.0 }.single()
        assertEquals(2, nota.grado)
    }

    @Test
    fun `con referencia el seis se omite si gira menos de 20 grados`() {
        val curvas = listOf(curva(radio = 300.0, angulo = 19.0))
        assertTrue(generador.generar(curvas) { 90.0 }.isEmpty())
        assertEquals(5, generador.generar(listOf(curva(radio = 150.0, angulo = 19.0))) { 90.0 }.single().grado)
    }

    @Test
    fun `una curva con 140 grados y radio bajo 30 m es horquilla sin grado ni modificadores`() {
        val nota = una(curva(radio = 15.0, angulo = 180.0, arco = 90.0, entrada = 30.0, salida = 10.0))
        assertNull(nota.grado)
        assertTrue(nota.modificadores.isEmpty())
        assertEquals(GradoSimplificado.HORQUILLA, nota.simplificada())
    }

    @Test
    fun `la horquilla es larga desde 200 grados y muy larga desde 240`() {
        fun mods(angulo: Double) = una(curva(radio = 10.0, angulo = angulo, arco = 90.0)).modificadores
        assertTrue(mods(199.9).isEmpty())
        assertEquals(setOf(Modificador.LARGA), mods(200.0))
        assertEquals(setOf(Modificador.LARGA), mods(239.9))
        assertEquals(setOf(Modificador.MUY_LARGA), mods(240.0))
        // La corbata de la Ma-2141 (~263 grados).
        val corbata = una(curva(radio = 10.0, angulo = 263.0, arco = 90.0, entrada = 30.0, salida = 6.0))
        assertNull(corbata.grado)
        assertEquals(setOf(Modificador.MUY_LARGA), corbata.modificadores)
        assertEquals("horquilla derecha muy larga", FrasesNota.frase(corbata))
    }

    @Test
    fun `una curva cerrada con poco angulo no es horquilla`() {
        assertEquals(1, una(curva(radio = 15.0, angulo = 90.0)).grado)
    }

    @Test
    fun `larga desde 100 grados de angulo y muy larga desde 160`() {
        assertTrue(una(curva(angulo = 99.9)).modificadores.isEmpty())
        assertEquals(setOf(Modificador.LARGA), una(curva(angulo = 100.0)).modificadores)
        assertEquals(setOf(Modificador.LARGA), una(curva(angulo = 159.9)).modificadores)
        assertEquals(setOf(Modificador.MUY_LARGA), una(curva(angulo = 160.0)).modificadores)
    }

    @Test
    fun `larga depende del angulo y no del arco`() {
        assertTrue(una(curva(arco = 400.0, angulo = 60.0)).modificadores.isEmpty())
        assertEquals(setOf(Modificador.LARGA), una(curva(arco = 30.0, angulo = 100.0)).modificadores)
    }

    @Test
    fun `larga se aplica tambien a los grados 5 y 6`() {
        assertEquals(setOf(Modificador.LARGA), una(curva(radio = 200.0, angulo = 120.0)).modificadores)
        assertEquals(setOf(Modificador.LARGA), una(curva(radio = 300.0, angulo = 120.0)).modificadores)
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
        // Grado 5 (R central 200 m) con entrada y salida muy distintas.
        assertTrue(una(curva(radio = 200.0, entrada = 200.0, salida = 100.0)).modificadores.isEmpty())
        assertTrue(una(curva(radio = 200.0, entrada = 100.0, salida = 250.0)).modificadores.isEmpty())
        // Grado 4 si lo admite.
        val grado4 = una(curva(radio = 100.0, entrada = 200.0, salida = 100.0))
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
        val nota = una(curva(angulo = 100.0, entrada = 100.0, salida = 50.0))
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

    @Test
    fun `la tabla calibrada cambia los grados`() {
        val nota = GeneradorNotas(TablaGrados(factor = 0.5)).generar(listOf(curva(radio = 100.0))).single()
        assertEquals(1, nota.grado)
    }
}
