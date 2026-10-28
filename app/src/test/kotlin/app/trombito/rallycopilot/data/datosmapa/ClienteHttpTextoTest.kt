package app.trombito.rallycopilot.data.datosmapa

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class ClienteHttpTextoTest {
    @get:Rule val carpeta = TemporaryFolder()

    private lateinit var entorno: EntornoDatosMapa
    private lateinit var cliente: ClienteHttp

    @Before
    fun preparar() {
        entorno = EntornoDatosMapa(carpeta.newFolder("entorno"))
        cliente = ClienteHttp(entorno.config)
    }

    @After
    fun cerrar() = entorno.close()

    @Test
    fun `devuelve el texto completo de una respuesta pequeña en UTF-8`() {
        val texto = """{"esquema":1,"nombre":"Mallorca ñandú"}"""
        entorno.publicarManifestCrudo(texto)

        assertEquals(texto, cliente.texto(URL_MANIFEST))
    }

    @Test
    fun `acepta exactamente el máximo de 1 MiB`() {
        val texto = "a".repeat(MAX_TEXTO)
        entorno.servidor.servir(URL(URL_MANIFEST).path, texto.toByteArray())

        assertEquals(texto, cliente.texto(URL_MANIFEST))
    }

    @Test
    fun `rechaza una respuesta de más de 1 MiB`() {
        entorno.servidor.servir(URL(URL_MANIFEST).path, ByteArray(MAX_TEXTO + 1) { 'a'.code.toByte() })

        val error = assertThrows(IOException::class.java) { cliente.texto(URL_MANIFEST) }

        assertTrue(error.message.orEmpty().startsWith("Respuesta demasiado grande"))
    }

    /**
     * Regresión del crash en Android 8-12 (minSdk 26): `InputStream.readNBytes(Int)` es de API 33. Este flujo falla
     * con UnsupportedOperationException si ClienteHttp vuelve a usar ese método, aunque el JVM lo tenga.
     */
    @Test
    fun `lee el texto sin usar InputStream readNBytes de API 33`() {
        val bytes = """{"esquema":1}""".toByteArray()
        val config =
            entorno.config.copy(
                abrirConexion = { url -> ConexionConCuerpo(url, EntradaSinReadNBytes(bytes)) },
            )

        assertEquals("""{"esquema":1}""", ClienteHttp(config).texto(URL_MANIFEST))
    }

    private companion object {
        const val MAX_TEXTO = 1_048_576
    }
}

/** Conexión sin red que responde 200 con un cuerpo dado. */
private class ConexionConCuerpo(
    url: URL,
    private val cuerpo: InputStream,
) : HttpURLConnection(url) {
    override fun connect() = Unit

    override fun disconnect() = Unit

    override fun usingProxy(): Boolean = false

    override fun getResponseCode(): Int = 200

    override fun getInputStream(): InputStream = cuerpo
}

/** Flujo de entrada que lanza en `readNBytes(Int)`, el método de API 33 que no existe en Android 8-12. */
private class EntradaSinReadNBytes(
    bytes: ByteArray,
) : InputStream() {
    private val dentro = ByteArrayInputStream(bytes)

    override fun read(): Int = dentro.read()

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int = dentro.read(b, off, len)

    override fun readNBytes(len: Int): ByteArray = throw UnsupportedOperationException("readNBytes es de API 33")
}
