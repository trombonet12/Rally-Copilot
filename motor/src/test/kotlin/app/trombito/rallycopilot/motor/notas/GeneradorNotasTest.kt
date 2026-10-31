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
        val notas = generador.generar(curvas) { pos -> if (pos < 500.0) 80.0 else null }
        assertEquals(listOf(1000.0), notas.map { it.posicionM })
        assertEquals(4, notas.single().grado)
    }

    @Test
    fun `una curva con 140 grados y radio bajo 30 m es horquilla sin grado ni modificadores`() {
        val nota = una(curva(radio = 15.0, angulo = 180.0, arco = 90.0, entrada = 30.0, salida = 10.0))
        assertNull(nota.grado)
        assertTrue(nota.modificadores.isEmpty())
        assertEquals(GradoSimplificado.HORQUILLA, nota.simplificada())
    }

    @Test
    fun `una curva cerrada con poco angulo no es horquilla`() {
        assertEquals(1, una(curva(radio = 15.0, angulo = 90.0)).grado)
    }

    @Test
    fun `larga desde 80 m de arco con grado hasta 4`() {
        assertTrue(una(curva(arco = 79.9)).modificadores.isEmpty())
        assertEquals(setOf(Modificador.LARGA), una(curva(arco = 80.0)).modificadores)
        assertEquals(setOf(Modificador.LARGA), una(curva(arco = 149.9)).modificadores)
    }

    @Test
    fun `muy larga desde 150 m y sustituye a larga`() {
        assertEquals(setOf(Modificador.MUY_LARGA), una(curva(arco = 150.0)).modificadores)
    }

    @Test
    fun `larga no se aplica al grado 5 ni al 6`() {
        assertTrue(una(curva(radio = 200.0, arco = 200.0)).modificadores.isEmpty())
        assertTrue(una(curva(radio = 300.0, arco = 200.0, angulo = 40.0)).modificadores.isEmpty())
    }

    @Test
    fun `cierra cuando el grado de salida es al menos uno menor que el de entrada`() {
        assertEquals(setOf(Modificador.CIERRA), una(curva(entrada = 100.0, salida = 50.0)).modificadores)
        assertEquals(setOf(Modificador.CIERRA), una(curva(entrada = 200.0, salida = 100.0)).modificadores)
    }

    @Test
    fun `abre cuando el grado de salida es al menos uno mayor que el de entrada`() {
        assertEquals(setOf(Modificador.ABRE), una(curva(entrada = 50.0, salida = 100.0)).modificadores)
        assertEquals(setOf(Modificador.ABRE), una(curva(entrada = 100.0, salida = 200.0)).modificadores)
    }

    @Test
    fun `sin cambio de grado no hay cierra ni abre aunque el radio varie mucho`() {
        // Ambos radios son grado 4 (95 a 160 m aprox.): 112 / 150 = 0,75.
        assertTrue(una(curva(radio = 130.0, entrada = 150.0, salida = 112.0)).modificadores.isEmpty())
        assertTrue(una(curva(radio = 130.0, entrada = 112.0, salida = 150.0)).modificadores.isEmpty())
    }

    @Test
    fun `larga y cierra se combinan`() {
        val nota = una(curva(arco = 100.0, entrada = 100.0, salida = 50.0))
        assertEquals(setOf(Modificador.LARGA, Modificador.CIERRA), nota.modificadores)
    }

    @Test
    fun `se unen las notas con un hueco menor de 25 m`() {
        val notas =
            generador.generar(
                listOf(curva(inicio = 0.0), curva(inicio = 84.9, direccion = Direccion.IZQUIERDA)),
            )
        assertTrue(notas[0].unidaConSiguiente)
        assertNull(notas[0].distanciaSiguienteM)
    }

    @Test
    fun `con un hueco de 25 m en curva lenta ya no se unen`() {
        val notas = generador.generar(listOf(curva(inicio = 0.0, radio = 20.0), curva(inicio = 85.0, radio = 20.0)))
        assertFalse(notas[0].unidaConSiguiente)
        assertEquals(20, notas[0].distanciaSiguienteM)
    }

    @Test
    fun `se unen por tiempo si el hueco es menor de 1,5 s a la velocidad de la curva`() {
        // R = 100 m: v = 24,26 m/s, 1,5 s son 36,4 m.
        val unidas = generador.generar(listOf(curva(inicio = 0.0), curva(inicio = 96.0)))
        assertTrue(unidas[0].unidaConSiguiente)
        assertNull(unidas[0].distanciaSiguienteM)
        val separadas = generador.generar(listOf(curva(inicio = 0.0), curva(inicio = 97.0)))
        assertFalse(separadas[0].unidaConSiguiente)
        assertEquals(30, separadas[0].distanciaSiguienteM)
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
                501.0 to 500,
                2000.0 to 500,
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
