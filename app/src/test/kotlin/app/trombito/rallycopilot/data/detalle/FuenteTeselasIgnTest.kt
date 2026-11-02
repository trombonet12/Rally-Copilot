package app.trombito.rallycopilot.data.detalle

import app.trombito.rallycopilot.data.datosmapa.ClienteHttp
import app.trombito.rallycopilot.data.datosmapa.EntornoDatosMapa
import app.trombito.rallycopilot.data.datosmapa.RespuestaHttpException
import app.trombito.rallycopilot.data.datosmapa.ServidorFalso
import app.trombito.rallycopilot.motor.precarga.Tesela
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class FuenteTeselasIgnTest {
    @get:Rule val carpeta = TemporaryFolder()

    private lateinit var entorno: EntornoDatosMapa
    private val tesela = Tesela(18, 133_000, 100_000)

    /** Ruta TMS del IGN: la fila va con el origen abajo. */
    private val ruta = "/1.0.0/pnoa-ma/18/133000/${262_143 - 100_000}.jpeg"
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0)
    private val esperas = mutableListOf<Long>()

    @Before
    fun preparar() {
        entorno = EntornoDatosMapa(carpeta.newFolder("e"))
    }

    @After
    fun cerrar() = entorno.close()

    private fun fuente(reintentos: Int = 3) =
        FuenteTeselasIgn(ClienteHttp.paraIgn(entorno.config), reintentos = reintentos, esperar = { esperas += it })

    @Test
    fun `pide la fila TMS con el user agent y devuelve el JPEG`() =
        runBlocking {
            entorno.servidor.servir(ruta, jpeg)

            val r = fuente().obtener(tesela)

            assertEquals(jpeg.toList(), (r as ResultadoTesela.Jpeg).bytes.toList())
            assertEquals(
                "RallyCopilot/test",
                entorno.servidor
                    .peticionesA(ruta)
                    .single()
                    .userAgent,
            )
        }

    @Test
    fun `un PNG transparente o un 404 son mar`() =
        runBlocking {
            entorno.servidor.servir(ruta, png)
            assertEquals(ResultadoTesela.SinCobertura, fuente().obtener(tesela))

            val otra = Tesela(18, 133_001, 100_000)
            assertEquals(ResultadoTesela.SinCobertura, fuente().obtener(otra))
        }

    @Test
    fun `reintenta los 503 con espera exponencial y acaba fallando`() =
        runBlocking {
            entorno.servidor.servir(ruta, ServidorFalso.Recurso(ByteArray(0), codigo = 503))

            try {
                fuente(reintentos = 3).obtener(tesela)
                fail("debía fallar")
            } catch (e: RespuestaHttpException) {
                assertEquals(503, e.codigo)
            }

            assertEquals(3, entorno.servidor.peticionesA(ruta).size)
            assertEquals(listOf(500L, 1_000L), esperas)
        }

    @Test
    fun `un 403 no se reintenta y algo que no es imagen es un error`() =
        runBlocking {
            entorno.servidor.servir(ruta, ServidorFalso.Recurso(ByteArray(0), codigo = 403))
            try {
                fuente().obtener(tesela)
                fail("debía fallar")
            } catch (e: RespuestaHttpException) {
                assertEquals(403, e.codigo)
            }
            assertEquals(1, entorno.servidor.peticionesA(ruta).size)

            entorno.servidor.servir(ruta, "<html>".toByteArray())
            try {
                fuente().obtener(tesela)
                fail("debía fallar")
            } catch (e: IOException) {
                assertTrue(e is FuenteTeselasIgn.FormatoInesperadoException)
            }
        }

    @Test
    fun `el cliente del IGN rechaza otros hosts y http`() {
        val cliente = ClienteHttp.paraIgn(entorno.config)
        for (url in listOf("https://github.com/x.jpeg", "http://tms-pnoa-ma.idee.es/x.jpeg", "https://evil.es/x")) {
            try {
                cliente.abrir(url)
                fail("debía rechazar $url")
            } catch (e: IOException) {
                assertTrue(e.message.orEmpty().contains("no permitido"))
            }
        }
    }
}
