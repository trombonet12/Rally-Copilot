package app.trombito.rallycopilot.data.alertas

import app.trombito.rallycopilot.data.datosmapa.EsperaDatosSembrados
import app.trombito.rallycopilot.motor.alertas.TipoRadar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CargadorRadaresTest {
    @get:Rule val carpeta = TemporaryFolder()

    private val archivo get() = File(carpeta.root, "radares-mallorca.tsv")
    private var escrituras = 1

    private fun escribir(vararg lineas: String) {
        archivo.writeText((listOf("#radares v1") + lineas).joinToString("\n") + "\n")
        // Que dos escrituras seguidas no coincidan en fecha y tamaño.
        archivo.setLastModified(archivo.lastModified() + escrituras++ * 5_000)
    }

    private fun cargador(siembra: EsperaDatosSembrados = EsperaDatosSembrados.Ninguna) =
        CargadorRadares(archivo, UnconfinedTestDispatcher(), siembra)

    @Test
    fun `sin archivo no hay radares y no es un error`() =
        runTest(UnconfinedTestDispatcher()) {
            val cargador = cargador()

            assertTrue(cargador.radares().isEmpty())
            assertEquals(0, cargador.lecturas)
        }

    @Test
    fun `lee los radares una sola vez y los comparte`() =
        runTest(UnconfinedTestDispatcher()) {
            escribir(
                "FIJO\tf1\t39.6\t2.9\t90\t80\t\tosm",
                "TRAMO_INICIO\tt1-i\t39.61\t2.9\t\t60\tt1\tosm",
                "linea rota",
            )
            val cargador = cargador()

            val primeros = cargador.radares()
            val segundos = cargador.radares()

            assertEquals(listOf("f1", "t1-i"), primeros.map { it.id })
            assertEquals(TipoRadar.FIJO, primeros.first().tipo)
            assertEquals(80, primeros.first().limiteKmh)
            assertEquals(primeros, segundos)
            assertEquals(1, cargador.lecturas)
        }

    @Test
    fun `un archivo ilegible se trata como ausente y no se relee hasta que cambie`() =
        runTest(UnconfinedTestDispatcher()) {
            archivo.writeText("esto no es un tsv de radares\n")
            val cargador = cargador()

            assertTrue(cargador.radares().isEmpty())
            assertTrue(cargador.radares().isEmpty())
            assertEquals(1, cargador.lecturas)

            escribir("FIJO\tf1\t39.6\t2.9\t\t80\t\tosm")
            assertEquals(listOf("f1"), cargador.radares().map { it.id })
            assertEquals(2, cargador.lecturas)
        }

    @Test
    fun `espera a la siembra antes de mirar el archivo`() =
        runTest(UnconfinedTestDispatcher()) {
            val puerta = CompletableDeferred<Unit>()
            val cargador =
                cargador(
                    EsperaDatosSembrados {
                        puerta.await()
                        escribir("FIJO\tf1\t39.6\t2.9\t\t80\t\tosm")
                    },
                )

            val pendiente = async { cargador.radares() }

            assertFalse(pendiente.isCompleted)
            puerta.complete(Unit)
            assertEquals(listOf("f1"), pendiente.await().map { it.id })
        }
}
