package app.trombito.rallycopilot.data.datosmapa

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Cliente HTTP mínimo sobre `HttpURLConnection`: User-Agent propio, timeouts y solo https hacia los hosts que admita
 * [hostPermitido] (por defecto GitHub y su CDN). Con [aceptarGzip] pide `Accept-Encoding: gzip` (para [flujo]); sin él
 * pide `identity`, que es lo que necesitan las descargas con `Range`.
 */
class ClienteHttp(
    private val config: ConfigDatosMapa,
    private val hostPermitido: (String) -> Boolean = ::esHostDeGithub,
    private val aceptarGzip: Boolean = false,
    private val plazoFlujoMs: Long = PLAZO_FLUJO_MS,
    /** Tiempo máximo de conexión y de espera de datos; solo lo cambian las pruebas (cortes de red rápidos). */
    private val timeoutMs: Int = TIMEOUT_MS,
) {
    /**
     * Abre [url] (con `Range: bytes=desde-` si [desde] > 0) siguiendo redirecciones https a https dentro de GitHub.
     * El llamante cierra la conexión. Devuelve la conexión ya con respuesta; el código puede ser 200, 206, 416...
     */
    fun abrir(
        url: String,
        desde: Long = 0,
    ): HttpURLConnection {
        var actual = URL(url)
        exigirHostPermitido(actual)
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
            con.connectTimeout = timeoutMs
            con.readTimeout = timeoutMs
            con.setRequestProperty("User-Agent", config.userAgent)
            // Sin gzip salvo que se pida: con Range y Content-Length los bytes tienen que ser los del archivo.
            con.setRequestProperty("Accept-Encoding", if (aceptarGzip) "gzip" else "identity")
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
        exigirHostPermitido(siguiente)
        return siguiente
    }

    /** Solo https y solo los hosts permitidos, tanto en la URL inicial como en redirecciones. */
    private fun exigirHostPermitido(url: URL) {
        val host = url.host.lowercase()
        if (url.protocol != "https" || !hostPermitido(host)) {
            throw IOException("Destino no permitido: ${url.protocol}://$host")
        }
    }

    /**
     * Abre [url] y entrega el cuerpo ya descomprimido (si el servidor lo envía en gzip), limitado a [maxBytes] bytes
     * leídos: al pasarse falla con [TopeExcedidoException] (cubre también una bomba de descompresión). Además, la
     * lectura entera tiene un plazo total de [plazoMs] desde esta llamada: pasado ese tiempo la siguiente lectura falla
     * con [IOException] (el `readTimeout` solo cubre pausas, no un servidor que gotea bytes). Cerrar el flujo cierra
     * la conexión. Falla con [IOException] si la respuesta no es 200.
     */
    fun flujo(
        url: String,
        maxBytes: Long,
        plazoMs: Long = plazoFlujoMs,
    ): InputStream {
        val con = abrir(url)
        try {
            comprobarRespuesta(con, maxBytes)
            val crudo = con.inputStream
            val entrada = if (con.contentEncoding.equals("gzip", ignoreCase = true)) GZIPInputStream(crudo) else crudo
            return FlujoConTope(entrada, maxBytes, con, plazoMs)
        } catch (e: IOException) {
            con.disconnect()
            throw e
        }
    }

    private fun comprobarRespuesta(
        con: HttpURLConnection,
        maxBytes: Long,
    ) {
        if (con.responseCode != HTTP_OK) throw RespuestaHttpException(con.responseCode)
        if (con.contentLengthLong > maxBytes) throw TopeExcedidoException(maxBytes)
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

        /** Plazo total por defecto de [flujo] (2 min): un servidor que gotea bytes no puede retener la descarga. */
        const val PLAZO_FLUJO_MS = 120_000L
        const val HTTP_PARCIAL = 206

        /** Plazo total de una tesela (30 s). */
        private const val PLAZO_TESELA_MS = 30_000L
        const val HTTP_RANGO_NO_SATISFACIBLE = 416
        private const val TIMEOUT_MS = 30_000
        private const val MAX_REDIRECCIONES = 5
        private const val MAX_TEXTO = 1_048_576
        private const val TAMANO_BUFFER = 8192
        private val REDIRECCIONES = setOf(301, 302, 303, 307, 308)

        /** Cliente para el feed de la DGT: solo `nap.dgt.es`, con gzip y con [PLAZO_FLUJO_MS] de plazo total. */
        fun paraDgt(config: ConfigDatosMapa) =
            ClienteHttp(config, ::esHostDeDgt, aceptarGzip = true, plazoFlujoMs = PLAZO_FLUJO_MS)

        /** Cliente para las teselas PNOA del IGN: solo `tms-pnoa-ma.idee.es`, sin gzip (son JPEG). */
        fun paraIgn(config: ConfigDatosMapa) = ClienteHttp(config, ::esHostDelIgn, plazoFlujoMs = PLAZO_TESELA_MS)
    }
}

/** La respuesta HTTP no fue 200. */
class RespuestaHttpException(
    val codigo: Int,
) : IOException("HTTP $codigo")

/** La respuesta pasa del tope de bytes que se admite. */
class TopeExcedidoException(
    maxBytes: Long,
) : IOException("La respuesta pasa de $maxBytes bytes")

/**
 * Entrada que falla al leer más de [maxBytes] bytes o pasados [plazoMs] ms desde su creación, y que al cerrarse
 * cierra también la conexión.
 */
private class FlujoConTope(
    private val origen: InputStream,
    private val maxBytes: Long,
    private val conexion: HttpURLConnection,
    plazoMs: Long,
) : InputStream() {
    private var leidos = 0L
    private val limiteNs = System.nanoTime() + plazoMs.coerceIn(0, MAX_PLAZO_MS) * NS_POR_MS

    private fun comprobarPlazo() {
        if (System.nanoTime() - limiteNs > 0) throw IOException("La descarga pasa del plazo total")
    }

    override fun read(): Int {
        comprobarPlazo()
        val b = origen.read()
        if (b >= 0) sumar(1)
        return b
    }

    override fun read(
        buffer: ByteArray,
        desde: Int,
        largo: Int,
    ): Int {
        comprobarPlazo()
        val n = origen.read(buffer, desde, largo)
        if (n > 0) sumar(n)
        return n
    }

    private fun sumar(n: Int) {
        leidos += n
        if (leidos > maxBytes) throw TopeExcedidoException(maxBytes)
    }

    override fun close() {
        try {
            origen.close()
        } finally {
            conexion.disconnect()
        }
    }
}

private const val NS_POR_MS = 1_000_000L
private const val MAX_PLAZO_MS = 100L * 365 * 24 * 3_600_000

private const val HOST_GITHUB = "github.com"
private const val HOST_CDN_OBJETOS = "objects.githubusercontent.com"
private const val HOST_CDN_ASSETS = "release-assets.githubusercontent.com"
private const val HOST_DGT = "nap.dgt.es"
private const val HOST_IGN = "tms-pnoa-ma.idee.es"

/** GitHub y solo los dos CDN de las descargas de releases (no el contenido de usuarios: `raw.`, `gist.`...). */
private fun esHostDeGithub(host: String): Boolean =
    host == HOST_GITHUB || host == HOST_CDN_OBJETOS || host == HOST_CDN_ASSETS

/** Solo el NAP de la DGT (incidencias DATEX II). */
private fun esHostDeDgt(host: String): Boolean = host == HOST_DGT

/** Solo el TMS de PNOA del IGN (satélite de detalle). */
private fun esHostDelIgn(host: String): Boolean = host == HOST_IGN
