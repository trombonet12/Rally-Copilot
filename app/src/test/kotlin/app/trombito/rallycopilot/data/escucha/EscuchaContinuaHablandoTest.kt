package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.escucha.ControlOrdenes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** La escucha de «Copiloto» no decodifica mientras el propio copiloto habla (REN-2). */
class EscuchaContinuaHablandoTest {
    private val ambito = CoroutineScope(Dispatchers.Default)

    private class ReconocedorContador(
        val bloques: AtomicInteger,
    ) : Reconocedor {
        override fun aceptar(
            muestras: ShortArray,
            n: Int,
        ): Boolean {
            bloques.incrementAndGet()
            return false
        }

        override fun parcial(): String = ""

        override fun resultado(): String = ""

        override fun fin(): String = ""

        override fun close() = Unit
    }

    private class ControlInerte : ControlOrdenes {
        override val escuchando: StateFlow<Boolean> = MutableStateFlow(false)

        override fun tienePermiso(): Boolean = true

        override fun escucharOrden() = Unit
    }

    /** Micrófono con voz fuerte durante [bloques] lecturas y luego «fallo» (el bucle sale y avisa por [acabado]). */
    private fun microfono(
        bloques: Int,
        acabado: CountDownLatch,
    ): FabricaAudio {
        var restantes = bloques
        return FabricaAudio {
            object : FuenteAudio {
                override fun leer(buffer: ShortArray): Int {
                    if (restantes-- <= 0) return -1
                    buffer.fill(3_000)
                    return buffer.size
                }

                override fun close() = acabado.countDown()
            }
        }
    }

    private fun decodificadosCon(hablando: () -> Boolean): Int =
        runBlocking {
            val decodificados = AtomicInteger()
            val acabado = CountDownLatch(1)
            val continua =
                EscuchaContinua(
                    microfono(bloques = 20, acabado),
                    { ReconocedorContador(decodificados) },
                    { true },
                    flowOf(AjustesConduccion(activarConCopiloto = true)),
                    ControlInerte(),
                    Dispatchers.IO,
                )
            val trabajo = ambito.launch { continua.correr(hablando) }
            assertTrue(acabado.await(5, TimeUnit.SECONDS))
            trabajo.cancel()
            decodificados.get()
        }

    @Test
    fun `con voz y sin hablar el copiloto decodifica`() {
        assertTrue(decodificadosCon { false } > 0)
    }

    @Test
    fun `mientras habla el copiloto no decodifica nada`() {
        assertEquals(0, decodificadosCon { true })
    }
}
