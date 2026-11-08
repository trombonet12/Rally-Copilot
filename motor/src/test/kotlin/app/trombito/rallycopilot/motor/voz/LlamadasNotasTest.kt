package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.FrasesNota
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.NotaVisible
import app.trombito.rallycopilot.motor.notas.SelectorNotas
import app.trombito.rallycopilot.motor.notas.TipoAviso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamadasNotasTest {
    @Suppress("LongParameterList")
    private fun nota(
        posicionM: Double,
        grado: Int,
        unida: Boolean = false,
        direccion: Direccion = Direccion.IZQUIERDA,
        distanciaM: Int? = null,
        radioMinM: Double? = null,
        cuidado: Boolean = false,
        modificadores: Set<Modificador> = emptySet(),
    ) = ElementoHoja.DeNota(
        Nota(
            posicionM,
            posicionM + 20.0,
            direccion,
            grado,
            modificadores,
            distanciaM,
            unida,
            cuidado,
            radioMinM = radioMinM,
        ),
    )

    private fun visibles(
        hoja: HojaNotas,
        desde: Int = 0,
        activas: Set<Int> = hoja.elementos.indices.toSet(),
    ) = hoja.elementos.drop(desde).mapIndexed { i, e -> NotaVisible(e, 50.0, (i + desde) in activas) }

    @Suppress("LongParameterList")
    private fun de(
        hoja: HojaNotas,
        visibles: List<NotaVisible>,
        posicionM: Double,
        simplificada: Boolean,
        ultima: Double? = null,
        vMs: Double = 0.0,
        nivel: NivelAnticipacion = NivelAnticipacion.NORMAL,
    ) = LlamadasNotas.de(hoja, visibles, EstadoLlamadas(posicionM, simplificada, ultima, vMs, nivel))

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
        assertEquals("izquierda tres y izquierda cuatro y izquierda dos y", primera.frase.texto)
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
        val (primera, segunda) = llamadas(hoja, simplificada = true)
        assertEquals("izquierda cerrada, sesenta", primera.frase.texto)
        assertTrue(primera.frase.cubre.isEmpty())
        assertEquals(300.0, primera.entradaM, 0.0)
        assertEquals("derecha media", segunda.frase.texto)
        assertEquals("nota-380.0", segunda.frase.id)
        assertEquals(380.0, segunda.entradaM, 0.0)
    }

    @Test
    fun `la nota cantada antes de una callada dice la distancia hasta la siguiente cantada y no la suya`() {
        val hoja =
            HojaNotas(
                listOf(
                    nota(300.0, 2, distanciaM = 40),
                    nota(370.0, 5, distanciaM = 100),
                    nota(520.0, 2, distanciaM = 60),
                ),
            )
        // 520 - 320 = 200 m hasta la siguiente que se canta.
        assertEquals("izquierda cerrada, doscientos", llamadas(hoja, simplificada = true).first().frase.texto)
        // Sin simplificar la distancia es la suya.
        assertEquals("izquierda dos, cuarenta", llamadas(hoja).first().frase.texto)
    }

    @Test
    fun `si no queda ninguna nota cantada despues la nota no dice distancia ni y`() {
        val hoja = HojaNotas(listOf(nota(300.0, 2, unida = true), nota(340.0, 5, distanciaM = 100)))
        assertEquals("izquierda cerrada", llamadas(hoja, simplificada = true).single().frase.texto)
    }

    @Test
    fun `el simplificado canta la nota de grado 4 con radio minimo de 45 m o menos dentro de la cadena`() {
        val hoja =
            HojaNotas(
                listOf(
                    nota(300.0, 2, unida = true),
                    nota(340.0, 4, radioMinM = 30.0, direccion = Direccion.DERECHA, distanciaM = 50),
                ),
            )
        assertEquals(
            "izquierda cerrada y derecha media, cincuenta",
            llamadas(hoja, simplificada = true).single().frase.texto,
        )
    }

    @Test
    fun `una casi recta corta la cadena aunque no se simplifique`() {
        val hoja =
            HojaNotas(
                listOf(
                    nota(300.0, 3, unida = true),
                    nota(340.0, 6, unida = true, radioMinM = 800.0),
                    nota(380.0, 3, direccion = Direccion.DERECHA),
                ),
            )
        val (primera, segunda) = llamadas(hoja)
        assertEquals("izquierda tres, sesenta", primera.frase.texto)
        assertEquals("derecha tres", segunda.frase.texto)
    }

    @Test
    fun `una llamada cortada por el tope de notas termina en y`() {
        val hoja =
            HojaNotas(
                listOf(
                    nota(300.0, 3, unida = true),
                    nota(340.0, 4, unida = true),
                    nota(380.0, 2, unida = true),
                    nota(420.0, 5, distanciaM = 100),
                ),
            )
        assertTrue(
            llamadas(hoja)
                .first()
                .frase.texto
                .endsWith("izquierda dos y"),
        )
    }

    @Test
    fun `una llamada cortada por pasar de doce palabras se corta antes y termina en y`() {
        val larga = setOf(Modificador.LARGA)
        val hoja =
            HojaNotas(
                listOf(
                    nota(300.0, 2, unida = true, cuidado = true, modificadores = larga),
                    nota(340.0, 2, unida = true, cuidado = true, modificadores = larga),
                    nota(380.0, 2, cuidado = true, modificadores = larga),
                ),
            )
        val (primera, segunda) = llamadas(hoja)
        assertEquals("cuidado, izquierda dos larga y cuidado, izquierda dos larga y", primera.frase.texto)
        assertEquals(listOf("nota-300.0", "nota-340.0"), primera.frase.cubre)
        assertEquals("cuidado, izquierda dos larga", segunda.frase.texto)
        assertEquals("nota-380.0", segunda.frase.id)
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
        val salen = llamadas(hoja)
        assertEquals(listOf("nota-300.0", "nota-320.0", "nota-340.0"), salen.map { it.frase.id })
        // La nota cortada por el aviso sigue unida a la siguiente: termina en " y".
        assertEquals("izquierda tres y", salen.first().frase.texto)
    }

    @Test
    fun `el margen de espera es el maximo entre 80 m y la velocidad por el tiempo del nivel`() {
        fun margen(
            vMs: Double,
            nivel: NivelAnticipacion,
        ) = EstadoLlamadas(0.0, false, null, vMs, nivel).margenEsperaM
        assertEquals(80.0, margen(10.0, NivelAnticipacion.NORMAL), 0.0)
        assertEquals(80.0, margen(0.0, NivelAnticipacion.MUY_PRONTO), 0.0)
        assertEquals(200.0, margen(40.0, NivelAnticipacion.NORMAL), 0.0)
        assertEquals(320.0, margen(40.0, NivelAnticipacion.MUY_PRONTO), 0.0)
        assertEquals(100.0, margen(40.0, NivelAnticipacion.MUY_TARDE), 0.0)
    }

    @Test
    fun `a alta velocidad la siguiente llamada sale antes de entrar en la curva anterior`() {
        val hoja = HojaNotas(listOf(nota(300.0, 3), nota(450.0, 2)))
        val activas = visibles(hoja, activas = setOf(1))
        // A 170 m de la nota: con el margen minimo (80 m) espera.
        assertTrue(de(hoja, activas, 280.0, false, 300.0).isEmpty())
        // A 40 m/s con nivel NORMAL el margen es 200 m: ya sale.
        assertEquals(listOf("nota-450.0"), de(hoja, activas, 280.0, false, 300.0, vMs = 40.0).map { it.frase.id })
    }

    @Test
    fun `un aviso suelto no se canta antes que una nota anterior que espera`() {
        val hoja =
            HojaNotas(listOf(nota(400.0, 3), ElementoHoja.DeAviso(Aviso(420.0, TipoAviso.STOP)), nota(900.0, 2)))
        val activas = visibles(hoja, activas = setOf(0, 1))
        // Posicion 100: la nota 400 espera a entrar en la curva de 300, y el stop va detras de ella.
        assertTrue(de(hoja, activas, 100.0, false, 300.0).isEmpty())
        // Sin espera salen las dos.
        assertEquals(listOf("nota-400.0", "nota-420.0"), de(hoja, activas, 100.0, false, null).map { it.frase.id })
    }

    @Test
    fun `una nota ya cantada con su llamada no bloquea a los avisos de detras`() {
        val hoja =
            HojaNotas(
                listOf(
                    nota(400.0, 3, unida = true),
                    nota(440.0, 4),
                    ElementoHoja.DeAviso(Aviso(500.0, TipoAviso.STOP)),
                ),
            )
        val salen = de(hoja, visibles(hoja), 330.0, false, 350.0)
        assertEquals(listOf("nota-400.0", "nota-500.0"), salen.map { it.frase.id })
    }
}
