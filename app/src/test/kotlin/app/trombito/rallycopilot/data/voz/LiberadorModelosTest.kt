package app.trombito.rallycopilot.data.voz

import app.trombito.rallycopilot.data.escucha.FabricaReconocedor
import app.trombito.rallycopilot.domain.voz.VelocidadHabla
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiberadorModelosTest {
    private class SintetizadorContador : SintetizadorTts {
        var liberaciones = 0

        override fun frecuenciaHz(voz: VozCopiloto) = 1_000

        override fun sintetizar(
            voz: VozCopiloto,
            texto: String,
            velocidad: VelocidadHabla,
        ) = AudioPcm(ShortArray(0), 1_000)

        override fun liberar() {
            liberaciones++
        }
    }

    private class ReconocedoresContador : FabricaReconocedor {
        var liberaciones = 0

        override fun crear(gramaticaJson: String) = null

        override fun liberar() {
            liberaciones++
        }
    }

    private val sintetizador = SintetizadorContador()
    private val reconocedores = ReconocedoresContador()

    private fun liberadorCon(
        dispatcher: CoroutineDispatcher,
        sintetizador: SintetizadorTts = this.sintetizador,
    ) = LiberadorModelos(
        sintetizador,
        reconocedores,
        CoroutineScope(SupervisorJob() + dispatcher),
        dispatcher,
        graciaMs = 10_000,
    )

    @Test
    fun `liberarYa suelta el sintetizador y el reconocedor`() =
        runTest {
            val liberador = liberadorCon(StandardTestDispatcher(testScheduler))

            liberador.liberarYa()

            assertEquals(1, sintetizador.liberaciones)
            assertEquals(1, reconocedores.liberaciones)
        }

    @Test
    fun `al acabar la conduccion libera tras el periodo de gracia`() =
        runTest {
            val liberador = liberadorCon(StandardTestDispatcher(testScheduler))

            liberador.programar()
            advanceTimeBy(9_000)
            runCurrent()
            assertEquals(0, sintetizador.liberaciones)

            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(1, sintetizador.liberaciones)
            assertEquals(1, reconocedores.liberaciones)
        }

    @Test
    fun `una conduccion nueva dentro de la gracia cancela la liberacion`() =
        runTest {
            val liberador = liberadorCon(StandardTestDispatcher(testScheduler))

            liberador.programar()
            advanceTimeBy(5_000)
            liberador.cancelar()
            advanceTimeBy(20_000)
            runCurrent()

            assertEquals(0, sintetizador.liberaciones)
            assertEquals(0, reconocedores.liberaciones)
        }

    @Test
    fun `programar dos veces solo libera una`() =
        runTest {
            val liberador = liberadorCon(StandardTestDispatcher(testScheduler))

            liberador.programar()
            liberador.programar()
            advanceTimeBy(11_000)
            runCurrent()

            assertEquals(1, sintetizador.liberaciones)
        }

    @Test
    fun `si el sintetizador falla igualmente libera el reconocedor`() =
        runTest {
            val roto =
                object : SintetizadorTts by sintetizador {
                    override fun liberar() = throw ErrorSintesis("roto")
                }
            val liberador = liberadorCon(StandardTestDispatcher(testScheduler), roto)

            runCatching { liberador.liberarYa() }

            assertEquals(1, reconocedores.liberaciones)
        }
}
