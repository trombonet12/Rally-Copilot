package app.trombito.rallycopilot.data.notas

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.GZIPOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ContextoNotasRepositorioTest {
    @get:Rule val carpeta = TemporaryFolder()

    private val vias get() = File(carpeta.root, "vias-mallorca.tsv.gz")
    private val notas get() = File(carpeta.root, "vias-notas-mallorca.tsv.gz")

    private fun repositorio() = ContextoNotasRepositorio(vias, notas, UnconfinedTestDispatcher())

    private fun escribir(
        archivo: File,
        cabecera: String,
        vararg lineas: String,
    ) {
        GZIPOutputStream(archivo.outputStream()).bufferedWriter(Charsets.UTF_8).use { salida ->
            salida.write(cabecera + "\n")
            lineas.forEach { salida.write(it + "\n") }
        }
        // Que dos escrituras seguidas no coincidan en fecha y tamaño.
        archivo.setLastModified(archivo.lastModified() + escrituras++ * 5_000)
    }

    private var escrituras = 1

    private fun escribirVias(maxspeed: String = "60") =
        escribir(
            vias,
            "#vias-mallorca v1",
            "1\tprimary\t$maxspeed\t\t\tno\tMA-1\tCarretera\t39600000,2900000;39600000,2901000",
        )

    @Test
    fun `sin archivo de vias no hay contexto`() =
        runTest(UnconfinedTestDispatcher()) {
            assertNull(repositorio().contexto())
        }

    @Test
    fun `solo vias deja la hoja sin datos de notas`() =
        runTest(UnconfinedTestDispatcher()) {
            escribirVias()
            val contexto = checkNotNull(repositorio().contexto())

            assertNotNull(contexto.indice)
            assertNull(contexto.contextoVias)
        }

    @Test
    fun `con vias-notas instalado hay contexto de vias`() =
        runTest(UnconfinedTestDispatcher()) {
            escribirVias()
            escribir(notas, "#vias-notas v1", "N\t39600000,2900500\tstop")

            val contexto = checkNotNull(repositorio().contexto())

            assertNotNull(contexto.contextoVias)
            assertEquals(1, checkNotNull(contexto.contextoVias).datosNotas.nodos.size)
        }

    @Test
    fun `carga una sola vez y recarga si cambian los datos`() =
        runTest(UnconfinedTestDispatcher()) {
            escribirVias()
            val repositorio = repositorio()
            val primero = repositorio.contexto()

            assertSame(primero, repositorio.contexto())

            escribirVias(maxspeed = "50")
            val recargado = repositorio.contexto()
            assertNotSame(primero, recargado)

            escribir(notas, "#vias-notas v1")
            assertNotNull(checkNotNull(repositorio.contexto()).contextoVias)
        }

    @Test
    fun `un vias-notas ilegible se ignora y deja solo las curvas`() =
        runTest(UnconfinedTestDispatcher()) {
            escribirVias()
            escribir(notas, "#vias-notas v9")

            val contexto = checkNotNull(repositorio().contexto())

            assertNull(contexto.contextoVias)
        }
}
