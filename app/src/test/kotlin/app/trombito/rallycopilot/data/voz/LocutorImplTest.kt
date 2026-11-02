package app.trombito.rallycopilot.data.voz

import app.trombito.rallycopilot.domain.voz.VozCopiloto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class SintetizadorFalso : SintetizadorTts {
    val llamadas = mutableListOf<Pair<VozCopiloto, String>>()
    var falla = false

    override fun frecuenciaHz(voz: VozCopiloto) = 1_000

    override fun sintetizar(
        voz: VozCopiloto,
        texto: String,
    ): AudioPcm {
        llamadas += voz to texto
        if (falla) throw ErrorSintesis("falso")
        return AudioPcm(ShortArray(texto.length * 100) { 1_000 }, 1_000)
    }

    override fun liberar() = Unit
}

private class ReproductorFalso : ReproductorPcm {
    val sonadas = mutableListOf<Int>()

    /** Pico (valor absoluto máximo) de cada frase que ha sonado. */
    val picos = mutableListOf<Int>()
    var puerta: CompletableDeferred<Unit>? = null

    override suspend fun reproducir(
        muestras: ShortArray,
        frecuenciaHz: Int,
    ) {
        sonadas += muestras.size
        picos += muestras.maxOf { kotlin.math.abs(it.toInt()) }
        puerta?.await()
    }
}

private class FocoFalso(
    var concede: Boolean = true,
) : FocoAudio {
    var pedidos = 0
    var soltados = 0

    override fun pedir(): Boolean {
        pedidos++
        return concede
    }

    override fun soltar() {
        soltados++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LocutorImplTest {
    @get:Rule
    val carpeta = TemporaryFolder()

    private val sint = SintetizadorFalso()
    private val repro = ReproductorFalso()
    private val foco = FocoFalso()

    private fun TestScope.locutor(): Pair<LocutorImpl, CacheFrases> {
        val cache = CacheFrases(carpeta.root)
        val dispatcher = StandardTestDispatcher(testScheduler)
        return LocutorImpl(cache, sint, repro, foco, CoroutineScope(dispatcher), dispatcher) to cache
    }

    @Test
    fun `preparar sintetiza sin duplicados ni lo ya cacheado`() =
        runTest {
            val (locutor, cache) = locutor()
            cache.guardar(VozCopiloto.DAVEFX, "ya", ShortArray(2))

            locutor.preparar(listOf("a", "b", "a", "ya", " "))
            advanceUntilIdle()
            locutor.preparar(listOf("a", "b", "c")) // a y b ya estan en cache
            advanceUntilIdle()

            assertEquals(listOf("a", "b", "c"), sint.llamadas.map { it.second })
            assertTrue(cache.contiene(VozCopiloto.DAVEFX, "c"))
        }

    @Test
    fun `preparar descarta lo que ya no esta en la ultima lista`() =
        runTest {
            val (locutor, cache) = locutor()

            locutor.preparar(listOf("a", "b", "c"))
            locutor.preparar(listOf("c", "d")) // antes de que arranque nada
            advanceUntilIdle()

            assertEquals(listOf("c", "d"), sint.llamadas.map { it.second })
            assertFalse(cache.contiene(VozCopiloto.DAVEFX, "a"))
        }

    @Test
    fun `cambiar de voz vacia la cola y sintetiza con la nueva`() =
        runTest {
            val (locutor, _) = locutor()

            locutor.preparar(listOf("a"))
            locutor.usarVoz(VozCopiloto.SHARVARD)
            locutor.preparar(listOf("a"))
            advanceUntilIdle()

            assertEquals(listOf(VozCopiloto.SHARVARD to "a"), sint.llamadas)
        }

    @Test
    fun `decir sintetiza si no esta en cache, suena y hablando pasa de true a false`() =
        runTest {
            val (locutor, cache) = locutor()
            repro.puerta = CompletableDeferred()
            assertFalse(locutor.hablando.value)

            locutor.decir("curva")
            runCurrent()

            assertTrue(locutor.hablando.value)
            assertEquals(listOf(500), repro.sonadas)
            assertTrue(cache.contiene(VozCopiloto.DAVEFX, "curva"))

            repro.puerta?.complete(Unit)
            advanceUntilIdle()

            assertFalse(locutor.hablando.value)
            assertEquals(1, foco.pedidos)
            assertEquals(1, foco.soltados)
        }

    @Test
    fun `decir usa la cache y no vuelve a sintetizar`() =
        runTest {
            val (locutor, _) = locutor()
            locutor.preparar(listOf("curva"))
            advanceUntilIdle()

            locutor.decir("curva")
            locutor.decir("curva")
            advanceUntilIdle()

            assertEquals(1, sint.llamadas.size)
            assertEquals(listOf(500, 500), repro.sonadas)
        }

    @Test
    fun `si se deniega el foco no suena y hablando vuelve a false`() =
        runTest {
            val (locutor, _) = locutor()
            foco.concede = false

            locutor.decir("curva")
            advanceUntilIdle()

            assertTrue(repro.sonadas.isEmpty())
            assertFalse(locutor.hablando.value)
            assertEquals(1, foco.soltados)
        }

    @Test
    fun `las frases suenan de una en una y por orden`() =
        runTest {
            val (locutor, _) = locutor()
            repro.puerta = CompletableDeferred()

            locutor.decir("uno")
            locutor.decir("dos tres")
            runCurrent()
            assertEquals(listOf(300), repro.sonadas) // la segunda espera

            repro.puerta?.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(300, 800), repro.sonadas)
        }

    @Test
    fun `si la sintesis falla no suena y el locutor sigue funcionando`() =
        runTest {
            val (locutor, _) = locutor()
            sint.falla = true

            locutor.decir("mal")
            advanceUntilIdle()
            sint.falla = false
            locutor.decir("bien")
            advanceUntilIdle()

            assertEquals(listOf(400), repro.sonadas)
            assertFalse(locutor.hablando.value)
        }

    @Test
    fun `probar usa la voz pedida sin cambiar la elegida`() =
        runTest {
            val (locutor, _) = locutor()
            locutor.usarVoz(VozCopiloto.DAVEFX)

            locutor.probar(VozCopiloto.SHARVARD)
            locutor.decir("hola")
            advanceUntilIdle()

            assertEquals(listOf(VozCopiloto.SHARVARD, VozCopiloto.DAVEFX), sint.llamadas.map { it.first })
        }

    @Test
    fun `el volumen propio escala lo que suena sin tocar la cache`() =
        runTest {
            val (locutor, cache) = locutor()

            locutor.decir("a")
            advanceUntilIdle()
            locutor.usarVolumen(50)
            locutor.decir("a")
            advanceUntilIdle()
            locutor.usarVolumen(150)
            locutor.decir("a")
            advanceUntilIdle()

            assertEquals(listOf(1_000, 500, 1_500), repro.picos)
            assertEquals(1_000, cache.leer(VozCopiloto.DAVEFX, "a")!!.maxOf { it.toInt() })
        }

    @Test
    fun `probarFrase dice el texto con la voz pedida y el volumen vigente`() =
        runTest {
            val (locutor, _) = locutor()
            locutor.usarVolumen(50)

            locutor.probarFrase(VozCopiloto.SHARVARD, "derecha cuatro")
            advanceUntilIdle()

            assertEquals(listOf(VozCopiloto.SHARVARD to "derecha cuatro"), sint.llamadas)
            assertEquals(listOf(500), repro.picos)
        }

    @Test
    fun `duracion estimada es la real si esta en cache y 0,07 s por caracter si no`() =
        runTest {
            val (locutor, _) = locutor()
            assertEquals(0.28, locutor.duracionEstimadaS("hola"), 1e-9)

            locutor.preparar(listOf("hola"))
            advanceUntilIdle()

            // 400 muestras a 1000 Hz
            assertEquals(0.4, locutor.duracionEstimadaS("hola"), 1e-9)
        }
}
