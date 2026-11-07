package app.trombito.rallycopilot.data.voz

import app.trombito.rallycopilot.domain.voz.PrioridadVoz
import app.trombito.rallycopilot.domain.voz.VelocidadHabla
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

    /** Cada llamada con la velocidad pedida, en orden. */
    val velocidades = mutableListOf<VelocidadHabla>()
    var falla = false
    var liberaciones = 0

    override fun frecuenciaHz(voz: VozCopiloto) = 1_000

    override fun sintetizar(
        voz: VozCopiloto,
        texto: String,
        velocidad: VelocidadHabla,
    ): AudioPcm {
        llamadas += voz to texto
        velocidades += velocidad
        if (falla) throw ErrorSintesis("falso")
        return AudioPcm(ShortArray(texto.length * 100) { 1_000 }, 1_000)
    }

    override fun liberar() {
        liberaciones++
    }
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

    /** Reloj monótono del locutor en los tests. */
    private var ahoraMs = 1_000L

    private fun TestScope.locutor(): Pair<LocutorImpl, CacheFrases> {
        val cache = CacheFrases(carpeta.root)
        val dispatcher = StandardTestDispatcher(testScheduler)
        return LocutorImpl(cache, sint, repro, foco, CoroutineScope(dispatcher), dispatcher, { ahoraMs }) to cache
    }

    @Test
    fun `cerrar libera el sintetizador por el dispatcher de sintesis sin crear hilos`() =
        runTest {
            val (locutor, _) = locutor()

            locutor.cerrar()
            assertEquals(0, sint.liberaciones) // aun no: va encolado en el dispatcher de sintesis
            advanceUntilIdle()

            assertEquals(1, sint.liberaciones)
        }

    @Test
    fun `tras un rato sin hablar vuelca el uso de la cache y no antes`() =
        runTest {
            val (locutor, cache) = locutor()
            locutor.decir("izquierda")
            advanceUntilIdle() // sintetiza y guarda
            val fichero = java.io.File(carpeta.root, CacheFrases.clave(VozCopiloto.DAVEFX, "izquierda") + ".pcm")
            fichero.setLastModified(1_000L)
            locutor.decir("izquierda")
            runCurrent() // esta vez sale de la cache: queda marcada como usada, sin tocar el disco
            assertEquals(1_000L, fichero.lastModified())

            testScheduler.advanceTimeBy(LocutorImpl.INACTIVIDAD_CACHE_MS - 1)
            runCurrent()
            assertEquals(1_000L, fichero.lastModified())

            testScheduler.advanceTimeBy(2)
            runCurrent()
            assertTrue(fichero.lastModified() > 1_000L)
            assertTrue(cache.contiene(VozCopiloto.DAVEFX, "izquierda"))
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

    @Test
    fun `una frase urgente vacia lo que espera y suena la primera tras la que ya suena`() =
        runTest {
            val (locutor, _) = locutor()
            repro.puerta = CompletableDeferred()

            locutor.decir("uno") // empieza y se queda sonando
            runCurrent()
            locutor.decir("dos tres")
            locutor.decir("cuatro")
            locutor.decir("alerta", PrioridadVoz.URGENTE)
            runCurrent()
            assertEquals("la que suena no se corta", listOf(300), repro.sonadas)

            repro.puerta?.complete(Unit)
            advanceUntilIdle()

            // «dos tres» y «cuatro» (8 y 6 caracteres) se descartaron: solo suena la urgente.
            assertEquals(listOf(300, 600), repro.sonadas)
        }

    @Test
    fun `dos urgentes seguidas suenan solo la ultima si la primera no habia empezado`() =
        runTest {
            val (locutor, _) = locutor()
            repro.puerta = CompletableDeferred()
            locutor.decir("uno")
            runCurrent()

            locutor.decir("dos tres", PrioridadVoz.URGENTE)
            locutor.decir("alerta", PrioridadVoz.URGENTE)
            repro.puerta?.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(300, 600), repro.sonadas)
        }

    @Test
    fun `una frase caducada antes de empezar se descarta sin sintetizar`() =
        runTest {
            val (locutor, _) = locutor()
            repro.puerta = CompletableDeferred()
            locutor.decir("uno")
            runCurrent()

            locutor.decir("tarde", caducaMs = ahoraMs + 1_000)
            locutor.decir("fin")
            runCurrent()
            ahoraMs += 2_000 // pasó el plazo mientras sonaba «uno»
            repro.puerta?.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(300, 300), repro.sonadas)
            assertFalse("no se sintetizo", sint.llamadas.any { it.second == "tarde" })
        }

    @Test
    fun `una frase con plazo vigente suena con normalidad`() =
        runTest {
            val (locutor, _) = locutor()

            locutor.decir("vigente", caducaMs = ahoraMs + 5_000)
            advanceUntilIdle()

            assertEquals(listOf(700), repro.sonadas)
        }

    @Test
    fun `cada velocidad tiene su cache y se sintetiza a la velocidad pedida`() =
        runTest {
            val (locutor, cache) = locutor()

            locutor.decir("a")
            advanceUntilIdle()
            locutor.usarVelocidad(VelocidadHabla.RAPIDA)
            locutor.decir("a") // no vale la normal: se sintetiza otra vez
            advanceUntilIdle()
            locutor.decir("a") // ya esta a velocidad rapida
            advanceUntilIdle()

            assertEquals(listOf(VelocidadHabla.NORMAL, VelocidadHabla.RAPIDA), sint.velocidades)
            assertTrue(cache.contiene(VozCopiloto.DAVEFX, "a", VelocidadHabla.NORMAL))
            assertTrue(cache.contiene(VozCopiloto.DAVEFX, "a", VelocidadHabla.RAPIDA))
            assertEquals(3, repro.sonadas.size)
        }

    @Test
    fun `cambiar la velocidad regenera las frases preparadas y descarta la cola anterior`() =
        runTest {
            val (locutor, cache) = locutor()
            locutor.preparar(listOf("a", "b"))
            advanceUntilIdle()
            assertEquals(listOf("a", "b"), sint.llamadas.map { it.second })

            locutor.usarVelocidad(VelocidadHabla.RAPIDA)
            locutor.preparar(listOf("a", "b")) // lo normal no cuenta: hay que sintetizar de nuevo
            advanceUntilIdle()

            assertEquals(listOf("a", "b", "a", "b"), sint.llamadas.map { it.second })
            assertEquals(
                listOf(VelocidadHabla.NORMAL, VelocidadHabla.NORMAL, VelocidadHabla.RAPIDA, VelocidadHabla.RAPIDA),
                sint.velocidades,
            )
            assertTrue(cache.contiene(VozCopiloto.DAVEFX, "b", VelocidadHabla.RAPIDA))
        }

    @Test
    fun `la vista previa de la velocidad dice la frase con la velocidad vigente`() =
        runTest {
            val (locutor, _) = locutor()

            locutor.usarVelocidad(VelocidadHabla.RAPIDA)
            locutor.probarFrase(VozCopiloto.DAVEFX, "derecha cuatro")
            locutor.probar(VozCopiloto.SHARVARD)
            advanceUntilIdle()

            assertEquals(listOf(VelocidadHabla.RAPIDA, VelocidadHabla.RAPIDA), sint.velocidades)
        }

    @Test
    fun `la duracion estimada sin cache es menor a velocidad rapida`() =
        runTest {
            val (locutor, _) = locutor()
            val normal = locutor.duracionEstimadaS("hola")

            locutor.usarVelocidad(VelocidadHabla.RAPIDA)

            assertEquals(normal / VelocidadHabla.RAPIDA.factor, locutor.duracionEstimadaS("hola"), 1e-6)
        }

    private fun TestScope.locutorSinSharvard(): LocutorImpl {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return LocutorImpl(
            CacheFrases(carpeta.root),
            sint,
            repro,
            foco,
            CoroutineScope(dispatcher),
            dispatcher,
            { ahoraMs },
            vozDisponible = { VozCopiloto.DAVEFX },
        )
    }

    @Test
    fun `si la voz elegida no esta instalada habla dave y no mezcla la cache`() =
        runTest {
            val locutor = locutorSinSharvard()
            val cache = CacheFrases(carpeta.root)

            locutor.usarVoz(VozCopiloto.SHARVARD)
            locutor.preparar(listOf("a"))
            locutor.decir("b")
            advanceUntilIdle()

            assertEquals(setOf(VozCopiloto.DAVEFX to "a", VozCopiloto.DAVEFX to "b"), sint.llamadas.toSet())
            assertTrue(cache.contiene(VozCopiloto.DAVEFX, "a"))
            assertFalse(cache.contiene(VozCopiloto.SHARVARD, "a"))
        }

    @Test
    fun `la muestra de una voz no instalada suena con dave`() =
        runTest {
            val locutor = locutorSinSharvard()

            locutor.probar(VozCopiloto.SHARVARD)
            advanceUntilIdle()

            assertEquals(listOf(VozCopiloto.DAVEFX), sint.llamadas.map { it.first })
        }
}
