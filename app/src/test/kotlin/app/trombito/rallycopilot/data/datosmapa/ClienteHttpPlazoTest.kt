package app.trombito.rallycopilot.data.datosmapa

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class ClienteHttpPlazoTest {
    @get:Rule val carpeta = TemporaryFolder()

    private lateinit var entorno: EntornoDatosMapa

    private val url = "https://github.com/trombonet12/rally-copilot-data/lento.bin"

    @Before
    fun preparar() {
        entorno = EntornoDatosMapa(carpeta.newFolder("entorno"))
        entorno.servidor.servir("/trombonet12/rally-copilot-data/lento.bin", ByteArray(1000) { 1 })
    }

    @After
    fun cerrar() = entorno.close()

    @Test
    fun `un flujo que tarda mas del plazo total falla con IOException aunque no pase del tope`() {
        val cliente = ClienteHttp(entorno.config)
        cliente.flujo(url, 10_000, plazoMs = 100).use { entrada ->
            assertEquals(1, entrada.read())
            Thread.sleep(250) // un servidor goteando bytes: cada lectura es rápida, el total no
            assertThrows(IOException::class.java) { entrada.read() }
        }
    }

    @Test
    fun `dentro del plazo se lee entero`() {
        val cliente = ClienteHttp(entorno.config)
        cliente.flujo(url, 10_000, plazoMs = 60_000).use { assertEquals(1000, it.readBytes().size) }
    }
}
