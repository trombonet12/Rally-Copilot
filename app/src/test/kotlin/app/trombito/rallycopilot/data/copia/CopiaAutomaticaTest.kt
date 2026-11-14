package app.trombito.rallycopilot.data.copia

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.domain.copia.CopiaDatos
import app.trombito.rallycopilot.domain.copia.ResumenCopia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.ZoneId

private class CopiaFalsa : CopiaDatos {
    var llamadas = 0
    var fallar = false

    override suspend fun exportar(salida: OutputStream): ResumenCopia {
        llamadas++
        salida.write(byteArrayOf(llamadas.toByte()))
        if (fallar) throw IOException("sin espacio")
        return ResumenCopia(1, "1", 1, 0, 0, 0, 0)
    }

    override suspend fun importar(entrada: InputStream): ResumenCopia = error("no se usa")
}

@RunWith(RobolectricTestRunner::class)
class CopiaAutomaticaTest {
    private val contexto = ApplicationProvider.getApplicationContext<Context>()
    private val zona = ZoneId.of("UTC")
    private val falsa = CopiaFalsa()
    private lateinit var carpeta: File

    @Before
    fun preparar() {
        val preferencias = contexto.getSharedPreferences("copia_automatica", Context.MODE_PRIVATE)
        preferencias.edit().clear().commit()
        carpeta = File(contexto.getExternalFilesDir(null), CopiaAutomatica.CARPETA)
        carpeta.deleteRecursively()
    }

    @After
    fun limpiar() {
        carpeta.deleteRecursively()
    }

    private fun crear() = CopiaAutomatica(contexto, falsa, Dispatchers.Unconfined, zona = zona)

    private fun ms(
        dia: Int,
        hora: Int = 10,
        minuto: Int = 5,
    ): Long {
        val fecha = LocalDateTime.of(2026, 11, dia, hora, minuto)
        return fecha.atZone(zona).toInstant().toEpochMilli()
    }

    private fun fechadas(): List<String> {
        val nombres = carpeta.list().orEmpty()
        return nombres.filter { it != CopiaAutomatica.NOMBRE_ULTIMA }.sorted()
    }

    @Test
    fun `copiar crea la ultima y una fechada sin dejar temporales`() =
        runBlocking {
            assertTrue(crear().copiarSiToca(ms(3)))
            assertEquals(listOf("rally-2026-11-03-1005.zip"), fechadas())
            assertEquals(setOf("rally-ultima.zip", "rally-2026-11-03-1005.zip"), carpeta.list().orEmpty().toSet())
            assertEquals(1, File(carpeta, CopiaAutomatica.NOMBRE_ULTIMA).readBytes().size)
        }

    @Test
    @Config(sdk = [29])
    fun `por debajo de la API 30 no escribe nada en la carpeta externa`() =
        runBlocking {
            val copia = crear()
            assertFalse(copia.copiarSiToca(ms(3)))
            assertFalse(copia.copiarAlTerminar())
            assertFalse(copia.copiarAhora())
            assertEquals(0, falsa.llamadas)
            assertFalse(carpeta.exists())
        }

    @Test
    @Config(sdk = [30])
    fun `desde la API 30 si copia`() =
        runBlocking {
            assertTrue(crear().copiarAhora())
            assertTrue(File(carpeta, CopiaAutomatica.NOMBRE_ULTIMA).exists())
        }

    @Test
    fun `copiarAlTerminar con una fechada de hoy solo refresca la ultima`() =
        runBlocking {
            var reloj = ms(3, 10, 5)
            val copia = CopiaAutomatica(contexto, falsa, Dispatchers.Unconfined, ahora = { reloj }, zona = zona)
            assertTrue(copia.copiarAlTerminar())
            assertEquals(listOf("rally-2026-11-03-1005.zip"), fechadas())
            reloj = ms(3, 18, 40)
            assertTrue(copia.copiarAlTerminar())
            assertEquals("sin otra fechada el mismo dia", listOf("rally-2026-11-03-1005.zip"), fechadas())
            assertEquals(2, File(carpeta, CopiaAutomatica.NOMBRE_ULTIMA).readBytes()[0].toInt())
            assertEquals(setOf("rally-ultima.zip", "rally-2026-11-03-1005.zip"), carpeta.list().orEmpty().toSet())
            reloj = ms(4, 9, 0)
            assertTrue(copia.copiarAlTerminar())
            assertEquals(listOf("rally-2026-11-03-1005.zip", "rally-2026-11-04-0900.zip"), fechadas())
        }

    @Test
    fun `solo conserva las cinco fechadas mas nuevas y la ultima es la mas reciente`() =
        runBlocking {
            val copia = crear()
            for (dia in 1..7) assertTrue(copia.copiarSiToca(ms(dia)))
            assertEquals((3..7).map { "rally-2026-11-%02d-1005.zip".format(it) }, fechadas())
            assertEquals(7, File(carpeta, CopiaAutomatica.NOMBRE_ULTIMA).readBytes()[0].toInt())
        }

    @Test
    fun `con el interruptor apagado no copia salvo con copiar ahora`() =
        runBlocking {
            val copia = crear()
            copia.activar(false)
            assertFalse(copia.activa.value)
            assertFalse(copia.copiarSiToca(ms(3)))
            assertFalse(copia.copiarAlTerminar())
            assertEquals(0, falsa.llamadas)
            assertTrue(copia.copiarAhora())
            assertEquals(1, falsa.llamadas)
            assertFalse(crear().activa.value) // persistido
        }

    @Test
    fun `copiarSiToca no copia dos veces en veinte horas pero si pasadas`() =
        runBlocking {
            val copia = crear()
            assertTrue(copia.copiarSiToca(ms(3, 10)))
            assertFalse(copia.copiarSiToca(ms(3, 10)))
            assertFalse(copia.copiarSiToca(ms(4, 5)))
            assertEquals(1, falsa.llamadas)
            assertTrue(copia.copiarSiToca(ms(4, 6, 30)))
            assertEquals(2, falsa.llamadas)
        }

    @Test
    fun `un fallo se devuelve como falso y no deja temporales ni toca la ultima buena`() =
        runBlocking {
            val copia = crear()
            assertTrue(copia.copiarAhora())
            val buena = File(carpeta, CopiaAutomatica.NOMBRE_ULTIMA).readBytes().toList()
            falsa.fallar = true
            assertFalse(copia.copiarAhora())
            assertEquals(buena, File(carpeta, CopiaAutomatica.NOMBRE_ULTIMA).readBytes().toList())
            assertTrue(carpeta.list().orEmpty().none { it.endsWith(".tmp") })
        }
}
