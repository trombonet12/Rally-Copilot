package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.NotaVisible
import app.trombito.rallycopilot.motor.notas.TipoAviso
import org.junit.Assert.assertEquals
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
}
