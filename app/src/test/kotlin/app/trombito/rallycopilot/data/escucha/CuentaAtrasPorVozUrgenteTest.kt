package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.voz.PrioridadVoz
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** La cuenta atrás por voz de `ConduccionPorVoz`: urgente y solo si el copiloto puede hablar. */
@OptIn(ExperimentalCoroutinesApi::class)
class CuentaAtrasPorVozUrgenteTest {
    private class Banco(
        ambito: CoroutineScope,
        puedeHablar: suspend () -> Boolean,
    ) {
        val crono = CronoFalso()
        val salida = SalidaFalsa()
        val locutor = LocutorGrabador()
        val conduccion =
            ConduccionPorVoz(
                estado = { EstadoNavegacion.Libre(0L, 0.0) },
                terminar = {},
                crono = crono,
                salida = salida,
                locutor = locutor,
                ambito = ambito,
                pasoCuentaAtrasMs = 1_000L,
                puedeHablar = puedeHablar,
            )

        init {
            crono.estado.value = CronoEnCurso(null, 0L, 0L, enMarcha = false, esperaSalidaManual = true)
        }
    }

    private fun TestScope.banco(puedeHablar: suspend () -> Boolean) = Banco(backgroundScope, puedeHablar)

    @Test
    fun `los numeros y el ya van como urgentes`() =
        runTest {
            val b = banco { true }
            b.conduccion.darSalida(salidaManual = true)
            runCurrent()
            advanceTimeBy(5_500L)
            runCurrent()

            assertEquals(listOf("cinco", "cuatro", "tres", "dos", "uno", "ya"), b.locutor.dichas.toList())
            assertTrue(b.locutor.prioridades.all { it == PrioridadVoz.URGENTE })
            assertEquals(1, b.salida.salidas)
        }

    @Test
    fun `sin poder hablar no suena nada pero se da la salida`() =
        runTest {
            val b = banco { false }
            b.conduccion.darSalida(salidaManual = true)
            runCurrent()
            advanceTimeBy(5_500L)
            runCurrent()

            assertTrue(b.locutor.dichas.isEmpty())
            assertEquals(1, b.salida.salidas)
        }
}
