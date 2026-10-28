package app.trombito.rallycopilot.data.datosmapa

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Cliente HTTP mínimo sobre `HttpURLConnection`: User-Agent propio, timeouts y redirecciones del mismo protocolo. */
class ClienteHttp(
    private val config: ConfigDatosMapa,
) {
    /**
     * Abre [url] (con `Range: bytes=desde-` si [desde] > 0) siguiendo redirecciones https a https (o http a http).
     * El llamante cierra la conexión. Devuelve la conexión ya con respuesta; el código puede ser 200, 206, 416...
     */
    fun abrir(
        url: String,
        desde: Long = 0,
    ): HttpURLConnection {
        var actual = URL(url)
        repeat(MAX_REDIRECCIONES + 1) {
            val con = conectar(actual, desde)
            actual = destinoRedireccion(actual, con) ?: return con
        }
        throw IOException("Demasiadas redirecciones")
    }

    private fun conectar(
        url: URL,
        desde: Long,
    ): HttpURLConnection {
        val con = config.abrirConexion(url)
        try {
            con.instanceFollowRedirects = false
            con.connectTimeout = TIMEOUT_MS
            con.readTimeout = TIMEOUT_MS
            con.setRequestProperty("User-Agent", config.userAgent)
            // Sin gzip transparente: con Range y Content-Length los bytes tienen que ser los del archivo.
            con.setRequestProperty("Accept-Encoding", "identity")
            if (desde > 0) con.setRequestProperty("Range", "bytes=$desde-")
            con.responseCode
        } catch (e: IOException) {
            con.disconnect()
            throw e
        }
        return con
    }

    /** La URL a la que redirige la respuesta (y cierra [con]), o null si no es una redirección. */
    private fun destinoRedireccion(
        actual: URL,
        con: HttpURLConnection,
    ): URL? {
        if (con.responseCode !in REDIRECCIONES) return null
        val destino = con.getHeaderField("Location")
        con.disconnect()
        if (destino == null) throw IOException("Redirección sin Location")
        val siguiente = URL(actual, destino)
        if (siguiente.protocol != actual.protocol) throw IOException("Redirección a otro protocolo")
        return siguiente
    }

    /** Descarga un texto pequeño (el manifest) o falla con [IOException]. */
    fun texto(url: String): String {
        val con = abrir(url)
        try {
            if (con.responseCode != HTTP_OK) throw IOException("HTTP ${con.responseCode} en $url")
            return con.inputStream.use { leerTexto(it, url) }
        } finally {
            con.disconnect()
        }
    }

    /** Lee como máximo MAX_TEXTO + 1 bytes. Sin `InputStream.readNBytes` (API 33): minSdk es 26. */
    private fun leerTexto(
        entrada: InputStream,
        url: String,
    ): String {
        val salida = ByteArrayOutputStream()
        val buffer = ByteArray(TAMANO_BUFFER)
        var restantes = MAX_TEXTO + 1
        while (restantes > 0) {
            val leidos = entrada.read(buffer, 0, minOf(buffer.size, restantes))
            if (leidos == -1) break
            salida.write(buffer, 0, leidos)
            restantes -= leidos
        }
        if (salida.size() > MAX_TEXTO) throw IOException("Respuesta demasiado grande en $url")
        return salida.toByteArray().toString(Charsets.UTF_8)
    }

    companion object {
        const val HTTP_OK = 200
        const val HTTP_PARCIAL = 206
        const val HTTP_RANGO_NO_SATISFACIBLE = 416
        private const val TIMEOUT_MS = 30_000
        private const val MAX_REDIRECCIONES = 5
        private const val MAX_TEXTO = 1_048_576
        private const val TAMANO_BUFFER = 8192
        private val REDIRECCIONES = setOf(301, 302, 303, 307, 308)
    }
}
