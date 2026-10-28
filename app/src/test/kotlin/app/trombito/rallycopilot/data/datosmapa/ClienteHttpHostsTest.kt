package app.trombito.rallycopilot.data.datosmapa

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class ClienteHttpHostsTest {
    @get:Rule val carpeta = TemporaryFolder()

    private lateinit var entorno: EntornoDatosMapa
    private lateinit var cliente: ClienteHttp

    private val inicio = "https://github.com/trombonet12/rally-copilot-data/releases/download/v1/a.bin"
    private val rutaInicio = "/trombonet12/rally-copilot-data/releases/download/v1/a.bin"

    @Before
    fun preparar() {
        entorno = EntornoDatosMapa(carpeta.newFolder("entorno"))
        cliente = ClienteHttp(entorno.config)
        entorno.servidor.servir("/real", "hola".toByteArray())
    }

    @After
    fun cerrar() = entorno.close()

    private fun abrirRedirigiendoA(destino: String) {
        entorno.servidor.redirigir(rutaInicio, destino)
        cliente.abrir(inicio).disconnect()
    }

    @Test
    fun `sigue redirecciones a los hosts de GitHub`() {
        val destinos =
            listOf(
                "https://github.com/real",
                "https://objects.githubusercontent.com/real",
                "https://release-assets.githubusercontent.com/real",
                "https://otro.githubusercontent.com/real",
            )
        for (destino in destinos) {
            abrirRedirigiendoA(destino)
            assertTrue(destino, entorno.servidor.peticionesA("/real").isNotEmpty())
            entorno.servidor.peticiones.clear()
        }
    }

    @Test
    fun `no sigue redirecciones a otros hosts y no llega a pedirlos`() {
        val destinos =
            listOf(
                "https://evil.example/real",
                "https://githubusercontent.com.evil.example/real",
                "https://evilgithubusercontent.com/real",
                "https://github.com.evil.example/real",
                "https://127.0.0.1/real",
            )
        for (destino in destinos) {
            assertThrows(destino, IOException::class.java) { abrirRedirigiendoA(destino) }
        }
        assertEquals(emptyList<ServidorFalso.Peticion>(), entorno.servidor.peticionesA("/real"))
    }

    @Test
    fun `no sigue redirecciones a http aunque el host sea de GitHub`() {
        assertThrows(IOException::class.java) { abrirRedirigiendoA("http://objects.githubusercontent.com/real") }
    }

    @Test
    fun `rechaza abrir una url inicial que no es https de GitHub`() {
        assertThrows(IOException::class.java) { cliente.abrir("http://github.com/real") }
        assertThrows(IOException::class.java) { cliente.abrir("https://evil.example/real") }
        assertThrows(IOException::class.java) { cliente.texto("https://evil.example/manifest.json") }
        assertTrue(entorno.servidor.peticiones.isEmpty())
    }
}
