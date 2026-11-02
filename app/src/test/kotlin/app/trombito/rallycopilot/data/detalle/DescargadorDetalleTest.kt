package app.trombito.rallycopilot.data.detalle

import app.trombito.rallycopilot.motor.precarga.Tesela
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** Fuente falsa: mar en las columnas de [mar], error en las de [fallan], JPEG de [tamano] bytes en el resto. */
class FuenteFalsa(
    private val tamano: Int = 1_000,
    private val mar: (Tesela) -> Boolean = { false },
    private val fallan: (Tesela) -> Boolean = { false },
) : FuenteTeselas {
    val pedidas = java.util.concurrent.CopyOnWriteArrayList<Tesela>()
    private val activas = AtomicInteger(0)
    val maxActivas = AtomicInteger(0)

    override suspend fun obtener(tesela: Tesela): ResultadoTesela {
        pedidas += tesela
        maxActivas.accumulateAndGet(activas.incrementAndGet()) { a, b -> maxOf(a, b) }
        try {
            delay(5)
            if (fallan(tesela)) throw IOException("sin red")
            return if (mar(tesela)) {
                ResultadoTesela.SinCobertura
            } else {
                ResultadoTesela.Jpeg(ByteArray(tamano) { 7 })
            }
        } finally {
            activas.decrementAndGet()
        }
    }
}

@RunWith(RobolectricTestRunner::class)
class DescargadorDetalleTest {
    @get:Rule val carpeta = TemporaryFolder()

    private val archivo: File get() = File(carpeta.root, "d.mbtiles")

    private fun teselas(n: Int) = (0 until n).map { Tesela(18, 133_000 + it, 100_000) }

    @Test
    fun `baja con cuatro peticiones a la vez y guarda todo`() =
        runBlocking {
            val fuente = FuenteFalsa()
            AlmacenDetalle(archivo).use { almacen ->
                val r = DescargadorDetalle(almacen, fuente, { 0 }, { Long.MAX_VALUE }).descargar(teselas(40))

                assertEquals(ResultadoDetalle.Completo(40, 0), r)
                assertEquals(40L, almacen.contarTeselas())
            }
            assertEquals(4, fuente.maxActivas.get())
        }

    @Test
    fun `las teselas de mar no se guardan pero se anotan para no pedirlas otra vez`() =
        runBlocking {
            val mar: (Tesela) -> Boolean = { it.x % 2 == 0 }
            AlmacenDetalle(archivo).use { almacen ->
                val primera = FuenteFalsa(mar = mar)
                val r = DescargadorDetalle(almacen, primera, { 0 }, { Long.MAX_VALUE }).descargar(teselas(10))
                assertEquals(ResultadoDetalle.Completo(5, 5), r)
                assertEquals(5L, almacen.contarTeselas())

                val segunda = FuenteFalsa(mar = mar)
                DescargadorDetalle(almacen, segunda, { 0 }, { Long.MAX_VALUE }).descargar(teselas(10))
                assertTrue("no repite ni las de mar", segunda.pedidas.isEmpty())
            }
        }

    @Test
    fun `no repite las teselas ya bajadas y reanuda lo que falta`() =
        runBlocking {
            AlmacenDetalle(archivo).use { almacen ->
                DescargadorDetalle(almacen, FuenteFalsa(), { 0 }, { Long.MAX_VALUE }).descargar(teselas(10))
                val fuente = FuenteFalsa()

                val r = DescargadorDetalle(almacen, fuente, { 0 }, { Long.MAX_VALUE }).descargar(teselas(15))

                assertEquals(ResultadoDetalle.Completo(5, 0), r)
                assertEquals(teselas(15).drop(10).toSet(), fuente.pedidas.toSet())
                assertEquals(15L, almacen.contarTeselas())
            }
        }

    @Test
    fun `se detiene al llegar al limite de espacio contando los otros archivos`() =
        runBlocking {
            AlmacenDetalle(archivo).use { almacen ->
                val base = almacen.bytes()
                val fuente = FuenteFalsa(tamano = 60_000)
                // Otros archivos ocupan 1 MB; el limite deja sitio para ~3 lotes de 2 teselas.
                val limite = 1_000_000L + base + 250_000L
                val d = DescargadorDetalle(almacen, fuente, { 1_000_000L }, { limite }, lote = 2)

                val r = d.descargar(teselas(40))

                assertTrue("limite: $r", r is ResultadoDetalle.LimiteAlcanzado)
                val guardadas = almacen.contarTeselas()
                assertTrue("guardadas: $guardadas", guardadas in 2..12)
                assertTrue(fuente.pedidas.size < 40)
            }
        }

    @Test
    fun `sin sitio desde el principio no pide nada`() =
        runBlocking {
            AlmacenDetalle(archivo).use { almacen ->
                val fuente = FuenteFalsa()
                val r = DescargadorDetalle(almacen, fuente, { 5_000_000L }, { 4_000_000L }).descargar(teselas(10))

                assertEquals(ResultadoDetalle.LimiteAlcanzado(0), r)
                assertTrue(fuente.pedidas.isEmpty())
            }
        }

    @Test
    fun `un fallo de red guarda lo ya bajado del lote y se propaga`() =
        runBlocking {
            AlmacenDetalle(archivo).use { almacen ->
                val fuente = FuenteFalsa(fallan = { it.x == 133_003 })
                try {
                    DescargadorDetalle(almacen, fuente, { 0 }, { Long.MAX_VALUE }).descargar(teselas(8))
                    fail("debía fallar")
                } catch (_: IOException) {
                    // esperado
                }
                assertEquals(7L, almacen.contarTeselas())

                val reanuda = FuenteFalsa()
                DescargadorDetalle(almacen, reanuda, { 0 }, { Long.MAX_VALUE }).descargar(teselas(8))
                assertEquals(listOf(Tesela(18, 133_003, 100_000)), reanuda.pedidas.toList())
            }
        }

    @Test
    fun `el progreso llega hasta el total de pendientes`() =
        runBlocking {
            AlmacenDetalle(archivo).use { almacen ->
                val avisos = mutableListOf<Pair<Int, Int>>()
                DescargadorDetalle(almacen, FuenteFalsa(), { 0 }, { Long.MAX_VALUE }, lote = 4)
                    .descargar(teselas(10)) { h, t -> avisos += h to t }

                assertEquals(0 to 10, avisos.first())
                assertEquals(10 to 10, avisos.last())
            }
        }
}
