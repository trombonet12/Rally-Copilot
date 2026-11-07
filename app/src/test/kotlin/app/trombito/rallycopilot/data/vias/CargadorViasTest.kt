package app.trombito.rallycopilot.data.vias

import app.trombito.rallycopilot.data.notas.ContextoNotasRepositorio
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
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
class CargadorViasTest {
    @get:Rule val carpeta = TemporaryFolder()

    private val vias get() = File(carpeta.root, "vias-mallorca.tsv.gz")
    private var escrituras = 1

    private fun escribirVias(
        maxspeed: String = "60",
        vararg extra: String,
    ) {
        GZIPOutputStream(vias.outputStream()).bufferedWriter(Charsets.UTF_8).use { salida ->
            salida.write("#vias-mallorca v1\n")
            salida.write("1\tprimary\t$maxspeed\t\t\tno\tMA-1\tCarretera\t39600000,2900000;39600000,2901000\n")
            extra.forEach { salida.write(it + "\n") }
        }
        // Que dos escrituras seguidas no coincidan en fecha y tamaño.
        vias.setLastModified(vias.lastModified() + escrituras++ * 5_000)
    }

    private fun fijacion() =
        Fijacion(PuntoGeo(39.6, 2.9005), tiempoMs = 0, velocidadMs = 15.0, rumboGrados = 90.0, precisionM = 4.0)

    @Test
    fun `dos consumidores comparten una sola lectura y el mismo indice`() =
        runTest(UnconfinedTestDispatcher()) {
            escribirVias()
            val cargador = CargadorVias(vias, UnconfinedTestDispatcher())
            val proveedor = ProveedorViaActualOsm(cargador)
            val sinNotas = File(carpeta.root, "no-hay.tsv.gz")
            val notas = ContextoNotasRepositorio(cargador, sinNotas, UnconfinedTestDispatcher())

            assertEquals(60, proveedor.viaEn(fijacion())?.limiteKmh)
            val contexto = checkNotNull(notas.contexto())
            assertEquals(60, proveedor.viaEn(fijacion())?.limiteKmh)

            assertEquals(1, cargador.lecturas)
            assertSame(checkNotNull(cargador.datos()).indice, contexto.indice)
        }

    @Test
    fun `recarga una sola vez para todos si el archivo cambia`() =
        runTest(UnconfinedTestDispatcher()) {
            escribirVias()
            val cargador = CargadorVias(vias, UnconfinedTestDispatcher())
            val proveedor = ProveedorViaActualOsm(cargador)
            val primero = cargador.datos()
            assertEquals(60, proveedor.viaEn(fijacion())?.limiteKmh)

            escribirVias(maxspeed = "80")

            // Por fijación no se mira el disco (REN-16): sigue lo cargado hasta que se revalide.
            assertEquals(60, proveedor.viaEn(fijacion())?.limiteKmh)
            assertEquals(1, cargador.lecturas)

            // Al empezar una conducción las notas piden los datos y eso revalida el archivo.
            assertNotSame(primero, cargador.datos())
            assertEquals(80, proveedor.viaEn(fijacion())?.limiteKmh)
            assertEquals(2, cargador.lecturas)
        }

    @Test
    fun `en memoria no hay nada hasta la primera carga y despues es el mismo indice`() =
        runTest(UnconfinedTestDispatcher()) {
            escribirVias()
            val cargador = CargadorVias(vias, UnconfinedTestDispatcher())
            assertNull(cargador.enMemoria())

            val cargados = checkNotNull(cargador.datos())

            assertSame(cargados, cargador.enMemoria())
            assertEquals(1, cargador.lecturas)
        }

    @Test
    fun `sin archivo o ilegible no hay datos y no se relee hasta que cambie`() =
        runTest(UnconfinedTestDispatcher()) {
            val cargador = CargadorVias(vias, UnconfinedTestDispatcher())
            assertNull(cargador.datos())
            assertEquals(0, cargador.lecturas)

            vias.writeText("esto no es gzip")
            assertNull(cargador.datos())
            assertNull(cargador.datos())
            assertEquals(1, cargador.lecturas)

            escribirVias()
            assertNotNull(cargador.datos())
            assertEquals(2, cargador.lecturas)
        }

    @Test
    fun `ahorro en memoria al compartir el indice es el de una copia de las vias`() =
        runTest(UnconfinedTestDispatcher()) {
            val trazo = "39610000,2900000;39610000,2901000;39610000,2902000"
            val extra = (2..51).map { id -> "$id\tprimary\t60\t\t\tno\tMA-$id\tV$id\t$trazo" }
            escribirVias("60", *extra.toTypedArray())
            val datos = checkNotNull(CargadorVias(vias, UnconfinedTestDispatcher()).datos())

            val puntos = datos.vias.sumOf { it.puntos.size }
            // Antes: dos copias de vías e índices (una por consumidor); ahora una. Ahorro = vías x puntos de una copia.
            assertEquals(51, datos.vias.size)
            assertEquals(2 + 50 * 3, puntos)
        }
}
