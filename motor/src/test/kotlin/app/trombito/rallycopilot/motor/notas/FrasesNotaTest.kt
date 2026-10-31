package app.trombito.rallycopilot.motor.notas

import org.junit.Assert.assertEquals
import org.junit.Test

class FrasesNotaTest {
    private fun nota(
        direccion: Direccion = Direccion.DERECHA,
        grado: Int? = 4,
        modificadores: Set<Modificador> = emptySet(),
        distancia: Int? = null,
        unida: Boolean = false,
    ) = Nota(0.0, 50.0, direccion, grado, modificadores, distancia, unida)

    @Test
    fun `numeros en palabras`() {
        val esperado =
            mapOf(
                0 to "cero",
                1 to "uno",
                6 to "seis",
                10 to "diez",
                16 to "dieciséis",
                20 to "veinte",
                21 to "veintiuno",
                22 to "veintidós",
                23 to "veintitrés",
                26 to "veintiséis",
                30 to "treinta",
                31 to "treinta y uno",
                50 to "cincuenta",
                80 to "ochenta",
                99 to "noventa y nueve",
                100 to "cien",
                120 to "ciento veinte",
                150 to "ciento cincuenta",
                200 to "doscientos",
                250 to "doscientos cincuenta",
                300 to "trescientos",
                400 to "cuatrocientos",
                500 to "quinientos",
                999 to "novecientos noventa y nueve",
            )
        esperado.forEach { (n, palabras) -> assertEquals("$n", palabras, FrasesNota.enPalabras(n)) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fuera de rango no se convierte`() {
        FrasesNota.enPalabras(1000)
    }

    @Test
    fun `direccion y grado`() {
        assertEquals("derecha cuatro", FrasesNota.frase(nota()))
        assertEquals("izquierda uno", FrasesNota.frase(nota(Direccion.IZQUIERDA, 1)))
        assertEquals("derecha seis", FrasesNota.frase(nota(grado = 6)))
    }

    @Test
    fun `la distancia va despues de la nota`() {
        val n = nota(modificadores = setOf(Modificador.LARGA), distancia = 50)
        assertEquals("derecha cuatro larga, cincuenta", FrasesNota.frase(n))
        assertEquals("derecha tres, ciento veinte", FrasesNota.frase(nota(grado = 3, distancia = 120)))
        assertEquals(
            "izquierda dos, doscientos cincuenta",
            FrasesNota.frase(nota(Direccion.IZQUIERDA, 2, distancia = 250)),
        )
    }

    @Test
    fun `los modificadores salen en orden fijo`() {
        val todos = nota(modificadores = setOf(Modificador.CIERRA, Modificador.MUY_LARGA))
        assertEquals("derecha cuatro muy larga cierra", FrasesNota.frase(todos))
        assertEquals("izquierda cinco abre", FrasesNota.frase(nota(Direccion.IZQUIERDA, 5, setOf(Modificador.ABRE))))
    }

    @Test
    fun `la horquilla no lleva grado`() {
        assertEquals("horquilla izquierda", FrasesNota.frase(nota(Direccion.IZQUIERDA, null)))
        assertEquals("horquilla derecha, cuarenta", FrasesNota.frase(nota(grado = null, distancia = 40)))
    }

    @Test
    fun `frases simplificadas`() {
        assertEquals("derecha cerrada", FrasesNota.frase(nota(grado = 1), simplificada = true))
        assertEquals("derecha cerrada", FrasesNota.frase(nota(grado = 2), simplificada = true))
        assertEquals("izquierda media", FrasesNota.frase(nota(Direccion.IZQUIERDA, 3), simplificada = true))
        assertEquals("derecha media", FrasesNota.frase(nota(grado = 4), simplificada = true))
        assertEquals("derecha suave", FrasesNota.frase(nota(grado = 5), simplificada = true))
        assertEquals("izquierda suave", FrasesNota.frase(nota(Direccion.IZQUIERDA, 6), simplificada = true))
        val larga = nota(grado = 3, modificadores = setOf(Modificador.LARGA), distancia = 50)
        assertEquals("derecha media larga, cincuenta", FrasesNota.frase(larga, simplificada = true))
        assertEquals("horquilla izquierda", FrasesNota.frase(nota(Direccion.IZQUIERDA, null), simplificada = true))
    }

    @Test
    fun `las notas simplificadas se agrupan como dice la tabla`() {
        val categorias = (1..6).map { nota(grado = it).simplificada() }
        assertEquals(
            listOf(
                GradoSimplificado.CERRADA,
                GradoSimplificado.CERRADA,
                GradoSimplificado.MEDIA,
                GradoSimplificado.MEDIA,
                GradoSimplificado.SUAVE,
                GradoSimplificado.SUAVE,
            ),
            categorias,
        )
    }

    @Test
    fun `las encadenadas se unen con y y la distancia va al final`() {
        val secuencia =
            listOf(
                nota(Direccion.IZQUIERDA, 3, unida = true),
                nota(Direccion.DERECHA, 4, distancia = 50),
            )
        assertEquals("izquierda tres y derecha cuatro, cincuenta", FrasesNota.secuencia(secuencia))
        assertEquals("izquierda media y derecha media, cincuenta", FrasesNota.secuencia(secuencia, simplificada = true))
    }

    @Test
    fun `una secuencia separa los grupos con un punto`() {
        val secuencia = listOf(nota(grado = 2, distancia = 100), nota(Direccion.IZQUIERDA, null))
        assertEquals("derecha dos, cien. horquilla izquierda", FrasesNota.secuencia(secuencia))
        assertEquals("", FrasesNota.secuencia(emptyList()))
    }
}
