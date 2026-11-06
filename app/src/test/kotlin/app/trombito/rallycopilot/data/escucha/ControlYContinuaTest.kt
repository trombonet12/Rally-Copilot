package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.escucha.ControlOrdenes
import app.trombito.rallycopilot.domain.escucha.Escucha
import app.trombito.rallycopilot.domain.escucha.ResultadoEscucha
import app.trombito.rallycopilot.motor.edicion.Orden
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private class EscuchaGuionizada(
    private val resultado: ResultadoEscucha,
) : Escucha {
    val liberar = CompletableDeferred<Unit>()
    val llamadas = AtomicInteger()
    override val escuchando: StateFlow<Boolean> = MutableStateFlow(false)

    override fun tienePermiso(): Boolean = true

    override suspend fun escuchar(): ResultadoEscucha {
        llamadas.incrementAndGet()
        liberar.await()
        return resultado
    }
}

/** Escucha que devuelve los resultados guionizados uno a uno (el último se repite). */
private class EscuchaSecuencia(
    private vararg val resultados: ResultadoEscucha,
) : Escucha {
    val llamadas = AtomicInteger()
    override val escuchando: StateFlow<Boolean> = MutableStateFlow(false)

    override fun tienePermiso(): Boolean = true

    override suspend fun escuchar(): ResultadoEscucha =
        resultados[minOf(llamadas.getAndIncrement(), resultados.lastIndex)]
}

class ControlYContinuaTest {
    private val ambito = CoroutineScope(Dispatchers.Default)
    private val banco = BancoOrdenes(ambito)
    private val ajustes = banco.ajustes
    private val locutor = banco.locutor

    private fun ejecutor() = banco.ejecutor

    @Test
    fun `escuchar una orden la ejecuta y una segunda peticion mientras escucha se ignora`() =
        runBlocking {
            val escucha = EscuchaGuionizada(ResultadoEscucha.Entendido(Orden.Silencio, "silencio"))
            val control = ControlOrdenesImpl(escucha, ejecutor(), locutor, ambito)

            control.escucharOrden()
            control.escucharOrden()
            assertTrue(control.escuchando.value)
            escucha.liberar.complete(Unit)
            withTimeout(5_000) { control.escuchando.first { !it } }

            assertEquals(1, escucha.llamadas.get())
            assertEquals(ModoCopiloto.SILENCIO, ajustes.mutable.value.modoCopiloto)
        }

    @Test
    fun `tras escuchar deja lo oido, la orden y la respuesta en el dialogo`() =
        runBlocking {
            val escucha = EscuchaGuionizada(ResultadoEscucha.Entendido(Orden.Silencio, "silencio"))
            val control = ControlOrdenesImpl(escucha, ejecutor(), locutor, ambito)
            assertEquals(null, control.dialogo.value)

            control.escucharOrden()
            escucha.liberar.complete(Unit)
            withTimeout(5_000) { control.escuchando.first { !it } }

            val dialogo = checkNotNull(control.dialogo.value)
            assertEquals("silencio", dialogo.texto)
            assertEquals(Orden.Silencio, dialogo.orden)
            assertEquals(1L, dialogo.id)
        }

    @Test
    fun `espera a que el copiloto termine de hablar antes de abrir el microfono`() =
        runBlocking {
            locutor.hablando.value = true
            val escucha = EscuchaGuionizada(ResultadoEscucha.NoEntendido(""))
            escucha.liberar.complete(Unit)
            val control = ControlOrdenesImpl(escucha, ejecutor(), locutor, ambito)

            control.escucharOrden()
            Thread.sleep(200)
            assertEquals(0, escucha.llamadas.get())
            locutor.hablando.value = false
            withTimeout(5_000) { control.escuchando.first { !it } }

            assertEquals(1, escucha.llamadas.get())
            assertEquals(listOf("no te he entendido, di ayuda"), locutor.dichas.toList())
        }

    private class ReconocedorDeActivacion(
        private val oye: Boolean,
    ) : Reconocedor {
        override fun aceptar(
            muestras: ShortArray,
            n: Int,
        ): Boolean = false

        override fun parcial(): String = if (oye) "copiloto" else ""

        override fun resultado(): String = ""

        override fun fin(): String = ""

        override fun close() = Unit
    }

    private class ControlFalso : ControlOrdenes {
        val peticiones = AtomicInteger()
        private val _escuchando = MutableStateFlow(false)
        override val escuchando: StateFlow<Boolean> = _escuchando

        override fun tienePermiso(): Boolean = true

        override fun escucharOrden() {
            peticiones.incrementAndGet()
        }
    }

    /** Micrófono falso con voz fuerte (la puerta se abre). */
    private val microfonoConVoz =
        FabricaAudio {
            object : FuenteAudio {
                override fun leer(buffer: ShortArray): Int {
                    buffer.fill(3_000)
                    Thread.sleep(1)
                    return buffer.size
                }

                override fun close() = Unit
            }
        }

    private fun continua(
        audio: FabricaAudio,
        permiso: Boolean,
        control: ControlOrdenes,
        tic: suspend () -> Unit = {},
    ) = EscuchaContinua(
        audio,
        { ReconocedorDeActivacion(true) },
        { permiso },
        ajustes.ajustes,
        control,
        Dispatchers.IO,
        tic,
    )

    @Test
    fun `con Copiloto activado al oir la palabra pide escuchar una orden`() =
        runBlocking {
            val control = ControlFalso()
            val continua =
                continua(microfonoConVoz, permiso = true, control = control)
            ajustes.mutable.value = ajustes.mutable.value.copy(activarConCopiloto = true)

            val trabajo = ambito.launch { continua.correr() }
            withTimeout(5_000) { while (control.peticiones.get() == 0) Thread.sleep(5) }
            trabajo.cancel()

            assertTrue(control.peticiones.get() >= 1)
        }

    @Test
    fun `tras una pregunta de si o no escucha una vez mas la respuesta y la ejecuta`() =
        runBlocking {
            banco.estado = guiandoCon(10_000.0, 600.0, 0L)
            val escucha =
                EscuchaSecuencia(
                    ResultadoEscucha.Entendido(Orden.ParaCrono, "para el crono"),
                    ResultadoEscucha.Entendido(Orden.Confirma(true), "sí"),
                )
            val control = ControlOrdenesImpl(escucha, ejecutor(), locutor, ambito)

            control.escucharOrden()
            withTimeout(5_000) { control.escuchando.first { !it } }

            assertEquals(2, escucha.llamadas.get())
            assertEquals(1, banco.terminadas)
            assertEquals(listOf("di sí para terminar", "conducción terminada"), locutor.dichas.toList())
            assertEquals(false, banco.ejecutor.esperaConfirmacion)
        }

    @Test
    fun `si no se responde a la pregunta se cancela y no se termina`() =
        runBlocking {
            banco.estado = guiandoCon(10_000.0, 600.0, 0L)
            val escucha =
                EscuchaSecuencia(
                    ResultadoEscucha.Entendido(Orden.ParaCrono, "para el crono"),
                    ResultadoEscucha.NoEntendido(""),
                )
            val control = ControlOrdenesImpl(escucha, ejecutor(), locutor, ambito)

            control.escucharOrden()
            withTimeout(5_000) { control.escuchando.first { !it } }

            assertEquals(0, banco.terminadas)
            assertEquals(listOf("di sí para terminar", "cancelado"), locutor.dichas.toList())
            assertEquals(false, banco.ejecutor.esperaConfirmacion)
        }

    @Test
    fun `una orden normal no abre una segunda escucha`() =
        runBlocking {
            val escucha = EscuchaSecuencia(ResultadoEscucha.Entendido(Orden.Habla, "habla"))
            val control = ControlOrdenesImpl(escucha, ejecutor(), locutor, ambito)

            control.escucharOrden()
            withTimeout(5_000) { control.escuchando.first { !it } }

            assertEquals(1, escucha.llamadas.get())
        }

    @Test
    fun `al oir la palabra suena el tic antes de escuchar la orden`() =
        runBlocking {
            val orden = java.util.Collections.synchronizedList(mutableListOf<String>())
            val control =
                object : ControlOrdenes {
                    override val escuchando: StateFlow<Boolean> = MutableStateFlow(false)

                    override fun tienePermiso(): Boolean = true

                    override fun escucharOrden() {
                        orden += "orden"
                    }
                }
            val continua = continua(microfonoConVoz, permiso = true, control = control, tic = { orden += "tic" })
            ajustes.mutable.value = ajustes.mutable.value.copy(activarConCopiloto = true)

            val trabajo = ambito.launch { continua.correr() }
            withTimeout(5_000) { while (orden.size < 2) Thread.sleep(5) }
            trabajo.cancel()

            assertEquals(listOf("tic", "orden"), orden.take(2))
        }

    @Test
    fun `el tic generado es un seno corto con rampas`() {
        val pcm = GeneradorTic.pcm()
        assertEquals(GeneradorTic.FRECUENCIA_MUESTREO_HZ * GeneradorTic.DURACION_MS / 1000, pcm.size)
        assertEquals(0, pcm.first().toInt())
        assertEquals(0, pcm.last().toInt())
        assertTrue(pcm.any { kotlin.math.abs(it.toInt()) > 10_000 })
    }

    @Test
    fun `con Copiloto desactivado o sin permiso no abre el microfono`() =
        runBlocking {
            val aperturas = AtomicInteger()
            val audio =
                FabricaAudio {
                    aperturas.incrementAndGet()
                    null
                }
            val control = ControlFalso()
            ajustes.mutable.value = ajustes.mutable.value.copy(activarConCopiloto = false)

            val desactivado =
                ambito.launch {
                    continua(audio, permiso = true, control = control).correr()
                }
            Thread.sleep(300)
            desactivado.cancel()
            assertEquals(0, aperturas.get())

            ajustes.mutable.value = ajustes.mutable.value.copy(activarConCopiloto = true)
            val sinPermiso =
                ambito.launch {
                    continua(audio, permiso = false, control = control).correr()
                }
            Thread.sleep(300)
            sinPermiso.cancel()

            assertEquals(0, aperturas.get())
            assertEquals(0, control.peticiones.get())
        }
}
