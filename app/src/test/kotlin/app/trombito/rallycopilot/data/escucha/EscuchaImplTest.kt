package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.data.voz.FocoAudio
import app.trombito.rallycopilot.domain.escucha.ResultadoEscucha
import app.trombito.rallycopilot.motor.edicion.Orden
import app.trombito.rallycopilot.motor.notas.Direccion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

private class FocoFalso(
    val concede: Boolean = true,
    val eventos: MutableList<String>,
) : FocoAudio {
    override fun pedir(): Boolean {
        eventos += "foco"
        return concede
    }

    override fun soltar() {
        eventos += "suelta"
    }
}

/** Micrófono falso: cada lectura da 100 ms (1.600 muestras) de silencio; [lecturas] cuenta cuántas se pidieron. */
private class AudioFalso(
    val eventos: MutableList<String>,
    private val falloEnLectura: Int? = null,
    private val retardoMs: Long = 0,
) : FabricaAudio {
    var lecturas = 0

    override fun abrir(): FuenteAudio {
        eventos += "abre-audio"
        return object : FuenteAudio {
            override fun leer(buffer: ShortArray): Int {
                lecturas++
                if (retardoMs > 0) Thread.sleep(retardoMs)
                return if (lecturas == falloEnLectura) -1 else buffer.size
            }

            override fun close() {
                eventos += "cierra-audio"
            }
        }
    }
}

/**
 * Reconocedor guionizado por número de lectura (1 = 100 ms): [parciales] da el texto parcial en cada lectura y
 * [finales] marca las lecturas en que acaba una frase con ese texto.
 */
private class ReconocedorFalso(
    private val parciales: Map<Int, String> = emptyMap(),
    private val finales: Map<Int, String> = emptyMap(),
    private val resto: String = "",
) : Reconocedor {
    private var lectura = 0
    private var ultimoFinal = ""
    private var parcialActual = ""
    var cerrado = false

    override fun aceptar(
        muestras: ShortArray,
        n: Int,
    ): Boolean {
        lectura++
        parciales[lectura]?.let { parcialActual = it }
        ultimoFinal = finales[lectura].orEmpty()
        if (lectura in finales) parcialActual = ""
        return lectura in finales
    }

    override fun parcial(): String = parcialActual

    override fun resultado(): String = ultimoFinal

    override fun fin(): String = resto

    override fun close() {
        cerrado = true
    }
}

class EscuchaImplTest {
    private val eventos: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private fun escucha(
        foco: FocoFalso = FocoFalso(eventos = eventos),
        audio: AudioFalso = AudioFalso(eventos),
        reconocedor: ReconocedorFalso? = ReconocedorFalso(),
        permiso: Boolean = true,
    ) = EscuchaImpl(foco, audio, { reconocedor }, { permiso }, Dispatchers.Unconfined)

    @Test
    fun `entiende una nota dictada y acaba 1,2 s despues de la ultima actividad`() =
        runBlocking {
            val audio = AudioFalso(eventos)
            val reconocedor =
                ReconocedorFalso(
                    parciales = mapOf(2 to "derecha", 4 to "derecha tres"),
                    finales = mapOf(6 to "derecha tres larga"),
                )

            val resultado = escucha(audio = audio, reconocedor = reconocedor).escuchar()

            val entendido = resultado as ResultadoEscucha.Entendido
            assertEquals(Direccion.DERECHA, (entendido.orden as Orden.Nota).direccion)
            // Última actividad en la lectura 6 (0,6 s): +1,2 s = lectura 18.
            assertEquals(18, audio.lecturas)
            assertTrue(reconocedor.cerrado)
        }

    @Test
    fun `una pausa corta en mitad de la orden no la corta y se juntan las frases`() =
        runBlocking {
            val reconocedor =
                ReconocedorFalso(
                    finales = mapOf(3 to "derecha tres", 8 to "cincuenta"),
                    parciales = mapOf(7 to "cincuenta"),
                )

            val resultado = escucha(reconocedor = reconocedor).escuchar()

            val nota = (resultado as ResultadoEscucha.Entendido).orden as Orden.Nota
            assertEquals(3, nota.grado)
            assertEquals(50, nota.distanciaSiguienteM)
        }

    @Test
    fun `sin hablar acaba a los 5 s y no se entiende`() =
        runBlocking {
            val audio = AudioFalso(eventos)

            val resultado = escucha(audio = audio).escuchar()

            assertEquals(ResultadoEscucha.NoEntendido(""), resultado)
            assertEquals(50, audio.lecturas)
        }

    @Test
    fun `hablando sin parar acaba a los 5 s con lo que haya`() =
        runBlocking {
            val audio = AudioFalso(eventos)
            val seguido = (1..60).associateWith { "palabra$it" }

            val reconocedor = ReconocedorFalso(parciales = seguido, resto = "repite")

            val resultado = escucha(audio = audio, reconocedor = reconocedor).escuchar()

            assertEquals(50, audio.lecturas)
            assertEquals(Orden.Repite, (resultado as ResultadoEscucha.Entendido).orden)
        }

    @Test
    fun `lo que no es una orden es no entendido con su texto`() =
        runBlocking {
            val reconocedor = ReconocedorFalso(finales = mapOf(2 to "[unk]"))

            val resultado = escucha(reconocedor = reconocedor).escuchar()

            assertEquals(ResultadoEscucha.NoEntendido("[unk]"), resultado)
        }

    @Test
    fun `pide el foco antes de abrir el microfono y lo suelta despues de cerrarlo`() =
        runBlocking {
            escucha(reconocedor = ReconocedorFalso(finales = mapOf(1 to "repite"))).escuchar()

            assertEquals(listOf("foco", "abre-audio", "cierra-audio", "suelta"), eventos)
        }

    @Test
    fun `sin foco no abre el microfono`() =
        runBlocking {
            val resultado = escucha(foco = FocoFalso(concede = false, eventos = eventos)).escuchar()

            assertEquals(ResultadoEscucha.NoDisponible, resultado)
            assertEquals(listOf("foco"), eventos)
        }

    @Test
    fun `sin permiso no pide ni el foco`() =
        runBlocking {
            val resultado = escucha(permiso = false).escuchar()

            assertEquals(ResultadoEscucha.SinPermiso, resultado)
            assertEquals(emptyList<String>(), eventos)
        }

    @Test
    fun `sin modelo es no disponible y suelta el foco`() =
        runBlocking {
            val resultado = escucha(reconocedor = null).escuchar()

            assertEquals(ResultadoEscucha.NoDisponible, resultado)
            assertEquals(listOf("foco", "suelta"), eventos)
        }

    @Test
    fun `si el microfono falla es no disponible y suelta el foco`() =
        runBlocking {
            val resultado = escucha(audio = AudioFalso(eventos, falloEnLectura = 3)).escuchar()

            assertEquals(ResultadoEscucha.NoDisponible, resultado)
            assertEquals(listOf("foco", "abre-audio", "cierra-audio", "suelta"), eventos)
        }

    @Test
    fun `cancelar la escucha suelta el foco y cierra el microfono`() =
        runBlocking {
            val audio = AudioFalso(eventos, retardoMs = 5)
            val escucha = escucha(audio = audio)

            val trabajo = launch(Dispatchers.Default) { escucha.escuchar() }
            while (audio.lecturas < 2) Thread.sleep(1)
            trabajo.cancel()
            trabajo.join()

            assertEquals("suelta", eventos.last())
            assertTrue("cierra-audio" in eventos)
            assertEquals(false, escucha.escuchando.value)
        }

    @Test
    fun `la gramatica de la escucha lleva los destinos que da lugares`() =
        runBlocking {
            val gramaticas = mutableListOf<String>()
            val escucha =
                EscuchaImpl(
                    FocoFalso(eventos = eventos),
                    AudioFalso(eventos),
                    { json ->
                        gramaticas += json
                        ReconocedorFalso(finales = mapOf(2 to "llévame a sóller"))
                    },
                    { true },
                    Dispatchers.Unconfined,
                    lugares = { listOf("sóller", "inca") },
                )

            val resultado = escucha.escuchar()

            assertEquals(Orden.IrA("soller"), (resultado as ResultadoEscucha.Entendido).orden)
            assertTrue("\"llévame a inca\"" in gramaticas.single())
            assertTrue("\"añade parada en sóller\"" in gramaticas.single())
        }
}
