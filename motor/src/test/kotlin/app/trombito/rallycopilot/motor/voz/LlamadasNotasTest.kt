package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.FrasesNota
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.NotaVisible
import app.trombito.rallycopilot.motor.notas.SelectorNotas
import app.trombito.rallycopilot.motor.notas.TipoAviso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamadasNotasTest {
    private fun nota(
        posicionM: Double,
        grado: Int,
        unida: Boolean = false,
        direccion: Direccion = Direccion.IZQUIERDA,
        distanciaM: Int? = null,
    ) = ElementoHoja.DeNota(
        Nota(
            posicionM,
            posicionM + 20.0,
            direccion,
            grado,
            distanciaSiguienteM = distanciaM,
            unidaConSiguiente = unida,
        ),
    )

    private fun visibles(
        hoja: HojaNotas,
        desde: Int = 0,
        activas: Set<Int> = hoja.elementos.indices.toSet(),
    ) = hoja.elementos.drop(desde).mapIndexed { i, e -> NotaVisible(e, 50.0, (i + desde) in activas) }

    private fun de(
        hoja: HojaNotas,
        visibles: List<NotaVisible>,
        posicionM: Double,
        simplificada: Boolean,
        ultima: Double? = null,
    ) = LlamadasNotas.de(hoja, visibles, EstadoLlamadas(posicionM, simplificada, ultima))

    private fun llamadas(
        hoja: HojaNotas,
        desde: Int = 0,
        posicionM: Double = 0.0,
        simplificada: Boolean = false,
        ultima: Double? = null,
    ) = de(hoja, visibles(hoja, desde), posicionM, simplificada, ultima)

    @Test
    fun `una cadena de dos notas se canta en una llamada con y`() {
        val hoja =
            HojaNotas(
                listOf(
                    nota(300.0, 3, unida = true),
                    nota(340.0, 4, direccion = Direccion.DERECHA, distanciaM = 50),
                ),
            )
        val llamada = llamadas(hoja).single()
        assertEquals("izquierda tres y derecha cuatro, cincuenta", llamada.frase.texto)
        assertEquals(FrasesNota.secuencia(hoja.notas), llamada.frase.texto)
        assertEquals("nota-300.0", llamada.frase.id)
        assertEquals(listOf("nota-300.0", "nota-340.0"), llamada.frase.cubre)
        assertEquals(300.0, llamada.frase.puntoM)
        assertEquals(340.0, llamada.entradaM, 0.0)
    }

    @Test
    fun `la llamada espera a que su primera nota este activa`() {
        val hoja = HojaNotas(listOf(nota(300.0, 3, unida = true), nota(340.0, 4)))
        assertTrue(de(hoja, visibles(hoja, activas = setOf(1)), 0.0, false).isEmpty())
    }

    @Test
    fun `una cadena de cuatro notas se parte en una llamada de tres y otra de una`() {
        val hoja =
            HojaNotas(
                listOf(
                    nota(300.0, 3, unida = true),
                    nota(340.0, 4, unida = true),
                    nota(380.0, 2, unida = true),
                    nota(420.0, 5, distanciaM = 100),
                ),
            )
        val salen = llamadas(hoja)
        assertEquals(2, salen.size)
        val (primera, segunda) = salen
        assertEquals("izquierda tres y izquierda cuatro y izquierda dos", primera.frase.texto)
        assertEquals(listOf("nota-300.0", "nota-340.0", "nota-380.0"), primera.frase.cubre)
        assertEquals(380.0, primera.entradaM, 0.0)
        assertEquals("izquierda cinco, cien", segunda.frase.texto)
        assertEquals("nota-420.0", segunda.frase.id)
        assertTrue(segunda.frase.cubre.isEmpty())
    }

    @Test
    fun `si la ventana empieza a mitad de cadena la cuarta nota sigue abriendo llamada`() {
        val hoja =
            HojaNotas(
                listOf(
                    nota(300.0, 3, unida = true),
                    nota(340.0, 4, unida = true),
                    nota(380.0, 2, unida = true),
                    nota(420.0, 5),
                ),
            )
        assertEquals(listOf("nota-420.0"), llamadas(hoja, desde = 1).map { it.frase.id })
    }

    @Test
    fun `una nota suelta da la misma frase que CandidatasNotas`() {
        val hoja = HojaNotas(listOf(nota(300.0, 3, distanciaM = 80)))
        val esperada = CandidatasNotas.de(visibles(hoja), simplificada = false)
        assertEquals(esperada, llamadas(hoja).map { it.frase })
    }

    @Test
    fun `el modo simplificado quita las notas suaves de la cadena`() {
        val hoja =
            HojaNotas(
                listOf(
                    nota(300.0, 2, unida = true),
                    nota(340.0, 5, unida = true),
                    nota(380.0, 3, direccion = Direccion.DERECHA),
                ),
            )
        val llamada = llamadas(hoja, simplificada = true).single()
        assertEquals("izquierda cerrada y derecha media", llamada.frase.texto)
        assertEquals(listOf("nota-300.0", "nota-380.0"), llamada.frase.cubre)
        assertEquals(380.0, llamada.entradaM, 0.0)
    }

    @Test
    fun `si el simplificado deja una sola nota de la cadena es una nota normal`() {
        val hoja = HojaNotas(listOf(nota(300.0, 5, unida = true), nota(340.0, 2, distanciaM = 60)))
        val llamada = llamadas(hoja, simplificada = true).single()
        assertEquals("nota-340.0", llamada.frase.id)
        assertEquals("izquierda cerrada, sesenta", llamada.frase.texto)
        assertTrue(llamada.frase.cubre.isEmpty())
    }

    @Test
    fun `la siguiente llamada espera mientras no se llega a la entrada y sale al entrar`() {
        val hoja = HojaNotas(listOf(nota(300.0, 3), nota(560.0, 2)))
        val selector = SelectorNotas()

        fun enPosicion(p: Double) =
            de(
                hoja,
                selector.siguientes(hoja, p, 40.0, NivelAnticipacion.NORMAL),
                p,
                false,
                300.0,
            )
        // A 40 m/s la anticipación son 240 m: la nota 560 está activa desde 320, pero aún falta entrar en la 300.
        assertTrue(enPosicion(250.0).none { it.frase.id == "nota-560.0" })
        // Antes de entrar, con la nota lejos (más de 80 m) espera aunque esté activa.
        val activaLejos = visibles(hoja, activas = setOf(1))
        assertTrue(de(hoja, activaLejos, 250.0, false, 300.0).isEmpty())
        // Al llegar a la entrada de la curva anterior ya sale.
        assertEquals(listOf("nota-560.0"), de(hoja, activaLejos, 300.0, false, 300.0).map { it.frase.id })
        // Sin nada cantado antes tampoco espera.
        assertEquals(1, de(hoja, activaLejos, 250.0, false, null).size)
    }

    @Test
    fun `una nota a menos del margen no se calla aunque no se haya entrado`() {
        val hoja = HojaNotas(listOf(nota(300.0, 3), nota(360.0, 2)))
        val activas = visibles(hoja, activas = setOf(1))
        assertEquals(listOf("nota-360.0"), de(hoja, activas, 290.0, false, 300.0).map { it.frase.id })
    }

    @Test
    fun `un aviso de peligro suelto no espera`() {
        val hoja =
            HojaNotas(listOf(nota(300.0, 3), ElementoHoja.DeAviso(Aviso(600.0, TipoAviso.STOP)), nota(900.0, 2)))
        val activas = visibles(hoja, activas = setOf(1, 2))
        val salen = de(hoja, activas, 100.0, false, 300.0)
        assertEquals(listOf("nota-600.0"), salen.map { it.frase.id })
        assertTrue(
            salen
                .single()
                .frase.cubre
                .isEmpty(),
        )
    }

    @Test
    fun `un aviso suelto en medio corta la cadena`() {
        val hoja =
            HojaNotas(
                listOf(
                    nota(300.0, 3, unida = true),
                    ElementoHoja.DeAviso(Aviso(320.0, TipoAviso.PUENTE)),
                    nota(340.0, 4),
                ),
            )
        assertEquals(listOf("nota-300.0", "nota-320.0", "nota-340.0"), llamadas(hoja).map { it.frase.id })
    }
}
