package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.NotaVisible
import app.trombito.rallycopilot.motor.notas.TipoAviso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidatasNotasTest {
    private val curva = ElementoHoja.DeNota(Nota(300.0, 330.0, Direccion.DERECHA, grado = 3))
    private val puente = ElementoHoja.DeAviso(Aviso(500.0, TipoAviso.PUENTE))

    @Test
    fun `solo las activas pasan y se convierten en frases de nota`() {
        val visibles =
            listOf(
                NotaVisible(curva, 80.0, activa = true),
                NotaVisible(puente, 280.0, activa = false),
            )
        val frases = CandidatasNotas.de(visibles, simplificada = false)
        assertEquals(1, frases.size)
        assertEquals(Frase("nota-300.0", "derecha tres", TipoFrase.NOTA, 300.0), frases.single())
    }

    @Test
    fun `un aviso suelto canta su frase`() {
        val frase = CandidatasNotas.de(listOf(NotaVisible(puente, 50.0, activa = true)), simplificada = false).single()
        assertEquals(Frase("nota-500.0", "puente", TipoFrase.NOTA, 500.0), frase)
    }

    @Test
    fun `la version simplificada cambia el grado por palabra`() {
        val frase = CandidatasNotas.de(listOf(NotaVisible(curva, 80.0, activa = true)), simplificada = true).single()
        assertEquals(curva.frase(simplificada = true), frase.texto)
    }

    @Test
    fun `la id depende solo de la posicion y se mantiene entre llamadas`() {
        val primera = CandidatasNotas.de(listOf(NotaVisible(curva, 80.0, activa = true)), simplificada = false)
        val segunda = CandidatasNotas.de(listOf(NotaVisible(curva, 40.0, activa = true)), simplificada = false)
        assertEquals(primera.single().id, segunda.single().id)
    }

    @Test
    fun `la id puede venir de fuera`() {
        val frase =
            CandidatasNotas
                .de(listOf(NotaVisible(curva, 80.0, activa = true)), simplificada = false) { "estable-1" }
                .single()
        assertEquals("estable-1", frase.id)
    }

    private fun cantadas(elemento: ElementoHoja) =
        CandidatasNotas.de(listOf(NotaVisible(elemento, 50.0, activa = true)), simplificada = true)

    private fun nota(
        grado: Int?,
        modificadores: Set<Modificador> = emptySet(),
        cuidado: Boolean = false,
        unidos: List<Aviso> = emptyList(),
    ) = ElementoHoja.DeNota(Nota(300.0, 330.0, Direccion.DERECHA, grado, modificadores, cuidado = cuidado), unidos)

    private fun suelto(tipo: TipoAviso) = ElementoHoja.DeAviso(Aviso(500.0, tipo))

    @Test
    fun `simplificada canta los grados 1 a 3 y la horquilla con la palabra simplificada`() {
        for (g in listOf(1, 2, 3, null)) {
            val e = nota(g)
            assertEquals("grado $g", listOf(e.frase(simplificada = true)), cantadas(e).map { it.texto })
        }
    }

    @Test
    fun `simplificada calla los grados 4 a 6`() {
        for (g in 4..6) assertTrue("grado $g", cantadas(nota(g)).isEmpty())
    }

    @Test
    fun `sin simplificar canta los grados suaves`() {
        val e = nota(6)
        assertEquals(1, CandidatasNotas.de(listOf(NotaVisible(e, 50.0, activa = true)), simplificada = false).size)
    }

    @Test
    fun `simplificada conserva una escuadra aunque su grado sea suave`() {
        assertEquals(1, cantadas(nota(5, setOf(Modificador.ESCUADRA))).size)
    }

    @Test
    fun `simplificada conserva una nota suave con cuidado`() {
        assertEquals(1, cantadas(nota(6, cuidado = true)).size)
    }

    @Test
    fun `simplificada conserva una nota suave que cierra pero no una larga ni que abre`() {
        assertEquals(1, cantadas(nota(5, setOf(Modificador.CIERRA))).size)
        assertTrue(cantadas(nota(5, setOf(Modificador.LARGA))).isEmpty())
        assertTrue(cantadas(nota(5, setOf(Modificador.MUY_LARGA))).isEmpty())
        assertTrue(cantadas(nota(5, setOf(Modificador.ABRE))).isEmpty())
    }

    @Test
    fun `simplificada conserva una nota suave con aviso unido de peligro y no con uno inofensivo`() {
        val peligros =
            listOf(
                TipoAviso.BACHE,
                TipoAviso.STOP,
                TipoAviso.CEDA,
                TipoAviso.PASO_A_NIVEL,
                TipoAviso.ESTRECHA,
                TipoAviso.SALTO,
            )
        for (tipo in peligros) assertEquals("$tipo", 1, cantadas(nota(6, unidos = listOf(Aviso(290.0, tipo)))).size)
        assertTrue(cantadas(nota(6, unidos = listOf(Aviso(290.0, TipoAviso.RASANTE)))).isEmpty())
    }

    @Test
    fun `simplificada conserva los avisos sueltos de peligro`() {
        val peligros =
            listOf(
                TipoAviso.BACHE,
                TipoAviso.BADEN,
                TipoAviso.CUIDADO,
                TipoAviso.STOP,
                TipoAviso.CEDA,
                TipoAviso.PASO_A_NIVEL,
                TipoAviso.ESTRECHA,
                TipoAviso.SALTO,
            )
        for (tipo in peligros) assertEquals("$tipo", listOf(tipo.frase), cantadas(suelto(tipo)).map { it.texto })
    }

    @Test
    fun `simplificada calla los avisos sueltos que no son peligro`() {
        val resto =
            listOf(
                TipoAviso.PUENTE,
                TipoAviso.TUNEL,
                TipoAviso.ROTONDA,
                TipoAviso.SEMAFORO,
                TipoAviso.CRUCE,
                TipoAviso.RASANTE,
                TipoAviso.CAMBIO_RASANTE,
                TipoAviso.COMPRESION,
            )
        for (tipo in resto) assertTrue("$tipo", cantadas(suelto(tipo)).isEmpty())
    }

    @Test
    fun `simplificada sigue respetando las activas y conserva ids y orden`() {
        val visibles =
            listOf(
                NotaVisible(nota(6), 10.0, activa = true),
                NotaVisible(suelto(TipoAviso.STOP), 20.0, activa = true),
                NotaVisible(nota(2), 90.0, activa = false),
                NotaVisible(curva, 30.0, activa = true),
            )
        val frases = CandidatasNotas.de(visibles, simplificada = true)
        assertEquals(listOf("nota-500.0", "nota-300.0"), frases.map { it.id })
    }
}
